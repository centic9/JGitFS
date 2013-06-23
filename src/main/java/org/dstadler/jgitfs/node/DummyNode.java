package org.dstadler.jgitfs.node;

import java.nio.ByteBuffer;

import net.fusejna.DirectoryFiller;
import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;

import org.eclipse.jgit.lib.Repository;


public class DummyNode implements Node {
	private final String path;
	
	public DummyNode(String path) {
		super();
		this.path = path;
	}

	@Override
	public void setRepository(Repository repository) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int populateStats(StatWrapper stat) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void populateDirectory(DirectoryFiller filler) {
		// TODO Auto-generated method stub
		filler.add("Dummy");

	}

	@Override
	public int read(ByteBuffer buffer, long size, long offset, FileInfoWrapper info) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int populateLink(ByteBuffer buffer, long size) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getPrefix() {
		// TODO Auto-generated method stub
		return path;
	}

	@Override
	public Node getNodeForPath(String path) {
		// TODO Auto-generated method stub
		return new DummyNode(path);
	}

}
