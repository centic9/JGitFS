package org.dstadler.jgitfs.node;

import org.eclipse.jgit.lib.Repository;


public class NodeFactory {
	private final Repository repository;
	
	public NodeFactory(Repository repository) {
		this.repository = repository;
	}
	
	public Node createNode(String path) {

		// otherwise populate the main directory
		if(path.equals("/")) {
			return new RootNode();
		}
		
		throw new UnsupportedOperationException();
	}


	/*
dr-x------ 8 dstadler dstadler 4096 Mai 26 13:40 commits
lr-------- 1 dstadler dstadler 4096 Mai 26 13:40 HEAD -> refs/HEAD
lr-------- 1 dstadler dstadler 4096 Mai 26 13:40 heads -> refs/refs/heads
dr-x------ 8 dstadler dstadler 4096 Mai 26 13:40 refs
lr-------- 1 dstadler dstadler 4096 Mai 26 13:40 remotes -> refs/refs/remotes
lr-------- 1 dstadler dstadler 4096 Mai 26 13:40 tags -> refs/refs/tags
dr-x------ 8 dstadler dstadler 4096 Mai 26 13:40 trees

stadler@dstathink:/opt/git-fs/fuse-jna$ ls -al .git/fs/*
lr-------- 1 dstadler dstadler 4096 Mai 26 13:40 .git/fs/HEAD -> refs/HEAD
lr-------- 1 dstadler dstadler 4096 Mai 26 13:40 .git/fs/heads -> refs/refs/heads
lr-------- 1 dstadler dstadler 4096 Mai 26 13:40 .git/fs/remotes -> refs/refs/remotes
lr-------- 1 dstadler dstadler 4096 Mai 26 13:40 .git/fs/tags -> refs/refs/tags

.git/fs/commits:
insgesamt 8
dr-x------ 8 dstadler dstadler 4096 Mai 26 13:40 .
dr-x------ 8 dstadler dstadler 4096 Mai 26 13:40 ..

.git/fs/refs:
insgesamt 16
dr-x------ 8 dstadler dstadler 4096 Mai 26 13:40 .
dr-x------ 8 dstadler dstadler 4096 Mai 26 13:40 ..
dr-x------ 8 dstadler dstadler 4096 Mai 26 13:40 HEAD
dr-x------ 8 dstadler dstadler 4096 Mai 26 13:40 refs

.git/fs/trees:
insgesamt 8
dr-x------ 8 dstadler dstadler 4096 Mai 26 13:40 .
dr-x------ 8 dstadler dstadler 4096 Mai 26 13:40 ..
	 */		
}
