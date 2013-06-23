package org.dstadler.jgitfs.node;

import java.nio.ByteBuffer;

import net.fusejna.DirectoryFiller;
import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;
import net.fusejna.types.TypeMode.NodeType;

import org.eclipse.jgit.lib.Repository;


public class BlobNode implements Node {
	private final String path;
	
	public BlobNode(String path) {
		super();
		this.path = path;
	}

	@Override
	public void setRepository(Repository repository) {
	}

	@Override
	public int populateStats(StatWrapper stat) {
		stat.setMode(NodeType.FILE, true, false, true);
		return 0;
	}

	@Override
	public void populateDirectory(DirectoryFiller filler) {
		throw new UnsupportedOperationException("Cannot populate directory for blobs");
	}

	@Override
	public int read(ByteBuffer buffer, long size, long offset, FileInfoWrapper info) {
		
		return 0;
	}

	@Override
	public int populateLink(ByteBuffer buffer, long size) {
		throw new UnsupportedOperationException("Cannot populate link for blobs");
	}

	@Override
	public String getPrefix() {
		return path;
	}

	@Override
	public Node getNodeForPath(String path) {
		throw new UnsupportedOperationException("Cannot fetch sub-links for blobs");
	}
}
