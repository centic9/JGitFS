package org.dstadler.jgitfs;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import net.fusejna.DirectoryFiller;
import net.fusejna.ErrorCodes;
import net.fusejna.FuseException;
import net.fusejna.FuseFilesystem;
import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;
import net.fusejna.types.TypeMode.NodeType;
import net.fusejna.util.FuseFilesystemAdapterFull;

import org.apache.commons.lang3.StringUtils;
import org.dstadler.jgitfs.util.GitUtils;
import org.dstadler.jgitfs.util.JGitHelper;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;

/**
 * Implementation of the {@link FuseFilesystem} interfaces to 
 * provide a view of branches/tags/commits of the given
 * Git repository.
 *
 * @author dominik.stadler
 */
public class JGitFilesystem extends FuseFilesystemAdapterFull implements Closeable {
	private static final long CACHE_TIMEOUT = 60 * 1000;	// one minute

	private long lastLinkCacheCleanup = System.currentTimeMillis();

	private final JGitHelper jgitHelper;

	/**
	 * static set of directories to handle them quickly in getattr().
	 */
	private static Set<String> DIRS = new HashSet<String>();
	static {
		DIRS.add("/");
		DIRS.add("/commit");
		DIRS.add("/branch");
		DIRS.add("/tag");

		// directories looked for by gnome/Linux/..., do not list them, but rather return ENOENT immediately
//		DIRS.add("/.Trash");
//		DIRS.add("/.Trash-1000");
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
	}

	@Override
	public int getattr(final String path, final StatWrapper stat)
	{
		// known entries and directories beneath /commit are always directories
		if(DIRS.contains(path) || GitUtils.isCommitSub(path) || GitUtils.isCommitDir(path)) {
			stat.setMode(NodeType.DIRECTORY, true, false, true, true, false, true, false, false, false);
			return 0;
		} else if (GitUtils.isCommitSubDir(path)) {
			// for actual entries for a commit we need to read the file-type information from Git
			String commit = jgitHelper.readCommit(path);
			String file = jgitHelper.readPath(path);

			try {
				jgitHelper.readType(commit, file, stat);
			} catch (Exception e) {
				throw new IllegalStateException("Error reading type of path " + path + ", found commit " + commit + " and file " + file, e);
			}
			return 0;
		} else if (GitUtils.isBranchDir(path) || GitUtils.isTagDir(path)) {
			// entries under /branch and /tag are always symbolic links
			stat.setMode(NodeType.SYMBOLIC_LINK, true, true, true, true, true, true, true, true, true);
			return 0;
		}

		// all others are reported as "not found"
		// don't throw an exception here as we get requests for some files/directories, e.g. .hidden or .Trash
		System.out.println("Had unknown path " + path + " in getattr()");
		return -ErrorCodes.ENOENT();
	}

	@Override
	public int read(final String path, final ByteBuffer buffer, final long size, final long offset, final FileInfoWrapper info) {
		String commit = jgitHelper.readCommit(path);
		String file = jgitHelper.readPath(path);

		try {
			InputStream openFile = jgitHelper.openFile(commit, file);

			try {
				// skip until we are at the offset
				openFile.skip(offset);

				byte[] arr = new byte[(int)size];
				int read = openFile.read(arr, 0, (int)size);
				buffer.put(arr);

				return read;
			} finally {
				openFile.close();
			}
		} catch (Exception e) {
			throw new IllegalStateException("Error reading contents of path " + path + ", found commit " + commit + " and file " + file, e);
		}
	}

	@Override
	public int readdir(final String path, final DirectoryFiller filler) {
		if(path.equals("/")) {
			// populate top-level directory with all supported sub-directories
			filler.add("/commit");
			filler.add("/branch");
			filler.add("/tag");

			// TODO: implement later
//			filler.add("/remotes");
//			filler.add("/stash");
//			filler.add("/index");
//			filler.add("/workspace");
//			filler.add("/git") => symbolic link to the source dir

			return 0;
		} else if (path.equals("/commit")) {
			// list two-char subs for all commits
			try {
				Collection<String> items = jgitHelper.allCommitSubs();
				for(String item : items) {
					filler.add(item);
				}
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
				for(String item : items) {
					filler.add(item);
				}
			} catch (Exception e) {
				throw new IllegalStateException("Error reading elements of path " + path + ", found commit " + commit + " and directory " + dir, e);
			}

			return 0;
		} else if (path.equals("/tag")) {
			try {
				List<String> items = jgitHelper.getTags();
				for(String item : items) {
					filler.add(item);
				}
			} catch (Exception e) {
				throw new IllegalStateException("Error reading tags", e);
			}

			return 0;
		} else if (path.equals("/branch")) {
			try {
				List<String> items = jgitHelper.getBranches();
				for(String item : items) {
					filler.add(item);
				}
			} catch (Exception e) {
				throw new IllegalStateException("Error reading branches", e);
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
		         		StringBuilder target = new StringBuilder(".." + GitUtils.COMMIT_SLASH);
		        		try {
		        			final String commit;
		        			if(GitUtils.isBranchDir(path)) {
		        				commit = jgitHelper.getBranchHeadCommit(StringUtils.removeStart(path, GitUtils.BRANCH_SLASH));
		        			} else if (GitUtils.isTagDir(path)) {
		        				commit = jgitHelper.getTagHeadCommit(StringUtils.removeStart(path, GitUtils.TAG_SLASH));
		        			} else {
		        				throw new IllegalStateException("Had unknown path " + path + " in readlink()");
		        			}

		        			if(commit == null) {
		        				throw new IllegalStateException("Had unknown tag/branch " + path + " in readlink()");
		        			}
		        			target.append(commit.substring(0, 2)).append("/").append(commit.substring(2));

		        			return target.toString().getBytes();
		        		} catch (Exception e) {
		        			throw new IllegalStateException("Error reading commit of tag/branch-path " + path, e);
		        		}
		             }
		           });

	@Override
	public int readlink(String path, ByteBuffer buffer, long size) {
		// ensure that we evict caches sometimes, Google Guava does not make guarantees that
		// eviction happens automatically in a mostly read-only cache
		if(System.currentTimeMillis() > (lastLinkCacheCleanup + CACHE_TIMEOUT)) {
			System.out.println("Perform manual cache maintenance after " + ((System.currentTimeMillis() - lastLinkCacheCleanup)/1000) + " seconds");
			lastLinkCacheCleanup = System.currentTimeMillis();
			linkCache.cleanUp();
		}
		
		// use the cache to speed up access, symlinks are always queried even for sub-path access, so we get lots of requests for these!
		byte[] cachedCommit;
		try {
			cachedCommit = linkCache.get(path);
			if(cachedCommit != null) {
				buffer.put(cachedCommit);
				buffer.put((byte)0);

				return 0;
			}
		} catch (UncheckedExecutionException e) {
			throw new IllegalStateException("Error reading commit of tag/branch-path " + path, e);
		} catch (ExecutionException e) {
			throw new IllegalStateException("Error reading commit of tag/branch-path " + path, e);
		}

		throw new IllegalStateException("Error reading commit of tag/branch-path " + path);
	}

	/**
	 * Free up resources held for the Git repository and unmount the FUSE-filesystem.
	 *
	 * @throws IOException If an error ocurred while closing the Git repository or while unmounting the filesystem.
	 */
	@Override
	public void close() throws IOException {
		jgitHelper.close();

		try {
			unmount();
		} catch (FuseException e) {
			throw new IOException(e);
		}
	}
}
