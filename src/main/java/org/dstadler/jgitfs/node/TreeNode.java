package org.dstadler.jgitfs.node;

import java.io.IOException;

import net.fusejna.DirectoryFiller;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;


public class TreeNode extends HierarchicalNode {

	private String path;
	private Ref ref;

	public TreeNode(String path, Ref ref) {
		this.path = path;
		this.ref = ref;
	}

	@Override
	public void populateDirectory(DirectoryFiller filler) {
		//RevWalk walk = new RevWalk(getRepository());
		try {
//			RevCommit commit = walk.parseCommit(ref.getPeeledObjectId());
//			RevTree tree = commit.getTree();
			
			//RevTree tree = walk.parseTree(ref.getObjectId());
			//add(new DummyNode(tree.getName()));
			
			//getRepository().get
			RevWalk revWalk = new RevWalk(getRepository());
			RevCommit commit = revWalk.parseCommit(ref.getObjectId());
			
			RevTree tree = commit.getTree();
			
			TreeWalk walk = new TreeWalk(getRepository());
			walk.addTree(tree.getId());
			while(walk.next()) {
				//MutableObjectId id = new MutableObjectId();
				
				// TODO: inefficient
				filler.add(walk.getNameString());
				add(new TreeNode(path + "/" + walk.getNameString(), getRepository().getRef(walk.getObjectId(0).getName())));
			}
		} catch (MissingObjectException e) {
			throw new IllegalStateException(e);
		} catch (IncorrectObjectTypeException e) {
			throw new IllegalStateException(e);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}


		//
//		
//		Git git = new Git(getRepository());
//		
//		ref.getPeeledObjectId()
//		
//		getRepository().get
//		
//		
//			git.reflog().call()
//			List<Ref> branches = git.branchList().call();
//			//System.out.println("Listing " + branches.size() + " branches");
//			for(Ref ref : branches) {
//				//System.out.println("Branch: " + ref.getName());
//				filler.add(ref.getName());
//				add(new TreeNode("/branches/" + ref.getName(), ref));
//			}
	}

	@Override
	public String getPrefix() {
		return path;
	}
}
