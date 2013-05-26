package org.dstadler.jgitfs.node;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;
import net.fusejna.types.TypeMode.NodeType;

import org.eclipse.jgit.lib.Repository;



public abstract class HierarchicalNode implements Node {
	protected Map<String, Node> childHandlers = new HashMap<String, Node>();

	private Repository repository;
	
	@Override
	public void setRepository(Repository repository) {
		this.repository = repository;
	}

	public Repository getRepository() {
		return repository;
	}

	public void add(Node node) {
		if(node.getPrefix() == null) {
			throw new NullPointerException("Node.getPrefix() should not return null!");
		}
		childHandlers.put(node.getPrefix(), node);
		node.setRepository(repository);
	}

	@Override
	public int populateStats(StatWrapper stat) {
		stat.setMode(NodeType.DIRECTORY, true, false, true);
		return 0;
	}

	@Override
	public int read(ByteBuffer buffer, long size, long offset, FileInfoWrapper info) {
		throw new UnsupportedOperationException("Cannot read data of directory: " + info.toString());
	}

	@Override
	public int populateLink(ByteBuffer buffer, long size) {
		throw new UnsupportedOperationException("Cannot read symbolic link of directory");
	}
	
	@Override
	public Node getNodeForPath(String path) {
		// check if this node itself is meant
		if(path.equals(getPrefix())) {
			return this;
		}
		
		// otherwise ask the matching sub-node for details
		for(String key : childHandlers.keySet()) {
			// check if this handler is able to process this key
			if(path.startsWith(key)) {
				return childHandlers.get(key).getNodeForPath(path);
			}
		}
		
		//throw new UnsupportedOperationException();
		System.out.println("Could not read node for " + path);
		return new DummyNode(path);
	}
}
