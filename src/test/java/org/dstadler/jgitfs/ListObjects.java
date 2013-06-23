package org.dstadler.jgitfs;

import java.io.File;
import java.io.IOException;
import java.util.List;

import net.fusejna.FuseException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;


public class ListObjects {
	public static void main(final String... args) throws FuseException, IOException, GitAPIException
	{
		Repository repository;
		
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		repository = builder.setGitDir(new File("/opt/poi/.git"))
		  .readEnvironment() // scan environment GIT_* variables
		  .findGitDir() // scan up the file system tree
		  .build();
		
		list(repository);
	}

	private static void list(Repository repository) throws GitAPIException, IOException {
		Git git = new Git(repository);
		
		List<Ref> branches = git.branchList().call();
		System.out.println("Listing " + branches.size() + " branches");
		for(Ref ref : branches) {
			System.out.println("Branch: " + ref.getName() + ", " + ref.toString() + ", " + ref.getLeaf().getName() + ", " + ref.getObjectId() + ", " + ref.getTarget().getName());
		}
		
		List<Ref> tags = git.tagList().call();
		System.out.println("Listing " + tags.size() + " tags");
		for(Ref ref : tags) {
			System.out.println("Tag: " + ref.getName());
		}
		
		String head = repository.getFullBranch();
		if(head == null) {
			System.out.println("Null head!");
		} else {
			if (head.startsWith("refs/heads/")) {
			        // Print branch name with "refs/heads/" stripped.
			        System.out.println("Current branch is " + repository.getBranch());
			}
		}
		
		// The following could also have been done using repo.resolve("HEAD")
//        ObjectId id = repository.resolve(repository.getFullBranch());
//        System.out.println("Branch " + repository.getBranch() + " points to " + id.name());
		
		// The following could also have been done using repo.resolve("HEAD")
        ObjectId id = repository.resolve("HEAD");
		if(id == null) {
			System.out.println("Null head!");
		} else {
			System.out.println("Branch " + repository.getBranch() + " points to " + id.name());
		}


	}
}
