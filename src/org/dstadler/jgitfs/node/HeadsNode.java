package org.dstadler.jgitfs.node;

import java.util.List;

import net.fusejna.DirectoryFiller;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;


public class HeadsNode extends HierarchicalNode {

	@Override
	public void populateDirectory(DirectoryFiller filler) {
		Git git = new Git(getRepository());
		
		try {
			List<Ref> branches = git.branchList().call();
			//System.out.println("Listing " + branches.size() + " branches");
			for(Ref ref : branches) {
				//System.out.println("Branch: " + ref.getName());
				filler.add(ref.getName());
				add(new TreeNode("/" + ref.getName(), ref));
				//add(new SymlinkNode(ref.getName(), ref.getName()));
			}
		} catch (GitAPIException e) {
			throw new IllegalStateException(e);
		}

	}

	@Override
	public String getPrefix() {
		return "/refs/heads";
	}

}
