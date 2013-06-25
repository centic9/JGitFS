package org.dstadler.jgitfs.util;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fusejna.StructStat.StatWrapper;
import net.fusejna.types.TypeMode.NodeType;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;


public class JGitHelper implements Closeable {
	private final Repository repository;
	private final Git git;
	
	public JGitHelper(String gitDir) throws IOException {
		if(!gitDir.endsWith(".git")) {
			gitDir = gitDir + "/.git";
		}
		if(!new File(gitDir).exists()) {
			throw new IllegalStateException("Could not find git repository at " + gitDir);
		}

		System.out.println("Using git repo at " + gitDir);
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		repository = builder.setGitDir(new File(gitDir))
		  .readEnvironment() // scan environment GIT_* variables
		  .findGitDir() // scan up the file system tree
		  .build();
		git = new Git(repository);
	}

	public String readCommit(String path) {
		String commit = StringUtils.removeStart(path, GitUtils.COMMIT_SLASH).replace("/", "");
		return StringUtils.substring(commit, 0, 40);
	}
	
	public String readPath(final String path) {
		String file = StringUtils.removeStart(path, GitUtils.COMMIT_SLASH);
		return StringUtils.substring(file, 40 + 2);	// cut away commitish and two slashes
	}
	
	public void readType(String commit, String path, StatWrapper stat) throws MissingObjectException, IncorrectObjectTypeException, IOException {
		RevCommit revCommit = buildRevCommit(commit);

		// and using commit's tree find the path
		RevTree tree = revCommit.getTree();
		//System.out.println("Having tree: " + tree + " for commit " + commit);
		
		// set time and user-id/group-id
		stat.ctime(revCommit.getCommitTime());
		stat.mtime(revCommit.getCommitTime());
		stat.uid(GitUtils.UID);
		stat.gid(GitUtils.GID);
		
		// now read the file/directory attributes
		TreeWalk treeWalk = buildTreeWalk(tree, path);
		FileMode fileMode = treeWalk.getFileMode(0);
		
		if(fileMode.equals(FileMode.EXECUTABLE_FILE) ||
				fileMode.equals(FileMode.REGULAR_FILE)) {
			ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
			stat.size(loader.getSize());
			stat.setMode(NodeType.FILE, 
					true, false, fileMode.equals(FileMode.EXECUTABLE_FILE), 
					true, false, fileMode.equals(FileMode.EXECUTABLE_FILE), 
					false, false, false);
			return;
		} else if(fileMode.equals(FileMode.TREE)) {
			stat.setMode(NodeType.DIRECTORY, true, false, true, true, false, true, false, false, false);
			return;
		} if(fileMode.equals(FileMode.SYMLINK)) {
			stat.setMode(NodeType.SYMBOLIC_LINK, true, false, true, true, false, true, false, false, false);
			return;
		}  
		throw new IllegalStateException("Found unknown FileMode in Git for commit '" + commit + "' and path '" + path + "': " + fileMode.getBits());
	}
	
	public InputStream openFile(String commit, String path) throws MissingObjectException, IncorrectObjectTypeException, IOException {
		RevCommit revCommit = buildRevCommit(commit);

		// and using commit's tree find the path
		RevTree tree = revCommit.getTree();
		//System.out.println("Having tree: " + tree + " for commit " + commit);

		// now try to find a specific file
		TreeWalk treeWalk = buildTreeWalk(tree, path);
		
		if((treeWalk.getFileMode(0).getBits() & FileMode.TYPE_FILE) == 0) {
			throw new IllegalStateException("Tried to read the contents of a non-file for commit '" + commit + "' and path '" + path + "', had filemode " + treeWalk.getFileMode(0).getBits());
		}
		
		ObjectId objectId = treeWalk.getObjectId(0);
		ObjectLoader loader = repository.open(objectId);

		// finally open an InputStream for the file contents
		return loader.openStream();		
	}

	private TreeWalk buildTreeWalk(RevTree tree, final String path) throws MissingObjectException,
			IncorrectObjectTypeException, CorruptObjectException, IOException {
		TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree);
		
		if(treeWalk == null) {
			throw new IllegalStateException("Did not find expected file '" + path + "' in tree '" + tree.getName() + "'");
		}
		
		return treeWalk;
	}

	private RevCommit buildRevCommit(String commit) throws MissingObjectException, IncorrectObjectTypeException, IOException {
		// a RevWalk allows to walk over commits based on some filtering that is defined
		RevWalk revWalk = new RevWalk(repository);
		return revWalk.parseCommit(ObjectId.fromString(commit));
	}

	public List<String> getBranches() throws GitAPIException {
		List<Ref> brancheRefs = git.branchList().setListMode(null).call();
		List<String> branches = new ArrayList<String>();
		for(Ref ref : brancheRefs) {
			String name = adjustName(ref.getName());
			branches.add(name);
			if(name.startsWith("refs_heads_")) {
				branches.add(StringUtils.removeStart(name, "refs_heads_"));
			}
		}
		return branches;
	}
	
	public String getBranchHeadCommit(String branch) throws GitAPIException {
		List<Ref> brancheRefs = git.branchList().setListMode(null).call();
		for(Ref ref : brancheRefs) {
			String name = adjustName(ref.getName());
			//System.out.println("Had branch: " + name);
			if(name.equals(branch) || name.equals("refs_heads_" + branch)) {
				return ref.getObjectId().getName();
			}
		}
		
		return null;
	}

	public List<String> getTags() throws GitAPIException {
		List<Ref> tagRefs = git.tagList().call();
		List<String> tags = new ArrayList<String>();
		for(Ref ref : tagRefs) {
			String name = adjustName(ref.getName());
			tags.add(name);
			if(name.startsWith("refs_tags_")) {
				tags.add(StringUtils.removeStart(name, "refs_tags_"));
			}
		}
		return tags;
	}
	
	public String adjustName(String name) {
		return name.replace("/", "_");
	}
	
	public String getTagHeadCommit(String tag) throws GitAPIException {
		List<Ref> tagRefs = git.tagList().call();
		for(Ref ref : tagRefs) {
			String name = adjustName(ref.getName());
			//System.out.println("Had tag: " + name);
			if(name.equals(tag) || name.equals("refs_tags_" + tag)) {
				return ref.getObjectId().getName();
			}
		}
		
		return null;
	}
	
	public Set<String> allCommitSubs() throws NoHeadException, GitAPIException, IOException {
		Set<String> commitSubs = new HashSet<String>();

		// we currently use all refs for finding commits quickly
		RevWalk walk = new RevWalk(repository);
		Map<String, Ref> allRefs = repository.getAllRefs();
		for(String ref : allRefs.keySet()) {
			addCommitSubs(commitSubs, walk, ref);
		}
		
		return commitSubs;
	}

	private void addCommitSubs(Collection<String> commits, RevWalk walk, String ref) throws IOException, MissingObjectException, IncorrectObjectTypeException {
		Ref head = repository.getRef(ref);
		RevCommit commit = walk.parseCommit(head.getObjectId());
		walk.markStart(commit);
		try {
			for(RevCommit rev : walk) {
				String name = rev.getName();
				commits.add(name.substring(0,2));
	
				// we can leave the loop as soon as we have all two-digit values, which is typically the case for large repositories
				if(commits.size() >= 256) {
					return;
				}
			}
		} finally {
			walk.reset();
		}
	}
	
	public Collection<String> allCommits(String sub) throws NoHeadException, GitAPIException, IOException {
		Set<String> commits = new HashSet<String>();

		RevWalk walk = new RevWalk(repository);

		Set<String> seenHeadCommits = new HashSet<String>();
		// TODO: we do not read unreferenced commits here and reading is done in an unperformant way as we likely read the same commits over and over again
		// as a workaround we currently use all branches (includes master) and all tags for finding commits quickly
		Map<String, Ref> allRefs = repository.getAllRefs();
		for(String ref : allRefs.keySet()) {
			Ref head = repository.getRef(ref);
			RevCommit commit = walk.parseCommit(head.getObjectId());

			// only read commits of this ref if we did not add parents of this commit already
			if(seenHeadCommits.add(commit.getName())) {
				addCommits(commits, walk, commit, sub);
			}
			//System.out.println("Having " + commits.size() + " commits after ref " + ref);
		}

		return commits;
	}

	private void addCommits(Collection<String> commits, RevWalk walk, RevCommit commit, String sub) throws IOException, MissingObjectException,
			IncorrectObjectTypeException {
		walk.markStart(commit);
		for(RevCommit rev : walk) {
			String name = rev.getId().getName();
			if(sub == null || name.startsWith(sub)) {
				commits.add(name);
			}
		}
		walk.reset();
	}
	
	@Override
	public void close() throws IOException {
		repository.close();
	}

	public List<String> readElementsAt(String commit, String path) throws MissingObjectException, IncorrectObjectTypeException, IOException {
		RevCommit revCommit = buildRevCommit(commit);

		// and using commit's tree find the path
		RevTree tree = revCommit.getTree();
		//System.out.println("Having tree: " + tree + " for commit " + commit);
		
		List<String> items = new ArrayList<String>();

		// shortcut for root-path
		if(path.isEmpty()) {
			TreeWalk treeWalk = new TreeWalk(repository);
			treeWalk.addTree(tree);
			treeWalk.setRecursive(false);
			treeWalk.setPostOrderTraversal(false);

			while(treeWalk.next()) {
				items.add(treeWalk.getPathString());
			}

			return items;
		}
		
		// now try to find a specific file
		TreeWalk treeWalk = buildTreeWalk(tree, path);
		if((treeWalk.getFileMode(0).getBits() & FileMode.TYPE_TREE) == 0) {
			throw new IllegalStateException("Tried to read the elements of a non-tree for commit '" + commit + "' and path '" + path + "', had filemode " + treeWalk.getFileMode(0).getBits());
		}

		TreeWalk dirWalk = new TreeWalk(repository);
		dirWalk.addTree(treeWalk.getObjectId(0));
		dirWalk.setRecursive(false);
		while(dirWalk.next()) {
			items.add(dirWalk.getPathString());
		}

		return items;
	}
}
