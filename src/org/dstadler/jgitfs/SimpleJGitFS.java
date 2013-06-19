package org.dstadler.jgitfs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.fusejna.DirectoryFiller;
import net.fusejna.ErrorCodes;
import net.fusejna.FuseException;
import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;
import net.fusejna.types.TypeMode.NodeType;
import net.fusejna.util.FuseFilesystemAdapterFull;

import org.apache.commons.lang3.StringUtils;
import org.dstadler.jgitfs.util.FuseUtils;
import org.dstadler.jgitfs.util.GitUtils;
import org.dstadler.jgitfs.util.JGitHelper;
import org.eclipse.jgit.api.errors.GitAPIException;

public class SimpleJGitFS extends FuseFilesystemAdapterFull
{
	private final JGitHelper jgitHelper;
	
	public static void main(final String... args) throws FuseException, IOException, GitAPIException
	{
		if (args.length != 2) {
			System.err.println("Usage: GitFS <git-repo> <mountpoint>");
			System.exit(1);
		}
		
		//read GIT
		SimpleJGitFS gitFS = new SimpleJGitFS(args[0]);
		try {
			//gitFS.list();
			gitFS.log(false);

			File mountPoint = new File(args[1]);
			
			FuseUtils.prepareMountpoint(mountPoint);

			gitFS.mount(args[1]);
		} finally {
			gitFS.close();
		}
		
	}

	private void close() throws IOException, FuseException {
		if(jgitHelper != null) {
			jgitHelper.close();
		}
		
		unmount();
	}

	public SimpleJGitFS(String gitDir) throws IOException {
		super();
		
		jgitHelper = new JGitHelper(gitDir);
	}

	private static Set<String> DIRS = new HashSet<String>();
	static {
		DIRS.add("/");
		DIRS.add("/commit");
		DIRS.add("/branch");
		DIRS.add("/tag");
		
		// directories looked for by gnome/Linux/...
//		DIRS.add("/.Trash");
//		DIRS.add("/.Trash-1000");
	}
	
	@Override
	public int getattr(final String path, final StatWrapper stat)
	{
		// known entries and directories beneath /commit are always directories 
		if(DIRS.contains(path) || GitUtils.isCommitTupel(path) || GitUtils.isCommitDir(path)) {
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
			filler.add("/commit");
			filler.add("/branch");
			filler.add("/tag");
			
			// TODO: implement later
//			filler.add("/index");
//			filler.add("/workspace");

			return 0;
		} else if (path.equals("/commit")) {
			// list two-char tupels for all commits 
			try {
				List<String> items = jgitHelper.allCommitTupels();
				for(String item : items) {
					filler.add(item);
				}
			} catch (Exception e) {
				throw new IllegalStateException("Error reading elements of path " + path, e);
			}
			
			return 0;
		} else if (GitUtils.isCommitTupel(path)) {
			// list all commits for the requested tupel
			String tupel = StringUtils.removeStart(path, GitUtils.COMMIT_SLASH);
			try {
				List<String> items = jgitHelper.allCommits(tupel);
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
					// TODO: handle branches with slash, e.g. refs/heads/master
					filler.add(jgitHelper.adjustName(item));
				}
			} catch (Exception e) {
				throw new IllegalStateException("Error reading tags", e);
			}
			
			return 0;
		} else if (path.equals("/branch")) {
			try {
				List<String> items = jgitHelper.getBranches();
				for(String item : items) {
					// TODO: handle tags with slash as subdirs instead of replacing with underscore
					filler.add(jgitHelper.adjustName(item));
				}
			} catch (Exception e) {
				throw new IllegalStateException("Error reading branches", e);
			}
			
			return 0;
		}
		
		throw new IllegalStateException("Had unknown path " + path + " in readdir()");
	}

	@Override
	public int readlink(String path, ByteBuffer buffer, long size) {
		if(GitUtils.isBranchDir(path)) {
			StringBuilder target = new StringBuilder(".." + GitUtils.COMMIT_SLASH);
			
			try {
				String commit = jgitHelper.getBranchHeadCommit(StringUtils.removeStart(path, GitUtils.BRANCH_SLASH));
				if(commit == null) {
					throw new IllegalStateException("Had unknown branch " + path + " in readlink()");
				}
				target.append(commit.substring(0, 2)).append("/").append(commit.substring(2));
				
				buffer.put(target.toString().getBytes());
				buffer.put((byte)0);
			} catch (Exception e) {
				throw new IllegalStateException("Error reading commit of branch-path " + path, e);
			}
			
			return 0;
		} else if (GitUtils.isTagDir(path)) {
			StringBuilder target = new StringBuilder(".." + GitUtils.COMMIT_SLASH);
			
			try {
				String commit = jgitHelper.getTagHeadCommit(StringUtils.removeStart(path, GitUtils.TAG_SLASH));
				if(commit == null) {
					throw new IllegalStateException("Had unknown tag " + path + " in readlink()");
				}

				target.append(commit.substring(0, 2)).append("/").append(commit.substring(2));
				
				buffer.put(target.toString().getBytes());
				buffer.put((byte)0);
			} catch (Exception e) {
				throw new IllegalStateException("Error reading commit of branch-path " + path, e);
			}
			
			return 0;
		}

		throw new IllegalStateException("Had unknown path " + path + " in readlink()");
	}
}
