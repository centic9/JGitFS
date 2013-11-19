package org.dstadler.jgitfs.util;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fusejna.StructStat.StatWrapper;
import net.fusejna.types.TypeMode.NodeType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdSubclassMap;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Helper class which encapsulates access to the actual Git repository by
 * using JGit internally, but providing plain object/data as results, i.e.
 * no JGit objects should be necessary as part of the API.
 *
 * @author cwat-dstadler
 */
public class JGitHelper implements Closeable {
	private final Repository repository;
	private final Git git;

	/**
	 * Construct the helper with the given directory as Git repository.
	 *
	 * @param pGitDir A Git repository, either the root-dir or the .git directory directly.
	 * @throws IllegalStateException If the .git directory is not found
	 * @throws IOException If opening the Git repository fails
	 */
	public JGitHelper(String pGitDir) throws IOException {
		String gitDir = pGitDir;
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

	/**
	 * For a path to a commit, i.e. something like "/commit/00/123456..." return the
	 * actual commit-id, i.e. 00123456...
	 *
	 * @param path The path with the three elements "commit", <two-digit-sub>, <40-digit-rest>
	 * @return The resulting commit-id
	 */
	public String readCommit(String path) {
		String commit = StringUtils.removeStart(path, GitUtils.COMMIT_SLASH).replace("/", "");
		return StringUtils.substring(commit, 0, 40);
	}

	/**
	 * For a path to a file/directory inside a commit like "/commit/00/123456.../somedir/somefile", return
	 * the actual file-path, i.e. "somedir/somefile"
	 *
	 * @param path The full path including the commit-id
	 * @return The extracted path to the directory/file
	 */
	public String readPath(final String path) {
		String file = StringUtils.removeStart(path, GitUtils.COMMIT_SLASH);
		return StringUtils.substring(file, 40 + 2);	// cut away commitish and two slashes
	}

	/**
	 * Populate the StatWrapper with the necessary values like mode, uid, gid and type of file/directory/symlink.
	 *
	 * @param commit The commit-id as-of which we read the data
	 * @param path The path to the file/directory
	 * @param stat The StatWrapper instance to populate
	 *
	 * @throws IllegalStateException If the path or the commit cannot be found or an unknown type is encountered
	 * @throws IOException If access to the Git repository fails
	 * @throws FileNotFoundException If the given path cannot be found as part of the given commit-id
	 */
	public void readType(String commit, String path, StatWrapper stat) throws IOException {
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

	/**
	 * Read the target file for the given symlink as part of the given commit.
	 *
	 * @param commit the commit-id as-of which we read the symlink
	 * @param path the path to the symlink
	 * @return the target of the symlink, relative to the directory of the symlink itself
	 * @throws IOException If an error occurs while reading from the Git repository
	 * @throws FileNotFoundException If the given path cannot be found in the given commit-id
	 * @throws IllegalArgumentException If the given path does not denote a symlink
	 */
	public String readSymlink(String commit, String path) throws IOException {
		RevCommit revCommit = buildRevCommit(commit);

		// and using commit's tree find the path
		RevTree tree = revCommit.getTree();

		// now read the file/directory attributes
		TreeWalk treeWalk = buildTreeWalk(tree, path);
		FileMode fileMode = treeWalk.getFileMode(0);
		if(!fileMode.equals(FileMode.SYMLINK)) {
			throw new IllegalArgumentException("Had request for symlink-target which is not a symlink, commit '" + commit + "' and path '" + path + "': " + fileMode.getBits());
		}
		
		// try to read the file-data as it contains the symlink target
		InputStream openFile = openFile(commit, path);
		try {
			return IOUtils.toString(openFile);
		} finally {
			openFile.close();
		}
	}
	
	/**
	 * Retrieve the contents of the given file as-of the given commit.
	 *
	 * @param commit The commit-id as-of which we read the data
	 * @param path The path to the file/directory
	 *
	 * @return An InputStream which can be used to read the contents of the file.
	 *
	 * @throws IllegalStateException If the path or the commit cannot be found or does not denote a file
	 * @throws IOException If access to the Git repository fails
	 * @throws FileNotFoundException If the given path cannotbe found in the given commit-id
	 */
	public InputStream openFile(String commit, String path) throws IOException {
		RevCommit revCommit = buildRevCommit(commit);

		// use the commit's tree find the path
		RevTree tree = revCommit.getTree();
		//System.out.println("Having tree: " + tree + " for commit " + commit);

		// now try to find a specific file
		TreeWalk treeWalk = buildTreeWalk(tree, path);
		if((treeWalk.getFileMode(0).getBits() & FileMode.TYPE_FILE) == 0) {
			throw new IllegalStateException("Tried to read the contents of a non-file for commit '" + commit + "' and path '" + path + "', had filemode " + treeWalk.getFileMode(0).getBits());
		}

		// then open the file for reading.
		ObjectId objectId = treeWalk.getObjectId(0);
		ObjectLoader loader = repository.open(objectId);

		// finally open an InputStream for the file contents
		return loader.openStream();
	}

	private TreeWalk buildTreeWalk(RevTree tree, final String path) throws IOException {
		TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree);

		if(treeWalk == null) {
			throw new FileNotFoundException("Did not find expected file '" + path + "' in tree '" + tree.getName() + "'");
		}

		return treeWalk;
	}

	private RevCommit buildRevCommit(String commit) throws MissingObjectException, IncorrectObjectTypeException, IOException {
		// a RevWalk allows to walk over commits based on some filtering that is defined
		RevWalk revWalk = new RevWalk(repository);
		return revWalk.parseCommit(ObjectId.fromString(commit));
	}

	/**
	 * Return all local branches, excluding any remote branches.
	 *
	 * To ease implementation, slashes in branch-names are replaced by underscore. Also
	 * entries starting with refs/heads/ are listed with their short name as well
	 *
	 * @return A list of branch-names
	 * @throws IOException If accessing the Git repository fails
	 */
	public List<String> getBranches() throws IOException {
		final List<Ref> brancheRefs = readBranches();
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

	/**
	 * Return the commit-id for the given branch.
	 *
	 * To ease implementation, slashes in branch-names are replaced by underscore. Also
	 * entries starting with refs/heads/ are listed with their short name as well
	 *
	 * @param branch The branch to read data for, both the short-name and the name with refs/heads/... is possible
	 * @return A commit-id if found or null if not found.
	 * @throws IOException If accessing the Git repository fails
	 */
	public String getBranchHeadCommit(String branch) throws IOException {
		final List<Ref> branchRefs = readBranches();
		for(Ref ref : branchRefs) {
			String name = adjustName(ref.getName());
			//System.out.println("Had branch: " + name);
			if(name.equals(branch) || name.equals("refs_heads_" + branch)) {
				return ref.getObjectId().getName();
			}
		}

		return null;
	}

	private List<Ref> readBranches() throws IOException {
		final List<Ref> branchRefs;
		try {
			branchRefs = git.branchList().setListMode(null).call();
		} catch (GitAPIException e) {
			throw new IOException("Had error while reading the list of branches from the Git repository", e);
		}
		return branchRefs;
	}


	/**
	 * Return all remote branches and tags.
	 *
	 * To ease implementation, slashes in names are replaced by underscore. Also
	 * entries starting with refs/remotes/ are listed with their short name as well
	 *
	 * @return A list of remote-names
	 * @throws IOException If accessing the Git repository fails
	 */
	public List<String> getRemotes() throws IOException {
		final List<Ref> remoteRefs = readRemotes();
		List<String> remotes = new ArrayList<String>();
		for(Ref ref : remoteRefs) {
			String name = adjustName(ref.getName());
			remotes.add(name);
			if(name.startsWith("refs_remotes_")) {
				remotes.add(StringUtils.removeStart(name, "refs_remotes_"));
			}
		}
		return remotes;
	}

	/**
	 * Return the commit-id for the given remote.
	 *
	 * To ease implementation, slashes in names are replaced by underscore. Also
	 * entries starting with refs/remotes/ are listed with their short name as well
	 *
	 * @param remote The remote name to read data for, both the short-name and the name with refs/remotes/... is possible
	 * @return A commit-id if found or null if not found.
	 * @throws IOException If accessing the Git repository fails
	 */
	public String getRemoteHeadCommit(String remote) throws IOException {
		final List<Ref> remoteRefs = readRemotes();
		for(Ref ref : remoteRefs) {
			String name = adjustName(ref.getName());
			//System.out.println("Had branch: " + name);
			if(name.equals(remote) || name.equals("refs_remotes_" + remote)) {
				return ref.getObjectId().getName();
			}
		}

		return null;
	}

	private List<Ref> readRemotes() throws IOException {
		final List<Ref> remoteRefs;
		try {
			remoteRefs = git.branchList().setListMode(ListMode.REMOTE).call();
		} catch (GitAPIException e) {
			throw new IOException("Had error while reading the list of remote branches/tags from the Git repository", e);
		}
		return remoteRefs;
	}

	/**
	 * Return all tags.
	 *
	 * To ease implementation, slashes in branch-names are replaced by underscore. Also
	 * entries starting with refs/tags/ are listed with their short name as well
	 *
	 * @return A list of branch-names
	 * @throws IOException If accessing the Git repository fails
	 */
	public List<String> getTags() throws IOException {
		List<Ref> tagRefs = readTags();
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

	/**
	 * Return the commit-id for the given tag.
	 *
	 * To ease implementation, slashes in tag-names are replaced by underscore. Also
	 * entries starting with refs/tags/ are listed with their short name as well
	 *
	 * @param tag The tag to read data for, both the short-name and the name with refs/tags/... is possible
	 * @return A commit-id if found or null if not found.
	 * @throws IOException If accessing the Git repository fails
	 */
	public String getTagHeadCommit(String tag) throws IOException {
		List<Ref> tagRefs = readTags();
		for(Ref ref : tagRefs) {
			String name = adjustName(ref.getName());
			//System.out.println("Had tag: " + name);
			if(name.equals(tag) || name.equals("refs_tags_" + tag)) {
				return ref.getObjectId().getName();
			}
		}

		return null;
	}

	private List<Ref> readTags() throws IOException {
		final List<Ref> tagRefs;
		try {
			tagRefs = git.tagList().call();
		} catch (GitAPIException e) {
			throw new IOException("Had error while reading the list of tags from the Git repository", e);
		}
		return tagRefs;
	}

	private String adjustName(String name) {
		// TODO: handle tags with slash as subdirs instead of replacing with underscore
		return name.replace("/", "_");
	}

	/**
	 * Retrieve a list of all two-digit commit-subs which allow to build the first directory level
	 * of a commit-file-structure.
	 *
	 * @return A Set containing all used commit-subs where the filesystem can drill down to actual commits.
	 * @throws IOException If access to the Git repository fails.
	 */
	public Set<String> allCommitSubs() throws IOException {
		Set<String> commitSubs = new HashSet<String>();

		// we currently use all refs for finding commits quickly
		RevWalk walk = new RevWalk(repository);
		try {
			// optimization: we only need the commit-ids here, so we can discard the contents right away
			walk.setRetainBody(false);
	
			Map<String, Ref> allRefs = repository.getAllRefs();
			for(String ref : allRefs.keySet()) {
				addCommitSubs(commitSubs, walk, ref);
			}
	
			return commitSubs;
		} finally {
			walk.dispose();
		}
	}

	private void addCommitSubs(Collection<String> commits, RevWalk walk, String ref) throws IOException {
		Ref head = repository.getRef(ref);
		final RevCommit commit;
		try {
			commit = walk.parseCommit(head.getObjectId());
		} catch (IncorrectObjectTypeException e) {
			System.out.println("Invalid head-commit for ref " + ref + " and id: " + head.getObjectId().getName() + ": " + e);
			return;
		}
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

	/**
	 * Retrieve a list of all or a certain range of commit-ids in this Git repository. This is used to
	 * populate the second level beneath the /commit directory with the actual commit-ids. The parameter
	 * "sub" allows to only return commits starting with a certain commit-sub.
	 *
	 * @param sub A two-digit which is used to filter commit-ids, or null if no filtering should be done.
	 * @return A Set containing all commit-ids found in this Git repository if sub is null or only matching commit-ids if sub is specified.
	 * @throws IOException If access to the Git repository fails.
	 */
	public Collection<String> allCommits(String sub) throws IOException {
		ObjectIdSubclassMap<RevCommit> map = new ObjectIdSubclassMap<RevCommit>();

		// TODO: we do not read unreferenced commits here, it would be nice to be able to access these as well here
		// see http://stackoverflow.com/questions/17178432/how-to-find-all-commits-using-jgit-not-just-referenceable-ones
		// as a workaround we currently use all branches (includes master) and all tags for finding commits quickly
		Map<String, Ref> allRefs = repository.getAllRefs();

		RevWalk walk = new RevWalk(repository);
		
		// optimization: we only need the commit-ids here, so we can discard the contents right away
		walk.setRetainBody(false);
		
		try {
			// Store commits directly, not the SHA1 as getName() is a somewhat costly operation on RevCommit via formatHexChar()
			Set<RevCommit> seenHeadCommits = new HashSet<RevCommit>(allRefs.size());
			for(String ref : allRefs.keySet()) {
				Ref head = repository.getRef(ref);
				final RevCommit commit;
				try {
					commit = walk.parseCommit(head.getObjectId());
				} catch (IncorrectObjectTypeException e) {
					System.out.println("Invalid head-commit for ref " + ref + " and id: " + head.getObjectId().getName() + ": " + e);
					continue;
				}
	
				// only read commits of this ref if we did not add parents of this commit already
				if(seenHeadCommits.add(commit)) {
					addCommits(map, walk, commit, sub);
				}
			}
			//System.out.println("Had " + seen + " dupplicate commits");
		} finally {
			walk.dispose();
		}

		// use the ObjectIdSubclassMap for quick map-insertion and only afterwards convert the resulting commits
		// to Strings. ObjectIds can be compared much quicker as Strings as they only are 4 ints, not 40 character strings
		List<String> commits = new ArrayList<String>(map.size());

		Iterator<RevCommit> iterator = map.iterator();
		while(iterator.hasNext()) {
			RevObject commit = iterator.next();
			commits.add(commit.getName());
		}

		return commits;
	}

	private void addCommits(ObjectIdSubclassMap<RevCommit> map, RevWalk walk, RevCommit commit, String sub) throws IOException, MissingObjectException,
			IncorrectObjectTypeException {
		walk.markStart(commit);
		try {
			for(RevCommit rev : walk) {
				String name = rev.getName();
				if(sub == null || name.startsWith(sub)) {
					map.addIfAbsent(rev);
				}
			}
		} finally {
			walk.reset();
		}
	}

	/**
	 * Free resources held in thie instance, i.e. by releasing the Git repository resources held internally.
	 * 
	 * The instance is not useable after this call any more.
	 */
	@Override
	public void close() throws IOException {
		repository.close();
	}

	/**
	 * Retrieve directory-entries based on a commit-id and a given directory in that commit.
	 *
	 * @param commit The commit-id to show the path as-of
	 * @param path The path underneath the commit-id to list
	 *
	 * @return A list of file, directory and symlink elements underneath the given path
	 *
	 * @throws IllegalStateException If the path or the commit cannot be found or does not denote a directory
	 * @throws IOException If access to the Git repository fails
	 * @throws FileNotFoundException If the given path cannot be found as part of the commit-id
	 */
	public List<String> readElementsAt(String commit, String path) throws IOException {
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

    @Override
    public String toString() {
        // just return toString() from Repository as it prints out the git-directory
        return repository.toString();
    }	
}
