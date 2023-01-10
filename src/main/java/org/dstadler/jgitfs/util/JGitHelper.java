package org.dstadler.jgitfs.util;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import net.fusejna.StructStat.StatWrapper;
import net.fusejna.types.TypeMode.NodeType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdSubclassMap;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.submodule.SubmoduleStatus;
import org.eclipse.jgit.submodule.SubmoduleStatusType;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Helper class which#apache-poi encapsulates access to the actual Git repository by
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
     * @throws IOException           If opening the Git repository fails
     */
    public JGitHelper(String pGitDir) throws IOException {
        String gitDir = pGitDir;
        if (!gitDir.endsWith("/.git") && !gitDir.endsWith("/.git/")) {
            gitDir = gitDir + "/.git";
        }
        if (!new File(gitDir).exists()) {
            throw new IllegalStateException("Could not find git repository at " + gitDir);
        }

        WindowCacheConfig cfg = new WindowCacheConfig();
        // set a lower stream file threshold as we want to run the code with
        // very limited memory, e.g. -Xmx60m and having the default of 50MB
        // would cause OOM if access is done in parallel
        cfg.setStreamFileThreshold(1024 * 1024);
        cfg.install();

        System.out.println("Using git repo at " + gitDir);
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        repository = builder.setGitDir(new File(gitDir))
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build();
        git = new Git(repository);
    }

    /**
     * Initialize a JGitHelper for a Git submodule, this requires the
     * parent JGitHelper object in order to correct open the repository
     * for the submodule.
     *
     * @param parent        The JGitHelper object for the parent Git repository
     * @param submodulePath The path where the submodule is linked in
     * @throws IllegalArgumentException If no submodule can be opened at the given path
     * @throws IOException              If opening the Git repository fails
     */
    public JGitHelper(JGitHelper parent, String submodulePath) throws IOException {
        System.out.println("Using submodule at " + submodulePath + " via git repository at " + parent.repository.getDirectory());
        repository = SubmoduleWalk.getSubmoduleRepository(parent.repository, submodulePath);
        if (repository == null) {
            throw new IllegalArgumentException("Could not open submodule at path " + submodulePath + " in repository " + parent.repository.getDirectory());
        }
        git = new Git(repository);
    }

    public String getName() {
        if (!repository.isBare()) {
            return repository.getWorkTree().getName();
        }
        return new File(StringUtils.removeEnd(
                repository.getDirectory().getAbsolutePath(),
                ".git")).getName();
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
        return StringUtils.substring(file, 40 + 2);    // cut away commitish and two slashes
    }

    /**
     * Populate the StatWrapper with the necessary values like mode, uid, gid and type of file/directory/symlink.
     *
     * @param commit The commit-id as-of which we read the data
     * @param path   The path to the file/directory
     * @param stat   The StatWrapper instance to populate
     * @throws IllegalStateException If the path or the commit cannot be found or an unknown type is encountered
     * @throws IOException           If access to the Git repository fails
     * @throws FileNotFoundException If the given path cannot be found as part of the given commit-id
     */
    public void readType(String commit, String path, StatWrapper stat) throws IOException {
        RevCommit revCommit = buildRevCommit(commit);

        // set time and user-id/group-id
        stat.ctime(revCommit.getCommitTime());
        stat.mtime(revCommit.getCommitTime());
        stat.uid(GitUtils.UID);
        stat.gid(GitUtils.GID);

        // and using commit's tree find the path
        RevTree tree = revCommit.getTree();

        // now read the file/directory attributes
        try (TreeWalk treeWalk = buildTreeWalk(tree, path)) {
            FileMode fileMode = treeWalk.getFileMode(0);
            if (fileMode.equals(FileMode.EXECUTABLE_FILE) ||
                    fileMode.equals(FileMode.REGULAR_FILE)) {
                ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                stat.size(loader.getSize());
                stat.setMode(NodeType.FILE,
                        true, false, fileMode.equals(FileMode.EXECUTABLE_FILE),
                        true, false, fileMode.equals(FileMode.EXECUTABLE_FILE),
                        false, false, false);
                return;
            } else if (fileMode.equals(FileMode.TREE)) {
                stat.setMode(NodeType.DIRECTORY, true, false, true, true, false, true, false, false, false);
                return;
            } else if (fileMode.equals(FileMode.SYMLINK)) {
                stat.setMode(NodeType.SYMBOLIC_LINK, true, false, true, true, false, true, false, false, false);
                return;
            } else if (fileMode.equals(FileMode.GITLINK)) {
                stat.setMode(NodeType.SYMBOLIC_LINK, true, false, true, true, false, true, false, false, false);
                return;
            }

            throw new IllegalStateException("Found unknown FileMode 0o" + Integer.toOctalString(fileMode.getBits()) + "/" + fileMode.getClass() +
                    " in Git for commit '" + commit + "' and path '" + path + "'");
        }
    }

    /**
     * Check if the given path in the given commit denotes a git link to a submodule.
     *
     * @param commit The commit-id as-of which we read the data
     * @param path   The path to the file/directory
     * @return true if the path in the given commit denotes a git submodule, false otherwise.
     * @throws IOException If access to the Git repository fails
     */
    public boolean isGitLink(String commit, String path) throws IOException {
        RevCommit revCommit = buildRevCommit(commit);

        // and using commit's tree find the path
        RevTree tree = revCommit.getTree();

        // now read the file/directory attributes
        try (TreeWalk treeWalk = buildTreeWalk(tree, path)) {
            FileMode fileMode = treeWalk.getFileMode(0);

            // TODO: this also returns true for a normal symbolic link,
            // how can we determine the difference?
            return fileMode.equals(FileMode.GITLINK);
        }
    }

    /**
     * Read the target file for the given symlink as part of the given commit.
     *
     * @param commit the commit-id as-of which we read the symlink
     * @param path   the path to the symlink
     * @return the target of the symlink, relative to the directory of the symlink itself
     * @throws IOException              If an error occurs while reading from the Git repository
     * @throws FileNotFoundException    If the given path cannot be found in the given commit-id
     * @throws IllegalArgumentException If the given path does not denote a symlink
     */
    public String readSymlink(String commit, String path) throws IOException {
        RevCommit revCommit = buildRevCommit(commit);

        // and using commit's tree find the path
        RevTree tree = revCommit.getTree();

        // now read the file/directory attributes
        final FileMode fileMode;
        try (TreeWalk treeWalk = buildTreeWalk(tree, path)) {
            fileMode = treeWalk.getFileMode(0);
        }

        if (!fileMode.equals(FileMode.SYMLINK) && !fileMode.equals(FileMode.GITLINK)) {
            throw new IllegalArgumentException("Had request for symlink-target which is not a symlink, commit '" + commit + "' and path '" + path + "': " + fileMode.getBits());
        }

        // TODO: add full support for Submodules
        if (fileMode.equals(FileMode.GITLINK)) {
            throw new UnsupportedOperationException("Support for git submodules is not yet available, cannot read path " + path + " of commit " + commit);
        }

        // try to read the file-data as it contains the symlink target
        try (InputStream openFile = openFile(commit, path)) {
            return IOUtils.toString(openFile, StandardCharsets.UTF_8);
        }
    }

    /**
     * Retrieve the contents of the given file as-of the given commit.
     *
     * @param commit The commit-id as-of which we read the data
     * @param path   The path to the file/directory
     * @return An InputStream which can be used to read the contents of the file.
     * @throws IllegalStateException If the path or the commit cannot be found or does not denote a file
     * @throws IOException           If access to the Git repository fails
     * @throws FileNotFoundException If the given path cannot be found in the given commit-id
     */
    public InputStream openFile(String commit, String path) throws IOException {
        RevCommit revCommit = buildRevCommit(commit);

        // use the commit's tree find the path
        RevTree tree = revCommit.getTree();
        //System.out.println("Having tree: " + tree + " for commit " + commit);

        // now try to find a specific file
        try (TreeWalk treeWalk = buildTreeWalk(tree, path)) {
            if ((treeWalk.getFileMode(0).getBits() & FileMode.TYPE_FILE) == 0) {
                throw new IllegalStateException("Tried to read the contents of a non-file for commit '" + commit + "' and path '" + path + "', had filemode " + treeWalk.getFileMode(0).getBits());
            }

            // then open the file for reading.
            ObjectId objectId = treeWalk.getObjectId(0);
            ObjectLoader loader = repository.open(objectId);

            // finally open an InputStream for the file contents
            return loader.openStream();
        }
    }

    private TreeWalk buildTreeWalk(RevTree tree, final String path) throws IOException {
        TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree);

        if (treeWalk == null) {
            throw new FileNotFoundException("Did not find expected file '" + path + "' in tree '" + tree.getName() + "'");
        }

        return treeWalk;
    }

    private RevCommit buildRevCommit(String commit) throws IOException {
        // a RevWalk allows to walk over commits based on some filtering that is defined
        try (RevWalk revWalk = new RevWalk(repository)) {
            return revWalk.parseCommit(ObjectId.fromString(commit));
        }
    }

    /**
     * Return all local branches, excluding any remote branches.
     * <p>
     * To ease implementation, slashes in branch-names are replaced by underscore. Also
     * entries starting with refs/heads/ are listed with their short name as well
     *
     * @return A list of branch-names
     * @throws IOException If accessing the Git repository fails
     */
    public List<String> getBranches() throws IOException {
        final List<Ref> branchRefs = readBranches();
        List<String> branches = new ArrayList<>();
        for (Ref ref : branchRefs) {
            String name = adjustName(ref.getName());
            branches.add(name);
            if (name.startsWith("refs_heads_")) {
                branches.add(StringUtils.removeStart(name, "refs_heads_"));
            }
        }
        return branches;
    }

    /**
     * Return the commit-id for the given branch.
     * <p>
     * To ease implementation, slashes in branch-names are replaced by underscore. Also
     * entries starting with refs/heads/ are listed with their short name as well
     *
     * @param branch The branch to read data for, both the short-name and the name with refs/heads/... is possible
     * @return A commit-id if found or null if not found.
     * @throws IOException If accessing the Git repository fails
     */
    public String getBranchHeadCommit(String branch) throws IOException {
        final List<Ref> branchRefs = readBranches();
        for (Ref ref : branchRefs) {
            String name = adjustName(ref.getName());
            //System.out.println("Had branch: " + name);
            if (name.equals(branch) || name.equals("refs_heads_" + branch)) {
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
     * <p>
     * To ease implementation, slashes in names are replaced by underscore. Also
     * entries starting with refs/remotes/ are listed with their short name as well
     *
     * @return A list of remote-names
     * @throws IOException If accessing the Git repository fails
     */
    public List<String> getRemotes() throws IOException {
        final List<Ref> remoteRefs = readRemotes();
        List<String> remotes = new ArrayList<>();
        for (Ref ref : remoteRefs) {
            String name = adjustName(ref.getName());
            remotes.add(name);
            if (name.startsWith("refs_remotes_")) {
                remotes.add(StringUtils.removeStart(name, "refs_remotes_"));
            }
        }
        return remotes;
    }

    /**
     * Return the commit-id for the given remote.
     * <p>
     * To ease implementation, slashes in names are replaced by underscore. Also
     * entries starting with refs/remotes/ are listed with their short name as well
     *
     * @param remote The remote name to read data for, both the short-name and the name with refs/remotes/... is possible
     * @return A commit-id if found or null if not found.
     * @throws IOException If accessing the Git repository fails
     */
    public String getRemoteHeadCommit(String remote) throws IOException {
        final List<Ref> remoteRefs = readRemotes();
        for (Ref ref : remoteRefs) {
            String name = adjustName(ref.getName());
            //System.out.println("Had branch: " + name);
            if (name.equals(remote) || name.equals("refs_remotes_" + remote)) {
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
     * <p>
     * To ease implementation, slashes in branch-names are replaced by underscore. Also
     * entries starting with refs/tags/ are listed with their short name as well
     *
     * @return A list of branch-names
     * @throws IOException If accessing the Git repository fails
     */
    public List<String> getTags() throws IOException {
        List<Ref> tagRefs = readTags();
        List<String> tags = new ArrayList<>();
        for (Ref ref : tagRefs) {
            String name = adjustName(ref.getName());
            tags.add(name);
            if (name.startsWith("refs_tags_")) {
                tags.add(StringUtils.removeStart(name, "refs_tags_"));
            }
        }
        return tags;
    }

    /**
     * Return the commit-id for the given tag.
     * <p>
     * To ease implementation, slashes in tag-names are replaced by underscore. Also
     * entries starting with refs/tags/ are listed with their short name as well
     *
     * @param tag The tag to read data for, both the short-name and the name with refs/tags/... is possible
     * @return A commit-id if found or null if not found.
     * @throws IOException If accessing the Git repository fails
     */
    public String getTagHeadCommit(String tag) throws IOException {
        List<Ref> tagRefs = readTags();
        for (Ref ref : tagRefs) {
            String name = adjustName(ref.getName());
            //System.out.println("Had tag: " + name);
            if (name.equals(tag) || name.equals("refs_tags_" + tag)) {
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
        // TODO: handle tags with slash as sub-dirs instead of replacing with underscore
        return name.replace("/", "_");
    }

    /**
     * Returns a collection of all submodules in the current repository.
     *
     * @return A collection containing the name of all known submodules.
     * @throws IOException If accessing the Git repository fails
     */
    public Collection<String> allSubmodules() throws IOException {
        if (repository.isBare()) {
            System.out.println("Cannot list submodules for bare repository at " + repository.getDirectory());
            return Collections.emptyList();
        }

        try {
            return git.submoduleStatus().call().keySet();
        } catch (GitAPIException e) {
            throw new IOException(e);
        }
    }

    /**
     * Returns the name of the git submodule linked at the given path.
     *
     * @param path the path where the git submodule is linked in.
     * @return The name of the git submodule.
     * @throws NoSuchElementException if the given path does not point to a git submodule
     * @throws IOException            If accessing the Git repository fails
     */
    public String getSubmoduleAt(String path) throws IOException {
        try {
            Map<String, SubmoduleStatus> set = git.submoduleStatus().addPath(path).call();
            if (set.isEmpty()) {
                throw new NoSuchElementException("Could not read submodule at path " + path);
            }
            return set.keySet().iterator().next();
        } catch (GitAPIException e) {
            throw new IOException(e);
        }
    }

    /**
     * Returns the path where the given submodule is linked.
     *
     * @param name the name of the Git submodule.
     * @return The path where the Git submodule is linked.
     * @throws NoSuchElementException if the given name is not known.
     * @throws IOException            If accessing the Git repository fails
     */
    public String getSubmodulePath(String name) throws IOException {
        try {
            Map<String, SubmoduleStatus> set = git.submoduleStatus().call();
            for (Map.Entry<String, SubmoduleStatus> entry : set.entrySet()) {
                if (entry.getKey().equals(name)) {
                    return entry.getValue().getPath();
                }
            }
            throw new NoSuchElementException("Could not read submodule " + name);
        } catch (GitAPIException e) {
            throw new IOException(e);
        }
    }

    /**
     * Returns the path where the given submodule is linked.
     *
     * @param name the name of the Git submodule.
     * @return The path where the Git submodule is linked.
     * @throws NoSuchElementException if the given name is not known.
     * @throws IOException            If accessing the Git repository fails
     */
    public String getSubmoduleHead(String name) throws IOException {
        try {
            Map<String, SubmoduleStatus> set = git.submoduleStatus().call();
            for (Map.Entry<String, SubmoduleStatus> entry : set.entrySet()) {
                if (entry.getKey().equals(name)) {
                    SubmoduleStatus value = entry.getValue();
                    SubmoduleStatusType type = value.getType();
                    if (type == SubmoduleStatusType.MISSING ||
                            type == SubmoduleStatusType.UNINITIALIZED) {
                        throw new NoSuchElementException("Could not read submodule " + name + " because it is in state " + type);
                    }
                    return value.getHeadId().getName();
                }
            }
            throw new NoSuchElementException("Could not read submodule " + name);
        } catch (GitAPIException e) {
            throw new IOException(e);
        }
    }

    /**
     * Return all stashes
     * <p>
     * To ease implementation, slashes in stash-names are replaced by underscore. Also
     * entries starting with refs/heads/ are listed with their short name as well
     *
     * @return A list of branch-names
     * @throws IOException If accessing the Git repository fails
     */
    public List<String> getStashes() throws IOException {
        List<String> stashNames = new ArrayList<>();

        // first a list of all stashes
        for (int i = 0; i < readStashes().size(); i++) {
            // for now just use the simple numbering as done in git stash list without the commit message
            stashNames.add(getStashName(i));
        }

        return stashNames;
    }

    private String getStashName(int i) {
        return "stash@{" + i + "}";
    }

    /**
     * Return the commit-id for the given stash.
     *
     * @param stash The stash to read data for, usually just a "stash@{<nr>}".
     * @return A commit-id if found or null if not found.
     * @throws IOException If accessing the Git repository fails
     */
    public String getStashHeadCommit(String stash) throws IOException {
        final Collection<RevCommit> stashes = readStashes();
        int i = 0;
        for (RevCommit rev : stashes) {
            if (getStashName(i).equals(stash)) {
                return rev.getName();
            }
            i++;
        }

        return null;
    }

    /**
     * Return the commit-id for the parent commit of the given stash.
     *
     * @param stash The stash to read data for, usually just a "stash@{<nr>}".
     * @return A commit-id if found or null if not found.
     * @throws IOException If accessing the Git repository fails
     */
    public String getStashOrigCommit(String stash) throws IOException {
        final Collection<RevCommit> stashes = readStashes();
        int i = 0;
        for (RevCommit rev : stashes) {
            if (getStashName(i).equals(stash)) {
                return rev.getParent(0).getName();
            }
            i++;
        }

        return null;
    }

    private Collection<RevCommit> readStashes() throws IOException {
        final Collection<RevCommit> stashes;
        try {
            stashes = git.stashList().call();
        } catch (GitAPIException e) {
            throw new IOException(e);
        }
        return stashes;
    }

    /**
     * Retrieve a list of all two-digit commit-subs which allow to build the first directory level
     * of a commit-file-structure.
     *
     * @return A Set containing all used commit-subs where the filesystem can drill down to actual commits.
     * @throws IOException If access to the Git repository fails.
     */
    public Set<String> allCommitSubs() throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            // optimization: we only need the commit-ids here, so we can discard the contents right away
            walk.setRetainBody(false);

            // use all refs (tags, branches, remotes, ...) for finding commits quickly
            addAllRefs(walk);

            // now iterate over all commits to find out which 2-hex-char sub-directories should be provided
            Set<String> commitSubs = new HashSet<>();
            for (RevCommit rev : walk) {
                String name = rev.getName();
                commitSubs.add(name.substring(0, 2));

                // we can leave the loop as soon as we have all two-digit values, which is typically the case for large repositories
                if (commitSubs.size() >= 256) {
                    break;
                }
            }

            walk.dispose();

            return commitSubs;
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
        try (RevWalk walk = new RevWalk(repository)) {
            // optimization: we only need the commit-ids here, so we can discard the contents right away
            walk.setRetainBody(false);

            // use all refs (tags, branches, remotes, ...) for finding commits quickly
            addAllRefs(walk);

            ObjectIdSubclassMap<RevCommit> map = new ObjectIdSubclassMap<>();
            for (RevCommit rev : walk) {
                String name = rev.getName();
                if (sub == null || name.startsWith(sub)) {
                    map.addIfAbsent(rev);
                }
            }

            walk.dispose();

            // use the ObjectIdSubclassMap for quick map-insertion and only afterwards convert the resulting commits
            // to Strings. ObjectIds can be compared much quicker as Strings as they only are 4 ints, not 40 character strings
            List<String> commits = new ArrayList<>(map.size());
            for (RevCommit commit : map) {
                commits.add(commit.getName());
            }

            return commits;
        }
    }

    private void addAllRefs(RevWalk walk) throws IOException {
        // TODO: we do not read unreferenced commits here, it would be nice to be able to access these as well here
        // see http://stackoverflow.com/questions/17178432/how-to-find-all-commits-using-jgit-not-just-referenceable-ones
        // as a workaround we currently use all branches (includes master) and all tags for finding commits quickly
        List<Ref> allRefs = repository.getRefDatabase().getRefs();
        for (Ref head : allRefs) {
            final RevCommit commit;
            try {
                commit = walk.parseCommit(head.getObjectId());
            } catch (IncorrectObjectTypeException e) {
                System.out.println("Invalid head-commit for ref " + head + " and id: " + head.getObjectId().getName() + ": " + e);
                continue;
            }
            walk.markStart(commit);
        }
    }

    /**
     * Free resources held in the instance, i.e. by releasing the Git repository resources held internally.
     * <p>
     * The instance is not usable after this call any more.
     */
    @Override
    public void close() {
        repository.close();
    }

    /**
     * Retrieve directory-entries based on a commit-id and a given directory in that commit.
     *
     * @param commit The commit-id to show the path as-of
     * @param path   The path underneath the commit-id to list
     * @return A list of file, directory and symlink elements underneath the given path
     * @throws IllegalStateException If the path or the commit cannot be found or does not denote a directory
     * @throws IOException           If access to the Git repository fails
     * @throws FileNotFoundException If the given path cannot be found as part of the commit-id
     */
    public List<String> readElementsAt(String commit, String path) throws IOException {
        RevCommit revCommit = buildRevCommit(commit);

        // and using commit's tree find the path
        RevTree tree = revCommit.getTree();
        //System.out.println("Having tree: " + tree + " for commit " + commit);

        List<String> items = new ArrayList<>();

        // shortcut for root-path
        if (path.isEmpty()) {
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(false);
                treeWalk.setPostOrderTraversal(false);

                while (treeWalk.next()) {
                    items.add(treeWalk.getPathString());
                }
            }
        } else {
            // now try to find a specific file
            try (TreeWalk treeWalk = buildTreeWalk(tree, path)) {
                if ((treeWalk.getFileMode(0).getBits() & FileMode.TYPE_TREE) == 0) {
                    throw new IllegalStateException("Tried to read the elements of a non-tree for commit '" + commit + "' and path '" + path + "', had filemode " + treeWalk.getFileMode(0).getBits());
                }

                try (TreeWalk dirWalk = new TreeWalk(repository)) {
                    dirWalk.addTree(treeWalk.getObjectId(0));
                    dirWalk.setRecursive(false);
                    while (dirWalk.next()) {
                        items.add(dirWalk.getPathString());
                    }
                }
            }
        }

        return items;
    }

    @Override
    public String toString() {
        // just return toString() from Repository as it prints out the git-directory
        return repository.toString();
    }
}
