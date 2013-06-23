package org.dstadler.jgitfs.node;

import net.fusejna.DirectoryFiller;


public class RefsNode extends HierarchicalNode {

	@Override
	public void populateDirectory(DirectoryFiller filler) {
		filler.add("heads");
		add(new HeadsNode());
	}

	@Override
	public String getPrefix() {
		return "/refs";
	}
}
