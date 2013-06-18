package org.dstadler.jgitfs.util;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import net.fusejna.types.TypeMode.NodeType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;


public class JGitHelperTest {
	private JGitHelper helper;

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
		String commit = helper.allCommits(null).get(0);
		
		System.out.println("Had commit: " + commit);
		assertEquals(NodeType.DIRECTORY, helper.readType(commit, "src"));
		assertEquals(NodeType.DIRECTORY, helper.readType(commit, "src/org"));
		
		assertEquals(NodeType.FILE, helper.readType(commit, "run.sh"));
		assertEquals(NodeType.FILE, helper.readType(commit, "src/org/dstadler/jgitfs/JGitFS.java"));
	}

	@Test
	public void testOpenFile() throws Exception {
		String commit = helper.allCommits(null).get(0);
		
		System.out.println("Had commit: " + commit);
		String runSh = IOUtils.toString(helper.openFile(commit, "run.sh"));
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
		String commit = helper.allCommits(null).get(0);
		
		System.out.println("Had commit: " + commit);
		assertEquals("[org]", helper.readElementsAt(commit, "src").toString());
		assertEquals("[dstadler]", helper.readElementsAt(commit, "src/org").toString());

		try {
			helper.readElementsAt(commit, "run.sh");
			fail("Should catch exception here");
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage().contains("run.sh"));
		}

		String list = helper.readElementsAt(commit, "").toString();
		assertTrue("Had: " + list, list.contains("src"));
		assertTrue("Had: " + list, list.contains("testsrc"));
		assertTrue("Had: " + list, list.contains(".classpath"));
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
		
		List<String> allCommits = helper.allCommits(null);
		assertTrue(allCommits.size() > 0);
		
		List<String> commits = helper.allCommits(allCommits.get(0).substring(0, 2));
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
