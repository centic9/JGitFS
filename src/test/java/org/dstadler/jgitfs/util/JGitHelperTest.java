package org.dstadler.jgitfs.util;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import net.fusejna.StatWrapperFactory;
import net.fusejna.StructStat.StatWrapper;
import net.fusejna.types.TypeMode;
import net.fusejna.types.TypeMode.NodeType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;


public class JGitHelperTest {
	private JGitHelper helper;

	@BeforeClass
	public static void setUpClass() throws GitAPIException, IOException {
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		Repository repository = builder.setGitDir(new File(".git"))
		  .readEnvironment() // scan environment GIT_* variables
		  .findGitDir() // scan up the file system tree
		  .build();
		Git git = new Git(repository);
		
		List<Ref> tags = git.tagList().call();
		boolean found = false;
		for(Ref ref : tags) {
			if(ref.getName().equals("refs/tags/testtag")) {
				found = true;
				break;
			}
		}
		if(!found) {
			git.tag().setName("testtag").call();
		}
	}
	
	@Before
	public void setUp() throws IOException {
		helper = new JGitHelper(".");
	}
	
	@After
	public void tearDown() throws IOException {
		helper.close();
	}
	
	@Test
	public void test() throws Exception {
		assertNotNull(helper);
		assertTrue(helper.allCommits(null).size() > 0);
	}
	
	@Test
	public void testWithGitdir() throws Exception {
		JGitHelper lhelper = new JGitHelper("./.git");
		assertNotNull(lhelper);
		assertTrue(lhelper.allCommits(null).size() > 0);
		lhelper.close();
	}

	@Test
	public void testReadCommit() throws Exception {
		assertEquals("abcd", helper.readCommit("abcd"));
		assertEquals("abcd", helper.readCommit("/commit/ab/cd"));
		assertEquals("1234567890123456789012345678901234567890", helper.readCommit("/commit/12/345678901234567890123456789012345678901234567890"));
	}

	@Test
	public void testReadPath() throws Exception {
		assertEquals("", helper.readPath("abcd"));
		assertEquals("", helper.readPath("/commit/ab/cd"));
		assertEquals("blabla", helper.readPath("/commit/12/34567890123456789012345678901234567890/blabla"));
	}

	@Test
	public void testReadType() throws Exception {
		StatWrapper wrapper = StatWrapperFactory.create();
		String commit = "ede9797616a805d6cbeca376bfbbac9a8b7eb64f";
		
		System.out.println("Had commit: " + commit);
		helper.readType(commit, "src", wrapper);
		assertEquals(NodeType.DIRECTORY, wrapper.type());
		
		helper.readType(commit, "src/main/java/org", wrapper);
		assertEquals(NodeType.DIRECTORY, wrapper.type());
		
		helper.readType(commit, "README.md", wrapper);
		assertEquals(NodeType.FILE, wrapper.type());
		assertTrue((wrapper.mode() & TypeMode.S_IXUSR) == 0);
		assertTrue((wrapper.mode() & TypeMode.S_IXGRP) == 0);
		assertTrue((wrapper.mode() & TypeMode.S_IXOTH) == 0);
		
		helper.readType(commit, "src/main/java/org/dstadler/jgitfs/JGitFS.java", wrapper);
		assertEquals(NodeType.FILE, wrapper.type());
		assertTrue((wrapper.mode() & TypeMode.S_IXUSR) == 0);
		assertTrue((wrapper.mode() & TypeMode.S_IXGRP) == 0);
		assertTrue((wrapper.mode() & TypeMode.S_IXOTH) == 0);
	}
	
	@Test
	public void testReadTypeExecutable() throws Exception {
		// Look at a specific older commit to have an executable file		
		StatWrapper wrapper = StatWrapperFactory.create();
		helper.readType("355ea52f1e38b1c8e6537c093332180918808b68", "run.sh", wrapper);
		assertEquals(NodeType.FILE, wrapper.type());
		assertTrue((wrapper.mode() & TypeMode.S_IXUSR) != 0);
		assertTrue((wrapper.mode() & TypeMode.S_IXGRP) != 0);
		assertTrue((wrapper.mode() & TypeMode.S_IXOTH) == 0);
	}

	@Test
	public void testOpenFile() throws Exception {
		String commit = "ede9797616a805d6cbeca376bfbbac9a8b7eb64f";
		System.out.println("Had commit: " + commit);
		String runSh = IOUtils.toString(helper.openFile(commit, "README.md"));
		assertTrue("Had: " + runSh, StringUtils.isNotEmpty(runSh));

		try {
			IOUtils.toString(helper.openFile(commit, "src"));
			fail("Should catch exception here");
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage().contains("src"));
		}
		try {
			IOUtils.toString(helper.openFile(commit, "src/org"));
			fail("Should catch exception here");
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage().contains("src/org"));
		}
	}

	@Test
	public void testReadElementsAt() throws Exception {
		String commit = "ede9797616a805d6cbeca376bfbbac9a8b7eb64f";
		
		System.out.println("Had commit: " + commit);
		assertEquals("[main, test]", helper.readElementsAt(commit, "src").toString());
		assertEquals("[dstadler]", helper.readElementsAt(commit, "src/main/java/org").toString());

		try {
			helper.readElementsAt(commit, "run.sh");
			fail("Should catch exception here");
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage().contains("run.sh"));
		}

		String list = helper.readElementsAt(commit, "").toString();
		assertTrue("Had: " + list, list.contains("src"));
		assertTrue("Had: " + list, list.contains("README.md"));
		assertTrue("Had: " + list, list.contains("build.gradle"));
	}
	
	@Test
	public void testGetBranchHeadCommit() throws GitAPIException {
		assertNull(helper.getBranchHeadCommit("somebranch"));
		assertNotNull(helper.getBranchHeadCommit("master"));
		assertNotNull(helper.getBranchHeadCommit("refs_heads_master"));
	}
	
	@Test
	public void testGetBranches() throws GitAPIException {
		List<String> branches = helper.getBranches();
		assertTrue(branches.size() > 0);
		assertTrue("Had: " + branches.toString(), branches.contains("master"));
		assertTrue("Had: " + branches.toString(), branches.contains("refs_heads_master"));
	}
	
	@Test
	public void testGetTagHead() throws GitAPIException {
		assertNull(helper.getTagHeadCommit("sometag"));
		assertNotNull(helper.getTagHeadCommit("testtag"));
		assertNotNull(helper.getTagHeadCommit("refs_tags_testtag"));
	}

	@Test
	public void testGetTags() throws GitAPIException {
		List<String> tags = helper.getTags();
		assertTrue(tags.size() > 0);
		assertTrue("Had: " + tags.toString(), tags.contains("testtag"));
		assertTrue("Had: " + tags.toString(), tags.contains("refs_tags_testtag"));
	}
	
	@Test
	public void testallCommitsNull() throws NoHeadException, GitAPIException, IOException {
		int size = helper.allCommits(null).size();
		assertTrue("Had size: " + size, size > 3);
	}

	@Test
	public void testallCommits() throws NoHeadException, GitAPIException, IOException {
		int size = helper.allCommits("zz").size();
		assertEquals("Had size: " + size, 0, size);
		
		Collection<String> allCommits = helper.allCommits(null);
		assertTrue(allCommits.size() > 0);
		
		Collection<String> commits = helper.allCommits(allCommits.iterator().next().substring(0, 2));
		assertTrue(commits.size() > 0);
	}

	@Test
	public void testAllCommitTupels() throws NoHeadException, GitAPIException, IOException {
		List<String> tupels = helper.allCommitTupels();
		int tupelSize = tupels.size();
		assertTrue("Had: " + tupels, tupelSize > 3);
		
		for(String tup : tupels) {
			assertEquals("Had: " + tup, 2, tup.length());
			assertTrue("Had: " + tup, tup.matches("[a-f0-9]{2}"));
		}
	}

	@Ignore("Local test")
	@Test
	public void testAllCommitsPOI() throws NoHeadException, GitAPIException, IOException {
		helper.close();
		helper = new JGitHelper("/opt/poi");
		
		int size = helper.allCommits(null).size();
		assertTrue("Had size: " + size, size > 3);
	}
}
