package org.dstadler.jgitfs.node;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import net.fusejna.DirectoryFiller;
import net.fusejna.FuseException;
import net.fusejna.FuseFilesystem;
import net.fusejna.util.FuseFilesystemAdapterAssumeImplemented;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;


public class BranchesNodeTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();
	
	private Repository repository;
	
	@Before
	public void setUp() throws IOException {
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		repository = builder.setGitDir(folder.newFolder())
		  .readEnvironment() // scan environment GIT_* variables
		  .findGitDir() // scan up the file system tree
		  .build();
	}
	
	@Test
	public void test() throws FuseException, IOException {
		FuseFilesystem filesystem = new FuseFilesystemAdapterAssumeImplemented() {

			@Override
			public int readdir(String path, DirectoryFiller filler) {
				BranchesNode node = new BranchesNode();
				node.setRepository(repository);
				assertEquals("/branches", node.getPrefix());
				node.populateDirectory(filler);
				
				return 0;
			}
			
		};
//		StructFuseOperations operations = new StructFuseOperations(filesystem);
//		Callback callback = operations.readdir;
//		assertNotNull(callback);
		filesystem.mount(folder.newFolder(), false);
		try {
		} finally {
			filesystem.unmount();
		}
	}
}
