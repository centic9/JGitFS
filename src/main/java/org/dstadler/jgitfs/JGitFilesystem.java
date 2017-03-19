package org.dstadler.jgitfs;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import net.fusejna.DirectoryFiller;
import net.fusejna.ErrorCodes;
import net.fusejna.FuseException;
import net.fusejna.FuseFilesystem;
import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;
import net.fusejna.types.TypeMode.NodeType;
import net.fusejna.util.FuseFilesystemAdapterFull;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.dstadler.jgitfs.util.GitUtils;
import org.dstadler.jgitfs.util.JGitHelper;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;

/**
 * Implementation of the {@link FuseFilesystem} interfaces to
 * provide a view of branches/tags/stashes/commits of the given
 * Git repository.
 *
 * @author dominik.stadler
 */
public class JGitFilesystem extends FuseFilesystemAdapterFull implements Closeable {
    private static final long CACHE_TIMEOUT = 60 * 1000;    // one minute

    private long lastLinkCacheCleanup = System.currentTimeMillis();

    private final JGitHelper jgitHelper;
    private final Map<String, JGitFilesystem> jgitSubmodules = new HashMap<>();

    private final AtomicLong getattrStat = new AtomicLong();
    private final AtomicLong readStat = new AtomicLong();
    private final AtomicLong readdirStat = new AtomicLong();
    private final AtomicLong readlinkStat = new AtomicLong();

    /**
     * static set of directories to handle them quickly in getattr().
     */
    private static Set<String> DIRS = new HashSet<>();
    static {
        DIRS.add("/");
        DIRS.add("/branch");
        DIRS.add("/commit");
        DIRS.add("/remote");
        DIRS.add("/tag");
        DIRS.add("/submodule");
        DIRS.add("/stash");
        DIRS.add("/stashorig");
    }

    /**
     * Don't print out a warning for some directories which are queried by
     * some apps, e.g. Nautilus on Gnome
     */
    private static Set<String> IGNORED_DIRS = new HashSet<>();
    static {
        IGNORED_DIRS.add("/.hidden");
        IGNORED_DIRS.add("/.Trash");
        IGNORED_DIRS.add("/.Trash-1000");
    }

    /**
     * Construct the filesystem and create internal helpers.
     *
     * @param gitDir The directory where the Git repository can be found.
     * @param enableLogging If fuse-jna should log details about file/directory accesses
     * @throws IOException If opening the Git repository fails.
     */
    public JGitFilesystem(String gitDir, boolean enableLogging) throws IOException {
        super();

        // disable verbose logging
        log(enableLogging);

        jgitHelper = new JGitHelper(gitDir);

        // open a separate JGitFilesystem for any submodule found in the Git repository
        for(String subName : jgitHelper.allSubmodules()) {
            String subPath = jgitHelper.getSubmodulePath(subName);
            System.out.println("Preparing submodule " + subName + " at " + subPath);
            try {
                @SuppressWarnings("resource")
                JGitFilesystem jgitFS = new JGitFilesystem(this, subPath, enableLogging);
                jgitSubmodules.put(subName, jgitFS);
            } catch (IllegalArgumentException e) {
                System.out.println("Error adding submodule: " + subName + ": " + e);
            }
        }
    }

    /**
     * Creates a JGitFilesystem for a Git submodule.
     *
     * @param parent The JGitFilesystem for the parent Git repository.
     * @param submodulePath The path in the parent Git repository where the submodule is mounted.
     * @param enableLogging If fuse-jna should log details about file/directory accesses
     * @throws IOException If opening the Git repository fails.
     */
    public JGitFilesystem(JGitFilesystem parent, String submodulePath, boolean enableLogging) throws IOException {
        super();

        // disable verbose logging
        log(enableLogging);

        jgitHelper = new JGitHelper(parent.jgitHelper, submodulePath);
    }

    @Override
    protected String getName() {
        if(jgitHelper == null) {
            return null;
        }

        return jgitHelper.getName();
    }

    @Override
    public int getattr(final String path, final StatWrapper stat) {
        getattrStat.incrementAndGet();
        
        // known entries and directories beneath /commit are always directories
        if(DIRS.contains(path) || GitUtils.isCommitSub(path) || GitUtils.isCommitDir(path) || GitUtils.isSubmoduleName(path)) {
            stat.setMode(NodeType.DIRECTORY, true, false, true, true, false, true, false, false, false);
            return 0;
        } else if (GitUtils.isCommitSubDir(path)) {
            // for actual entries for a commit we need to read the file-type information from Git
            String commit = jgitHelper.readCommit(path);
            String file = jgitHelper.readPath(path);

            try {
                jgitHelper.readType(commit, file, stat);
            } catch (@SuppressWarnings("unused") FileNotFoundException e) {
                return -ErrorCodes.ENOENT();
            } catch (Exception e) {
                throw new IllegalStateException("Error reading type of path " + path + ", commit " + commit + " and file " + file, e);
            }
            return 0;
        } else if (GitUtils.isBranchDir(path) || GitUtils.isTagDir(path) || GitUtils.isRemoteDir(path) ||
                GitUtils.isStashDir(path) || GitUtils.isStashOrigDir(path)) {
            // entries under /branch and /tag are always symbolic links
            stat.setMode(NodeType.SYMBOLIC_LINK, true, true, true, true, true, true, true, true, true);
            return 0;
        } else if (GitUtils.isSubmodulePath(path)) {
            // delegate submodule-requests to the separate filesystem
            Pair<String,String> sub = GitUtils.splitSubmodule(path);

            return jgitSubmodules.get(sub.getLeft()).getattr(sub.getRight(), stat);
        }

        // all others are reported as "not found"
        // don't throw an exception here as we get requests for some files/directories, e.g. .hidden or .Trash
        boolean show = true;
        for(String ignore : IGNORED_DIRS) {
            if(path.endsWith(ignore)) {
                show = false;
                break;
            }
        }
        if(show) {
            System.out.println("Had unknown path " + path + " in getattr()");
        }
        return -ErrorCodes.ENOENT();
    }

    @Override
    public int read(final String path, final ByteBuffer buffer, final long size, final long offset, final FileInfoWrapper info) {
        readStat.incrementAndGet();
        
        // delegate submodule-requests to the separate filesystem
        if (GitUtils.isSubmodulePath(path)) {
            Pair<String,String> sub = GitUtils.splitSubmodule(path);

            return jgitSubmodules.get(sub.getLeft()).read(sub.getRight(), buffer, size, offset, info);
        }

        String commit = jgitHelper.readCommit(path);
        String file = jgitHelper.readPath(path);

        try {
            try (InputStream openFile = jgitHelper.openFile(commit, file)) {
                // skip until we are at the offset
                IOUtils.skip(openFile, offset);

                byte[] arr = new byte[(int)size];
                int read = openFile.read(arr, 0, (int)size);
                // -1 indicates EOF => nothing to put into the buffer
                if(read == -1) {
                    return 0;
                }

                buffer.put(arr, 0, read);

                return read;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Error reading contents of path " + path + ", commit " + commit + " and file " + file, e);
        }
    }

    @Override
    public int readdir(final String path, final DirectoryFiller filler) {
        readdirStat.incrementAndGet();
        
        if(path.equals("/")) {
            // populate top-level directory with all supported sub-directories
            filler.add("/branch");
            filler.add("/commit");
            filler.add("/remote");
            filler.add("/tag");
            filler.add("/submodule");
            filler.add("/stash");
            filler.add("/stashorig");

            // TODO: implement later
//            filler.add("/index");    - use DirCache?
//            filler.add("/workspace"); - use WorkingTreeIterator?
//            filler.add("/git") => symbolic link to the source dir
//            filler.add("/notes"); - notes
//            filler.add("/perfile/branch"); - history per file
//            filler.add("/perfile/commit"); - history per file
//            filler.add("/perfile/remote"); - history per file
//            filler.add("/perfile/tag"); - history per file
//          filler.add("/date"); - commits sorted by date, i.e. 2013/03/04/34:23/...

            return 0;
        } else if (path.equals("/commit")) {
            // list two-char subs for all commits
            try {
                Collection<String> items = jgitHelper.allCommitSubs();
                items.forEach(filler::add);
            } catch (Exception e) {
                throw new IllegalStateException("Error reading elements of path " + path, e);
            }

            return 0;
        } else if (GitUtils.isCommitSub(path)) {
            // get the sub that is requested here
            String sub = StringUtils.removeStart(path, GitUtils.COMMIT_SLASH);
            try {
                // list all commits for the requested sub
                Collection<String> items = jgitHelper.allCommits(sub);
                for(String item : items) {
                    filler.add(item.substring(2));
                }
            } catch (Exception e) {
                throw new IllegalStateException("Error reading elements of path " + path, e);
            }

            return 0;
        } else if (GitUtils.isCommitDir(path) || GitUtils.isCommitSubDir(path)) {
            // handle listing the root dir of a commit or a file beneath that
            String commit = jgitHelper.readCommit(path);
            String dir = jgitHelper.readPath(path);

            try {
                List<String> items = jgitHelper.readElementsAt(commit, dir);
                items.forEach(filler::add);
            } catch (Exception e) {
                throw new IllegalStateException("Error reading elements of path " + path + ", commit " + commit + " and directory " + dir, e);
            }

            return 0;
        } else if (path.equals("/tag")) {
            try {
                List<String> items = jgitHelper.getTags();
                items.forEach(filler::add);
            } catch (Exception e) {
                throw new IllegalStateException("Error reading tags", e);
            }

            return 0;
        } else if (path.equals("/branch")) {
            try {
                List<String> items = jgitHelper.getBranches();
                items.forEach(filler::add);
            } catch (Exception e) {
                throw new IllegalStateException("Error reading branches", e);
            }

            return 0;
        } else if (path.equals("/remote")) {
            try {
                List<String> items = jgitHelper.getRemotes();
                items.forEach(filler::add);
            } catch (Exception e) {
                throw new IllegalStateException("Error reading remotes", e);
            }

            return 0;
        } else if (path.equals("/submodule")) {
            // list names of all submodules
            try {
                Collection<String> items = jgitHelper.allSubmodules();
                items.forEach(filler::add);
            } catch (Exception e) {
                throw new IllegalStateException("Error reading elements of path " + path, e);
            }

            return 0;
        } else if (GitUtils.isSubmodulePath(path)) {
            // delegate submodule-requests to the separate filesystem
            Pair<String, String> sub = GitUtils.splitSubmodule(path);

            return jgitSubmodules.get(sub.getLeft()).readdir(sub.getRight(), filler);
        } else if (path.equals("/stash") || path.equals("/stashorig")) {
            try {
                List<String> items = jgitHelper.getStashes();
                items.forEach(filler::add);
            } catch (Exception e) {
                throw new IllegalStateException("Error reading stashes", e);
            }

            return 0;
        }

        throw new IllegalStateException("Error reading directories in path " + path);
    }

    /**
     * A cache for symlinks from branches/tags to commits, this is useful as queries for symlinks
     * are done very often as each access to a file on a branch also requires the symlink to the
     * actual commit to be resolved. This cache greatly improves the speed of these accesses.
     *
     * This makes use of the Google Guava LoadingCache features to automatically populate
     * entries when they are missing which makes the usage of the cache very simple.
     */
    private LoadingCache<String, byte[]> linkCache = CacheBuilder.newBuilder()
               .maximumSize(1000)
               .expireAfterWrite(1, TimeUnit.MINUTES)
               .build(
                   new CacheLoader<String, byte[]>() {
                     @Override
                    public byte[] load(String path) {
                        try {
                            final String commit;
                            if(GitUtils.isBranchDir(path)) {
                                commit = jgitHelper.getBranchHeadCommit(StringUtils.removeStart(path, GitUtils.BRANCH_SLASH));
                            } else if (GitUtils.isTagDir(path)) {
                                commit = jgitHelper.getTagHeadCommit(StringUtils.removeStart(path, GitUtils.TAG_SLASH));
                            } else if(GitUtils.isRemoteDir(path)) {
                                commit = jgitHelper.getRemoteHeadCommit(StringUtils.removeStart(path, GitUtils.REMOTE_SLASH));
                            } else if(GitUtils.isStashDir(path)) {
                                commit = jgitHelper.getStashHeadCommit(StringUtils.removeStart(path, GitUtils.STASH_SLASH));
                            } else if(GitUtils.isStashOrigDir(path)) {
                                commit = jgitHelper.getStashOrigCommit(StringUtils.removeStart(path, GitUtils.STASHORIG_SLASH));
                            } else {
                                String lcommit = jgitHelper.readCommit(path);
                                String dir = jgitHelper.readPath(path);

                                // for symlinks that are actually git-links for a submodule, we need to redirect back to the
                                // separate submodule-folder with the correct submodule name filled in
                                if(jgitHelper.isGitLink(lcommit, dir)) {
                                    // TODO: does this still work with submodules in directories further down?
                                    String subName = jgitHelper.getSubmoduleAt(dir);
                                    String subHead = jgitHelper.getSubmoduleHead(subName);
                                    return ("../../.." + GitUtils.SUBMODULE_SLASH + subName + GitUtils.COMMIT_SLASH +
                                            subHead.substring(0,2) + "/" + subHead.substring(2)).getBytes();
                                }

                                return jgitHelper.readSymlink(lcommit, dir).getBytes();
                            }

                            if(commit == null) {
                                throw new FileNotFoundException("Had unknown tag/branch/remote " + path + " in readlink()");
                            }

                            return (".." + GitUtils.COMMIT_SLASH + commit.substring(0, 2) + "/" + commit.substring(2)).getBytes();
                        } catch (Exception e) {
                            throw new IllegalStateException("Error reading commit of tag/branch-path " + path, e);
                        }
                     }
                   });

    @Override
    public int readlink(String path, ByteBuffer buffer, long size) {
        readlinkStat.incrementAndGet();
        
        if (GitUtils.isSubmodulePath(path)) {
            // delegate submodule-requests to the separate filesystem
            Pair<String, String> sub = GitUtils.splitSubmodule(path);

            return jgitSubmodules.get(sub.getLeft()).readlink(sub.getRight(), buffer, size);
        }

        // ensure that we evict caches sometimes, Google Guava does not make guarantees that
        // eviction happens automatically in a mostly read-only cache
        if(System.currentTimeMillis() > (lastLinkCacheCleanup + CACHE_TIMEOUT)) {
            System.out.println("Perform manual cache maintenance for " + jgitHelper.toString() + " after " + ((System.currentTimeMillis() - lastLinkCacheCleanup)/1000) + " seconds");
            lastLinkCacheCleanup = System.currentTimeMillis();
            linkCache.cleanUp();
        }

        // use the cache to speed up access, symlinks are always queried even for sub-path access, so we get lots of requests for these!
        byte[] linkTarget;
        try {
            linkTarget = linkCache.get(path);
            if(linkTarget == null) {
                 throw new IllegalStateException("Error reading commit of tag/branch-path " + path);
            }

            // buffer overflow checks are done by the calls to put() itself per javadoc,
            // currently we will throw an exception to the outside, experiment showed that we support 4097 bytes of path-length on 64-bit Ubuntu this way
            buffer.put(linkTarget);
            // zero-byte is appended by fuse-jna itself

            // returning the size as per readlink(2) spec causes fuse errors: return cachedCommit.length;
            return 0;
        } catch (UncheckedExecutionException e) {
            if(e.getCause().getCause() instanceof FileNotFoundException) {
                return -ErrorCodes.ENOENT();
            }
            throw new IllegalStateException("Error reading commit of tag/branch-path " + path, e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Error reading commit of tag/branch-path " + path, e);
        }
    }

    /**
     * Free up resources held for the Git repository and unmount the FUSE-filesystem.
     *
     * @throws IOException If an error occurred while closing the Git repository or while unmounting the filesystem.
     */
    @Override
    public void close() throws IOException {
        // also close any submodules that we found
        for(Map.Entry<String, JGitFilesystem> entry : jgitSubmodules.entrySet()) {
            System.out.println("Closing submodule " + entry.getKey());
            entry.getValue().close();
        }

        jgitHelper.close();
        if(isMounted()) {
            try {
                unmount();
            } catch (FuseException e) {
                throw new IOException(e);
            }
        }
    }
    
    public String getStats() {
        return "getattr: " + getattrStat.get() + ", read: " + readStat.get() + 
                ", readdir: " + readdirStat.get() + ", readlink: " + readlinkStat.get();
    }
}
