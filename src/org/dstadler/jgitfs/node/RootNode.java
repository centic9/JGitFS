package org.dstadler.jgitfs.node;

import net.fusejna.DirectoryFiller;


public class RootNode extends HierarchicalNode {
	@Override
	public void populateDirectory(DirectoryFiller filler) {
		filler.add("HEAD");
		add(new SymlinkNode("/HEAD", "/refs/HEAD"));

		filler.add("commits");
		add(new CommitsNode());

		//filler.add("branches");
		//add(new BranchesNode());

		filler.add("refs");
		add(new RefsNode());

//		filler.add("heads");
//		filler.add("refs");
//		filler.add("remotes");
//		filler.add("tags");
//		filler.add("trees");
	}

	@Override
	public String getPrefix() {
		return "/";
	}
}
