package org.dstadler.jgitfs.node;

import java.nio.ByteBuffer;

import net.fusejna.DirectoryFiller;
import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;

import org.eclipse.jgit.lib.Repository;

public interface Node {
	void setRepository(Repository repository);

	int populateStats(StatWrapper stat);
	
	void populateDirectory(DirectoryFiller filler);
	
	int read(final ByteBuffer buffer, final long size, final long offset, final FileInfoWrapper info);
	
	int populateLink(ByteBuffer buffer, long size);
	
	String getPrefix();

	Node getNodeForPath(String path);
}