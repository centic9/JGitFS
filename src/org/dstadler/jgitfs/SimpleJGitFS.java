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
		DIRS.add("/.Trash");
		DIRS.add("/.Trash-1000");
	}
	
	@Override
	public int getattr(final String path, final StatWrapper stat)
	{
		if(DIRS.contains(path) || GitUtils.isCommitTupel(path) || GitUtils.isCommitDir(path)) {
			stat.setMode(NodeType.DIRECTORY);
			return 0;
		} else if (GitUtils.isBranchDir(path) || GitUtils.isTagDir(path)) {
			stat.setMode(NodeType.SYMBOLIC_LINK);
			return 0;
		} else if (GitUtils.isCommitSubDir(path)) {
			String commit = jgitHelper.readCommit(path);
			String file = jgitHelper.readPath(path);
			
			try {
				stat.setMode(jgitHelper.readType(commit, file));
			} catch (Exception e) {
				throw new IllegalStateException("Error reading type of path " + path + ", found commit " + commit + " and file " + file, e);
			}
			return 0;
		}

		//throw new IllegalStateException("Had unknown path " + path + " in getattr()");
		System.out.println("Had unknown path " + path + " in getattr()");
		
		// all others are reported as "not found"
		return -ErrorCodes.ENOENT;
	}

	@Override
	public int read(final String path, final ByteBuffer buffer, final long size, final long offset, final FileInfoWrapper info) {
		String commit = jgitHelper.readCommit(path);
		String file = jgitHelper.readPath(path);
		
		// TODO: do we need to tune this?!?
		try {
			InputStream openFile = jgitHelper.openFile(commit, file);
			
			try {
				// skip until we are at the offset
				for(int i = 0;i < offset;i++) {
					openFile.read();
				}
				
				// then return the requested number of bytes
				for(int i = 0;i < size;i++) {
					// TODO: handle end of file?
					buffer.put((byte)openFile.read());
				}
			} finally {
				openFile.close();
			}
		} catch (Exception e) {
			throw new IllegalStateException("Error reading contents of path " + path + ", found commit " + commit + " and file " + file, e);
		}
		
		throw new IllegalStateException("Had unknown path " + path + " in read()");

		//return getNodeForPath(path).read(buffer, size, offset, info);
		// let the related node handle this
//		Node node = topLevelHandlers.get(path);
//		if(node != null) {
//			return node.read(buffer, size, offset, info);
//		}
//		
//		// Compute substring that we are being asked to read
//		final String s = contents.substring((int) offset,
//				(int) Math.max(offset, Math.min(contents.length() - offset, offset + size)));
//		buffer.put(s.getBytes());
//		return s.getBytes().length;
	}

	@Override
	public int readdir(final String path, final DirectoryFiller filler) {
		// TODO: implement populating dirs
		
		if(path.equals("/")) {
			filler.add("/commit");
			filler.add("/branch");
			filler.add("/tag");
			
			// TODO: implement later
//			filler.add("/index");
//			filler.add("/workspace");

			return 0;
		} else if (path.equals("/commit")) {
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
			// get all commit-tupels to populate the first level
			String tupel = StringUtils.removeStart(path, "/commit/");
			try {
				List<String> items = jgitHelper.allCommits();
				for(String item : items) {
					if(item.startsWith(tupel)) {
						filler.add(item.substring(2));
					}
				}
			} catch (Exception e) {
				throw new IllegalStateException("Error reading elements of path " + path, e);
			}
			
			return 0;
		} else if (GitUtils.isCommitDir(path) || GitUtils.isCommitSubDir(path)) {
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
					// TODO: handle tags with slash
					filler.add(item);
				}
			} catch (Exception e) {
				throw new IllegalStateException("Error reading branches", e);
			}
			
			return 0;
		}
		
		throw new IllegalStateException("Had unknown path " + path + " in readdir()");
		
//		getNodeForPath(path).populateDirectory(filler);
//		
//		return 0;
//		Node node = topLevelHandlers.get(path);
//		if(node != null) {
//			node.populateDirectory(filler);
//		}
//
////		filler.add(filename);
//		return 0;
	}

	@Override
	public int readlink(String path, ByteBuffer buffer, long size) {
		// TODO: implement symbolic link handling
		
		if(GitUtils.isBranchDir(path)) {
			StringBuilder target = new StringBuilder("/commit/");
			
			try {
				String commit = jgitHelper.getBranchHeadCommit(path.substring("/branch/".length()));
				if(commit == null) {
					throw new IllegalStateException("Had unknown branch " + path + " in readlink()");
				}
				target.append(commit.substring(0, 2)).append("/").append(commit.substring(2));
				
				buffer.put(target.toString().getBytes());
			} catch (Exception e) {
				throw new IllegalStateException("Error reading commit of branch-path " + path, e);
			}
			
			return 0;
		} else if (GitUtils.isTagDir(path)) {
			StringBuilder target = new StringBuilder("/commit/");
			
			try {
				String commit = jgitHelper.getTagHeadCommit(path.substring("/tag/".length()));
				if(commit == null) {
					throw new IllegalStateException("Had unknown tag " + path + " in readlink()");
				}

				target.append(commit.substring(0, 2)).append("/").append(commit.substring(2));
				
				buffer.put(target.toString().getBytes());
			} catch (Exception e) {
				throw new IllegalStateException("Error reading commit of branch-path " + path, e);
			}
			
			return 0;
		}

		throw new IllegalStateException("Had unknown path " + path + " in readlink()");
		
//		return getNodeForPath(path).populateLink(buffer, size);
//		Node node = topLevelHandlers.get(path);
//		if(node != null) {
//			node.populateLink(buffer, size);
//		}
//
//		return super.readlink(path, buffer, size);
	}
}
