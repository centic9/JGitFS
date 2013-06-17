package org.dstadler.jgitfs;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import net.fusejna.DirectoryFiller;
import net.fusejna.FuseException;
import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;
import net.fusejna.util.FuseFilesystemAdapterFull;

import org.dstadler.jgitfs.node.Node;
import org.dstadler.jgitfs.node.RootNode;
import org.dstadler.jgitfs.util.FuseUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class JGitFS extends FuseFilesystemAdapterFull
{
	private final Repository repository;
	//private final NodeFactory factory;
	private final Node root;
	
	public static void main(final String... args) throws FuseException, IOException, GitAPIException
	{
		if (args.length != 2) {
			System.err.println("Usage: GitFS <git-repo> <mountpoint>");
			System.exit(1);
		}
		
		// jna update: 
		// * https://groups.google.com/forum/?fromgroups#!msg/jna-users/ju0CyEmiU_4/41litv0Tux4J
		// * https://github.com/twall/jna/blob/master/CHANGES.md
		
		//read GIT
		JGitFS gitFS = new JGitFS(args[0]);
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
		if(repository != null) {
			repository.close();
		}
		
		unmount();
	}

	public JGitFS(String gitDir) throws IOException {
		super();

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
		
		//factory = new NodeFactory(repository);
		root = new RootNode();
		root.setRepository(repository);
	}

	@Override
	public int getattr(final String path, final StatWrapper stat)
	{
		return getNodeForPath(path).populateStats(stat);
		
		// let the related node handle this
//		Node node = topLevelHandlers.get(path);
//		if(node != null) {
//			return node.populateStats(stat);
//		}
//		
//		
//		if (path.equals(File.separator)) { // Root directory
//			stat.setMode(NodeType.DIRECTORY);
//			return 0;
//		}
//		if (path.equals(filename)) { // hello.txt
//			stat.setMode(NodeType.FILE).size(contents.length());
//			return 0;
//		}
//		return -ErrorCodes.ENOENT;
	}

	@Override
	public int read(final String path, final ByteBuffer buffer, final long size, final long offset, final FileInfoWrapper info) {
		return getNodeForPath(path).read(buffer, size, offset, info);
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
		getNodeForPath(path).populateDirectory(filler);
		
		return 0;
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
		return getNodeForPath(path).populateLink(buffer, size);
//		Node node = topLevelHandlers.get(path);
//		if(node != null) {
//			node.populateLink(buffer, size);
//		}
//
//		return super.readlink(path, buffer, size);
	}
	
	private Node getNodeForPath(String path) {
		return root.getNodeForPath(path);
	}
}