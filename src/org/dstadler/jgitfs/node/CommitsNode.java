package org.dstadler.jgitfs.node;

import java.nio.ByteBuffer;

import net.fusejna.DirectoryFiller;
import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;
import net.fusejna.types.TypeMode.NodeType;

import org.eclipse.jgit.lib.Repository;


public class CommitsNode implements Node {

	@Override
	public void setRepository(Repository repository) {
	}

	@Override
	public int populateStats(StatWrapper stat) {
		stat.setMode(NodeType.DIRECTORY, true, false, true);
		return 0;
	}

	@Override
	public void populateDirectory(DirectoryFiller filler) {
		// TODO: implement
		filler.add("commit1");
		filler.add("commit2");
	}

	@Override
	public int read(ByteBuffer buffer, long size, long offset, FileInfoWrapper info) {
		throw new UnsupportedOperationException("Cannot read data of directory: " + info.toString());
	}

	@Override
	public int populateLink(ByteBuffer buffer, long size) {
		throw new UnsupportedOperationException("Can not read symbolic link for commits node");
	}

	@Override
	public String getPrefix() {
		return "/commits";
	}

	@Override
	public Node getNodeForPath(String path) {
		return new DummyNode(path);
	}
}
