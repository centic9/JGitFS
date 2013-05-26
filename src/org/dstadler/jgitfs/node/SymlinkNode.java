package org.dstadler.jgitfs.node;

import java.nio.ByteBuffer;

import net.fusejna.DirectoryFiller;
import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;
import net.fusejna.types.TypeMode.NodeType;

import org.eclipse.jgit.lib.Repository;


public class SymlinkNode implements Node {
	private final String path;
	private final String target;
	
	public SymlinkNode(String path, String target) {
		super();
		this.path = path;
		this.target = target;
	}

	@Override
	public void setRepository(Repository repository) {
	}

	@Override
	public int populateStats(StatWrapper stat) {
		stat.setMode(NodeType.SYMBOLIC_LINK, true, false, false);
		return 0;
	}

	@Override
	public void populateDirectory(DirectoryFiller filler) {
		throw new UnsupportedOperationException("Can not populate directory for symbolic link" + filler.toString());

	}

	@Override
	public int read(ByteBuffer buffer, long size, long offset, FileInfoWrapper info) {
		throw new UnsupportedOperationException("Can not read data of symbolic link" + info.toString());
	}

	@Override
	public int populateLink(ByteBuffer buffer, long size) {
		buffer.put(target.getBytes());
		return 0;
	}

	@Override
	public String getPrefix() {
		return path;
	}

	@Override
	public Node getNodeForPath(String path) {
		return new DummyNode(path);
	}
}
