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
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;


public class JGitHelperTest {
	private final static String DEFAULT_COMMIT = "ede9797616a805d6cbeca376bfbbac9a8b7eb64f";

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
	public void testNotexistingDir() throws Exception {
		try {
			JGitHelper jGitHelper = new JGitHelper("notexisting");
			assertNotNull(jGitHelper);
			fail("Should catch exception here");
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage().contains("notexisting"));
		}
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
		final StatWrapper wrapper = getStatsWrapper();
		
		System.out.println("Had commit: " + DEFAULT_COMMIT);
		helper.readType(DEFAULT_COMMIT, "src", wrapper);
		assertEquals(NodeType.DIRECTORY, wrapper.type());
		
		helper.readType(DEFAULT_COMMIT, "src/main/java/org", wrapper);
		assertEquals(NodeType.DIRECTORY, wrapper.type());
		
		helper.readType(DEFAULT_COMMIT, "README.md", wrapper);
		assertEquals(NodeType.FILE, wrapper.type());
		assertTrue((wrapper.mode() & TypeMode.S_IXUSR) == 0);
		assertTrue((wrapper.mode() & TypeMode.S_IXGRP) == 0);
		assertTrue((wrapper.mode() & TypeMode.S_IXOTH) == 0);
		
		helper.readType(DEFAULT_COMMIT, "src/main/java/org/dstadler/jgitfs/JGitFS.java", wrapper);
		assertEquals(NodeType.FILE, wrapper.type());
		assertTrue((wrapper.mode() & TypeMode.S_IXUSR) == 0);
		assertTrue((wrapper.mode() & TypeMode.S_IXGRP) == 0);
		assertTrue((wrapper.mode() & TypeMode.S_IXOTH) == 0);
	}
	
	@Test
	public void testReadTypeExecutable() throws Exception {
		final StatWrapper wrapper = getStatsWrapper();
		// Look at a specific older commit to have an executable file		
		helper.readType("355ea52f1e38b1c8e6537c093332180918808b68", "run.sh", wrapper);
		assertEquals(NodeType.FILE, wrapper.type());
		assertTrue((wrapper.mode() & TypeMode.S_IXUSR) != 0);
		assertTrue((wrapper.mode() & TypeMode.S_IXGRP) != 0);
		assertTrue((wrapper.mode() & TypeMode.S_IXOTH) == 0);
	}

	private StatWrapper getStatsWrapper() {
		final StatWrapper wrapper;
		try {
			wrapper = StatWrapperFactory.create();
		} catch (UnsatisfiedLinkError e) {
			System.out.println("This might fail on machines without fuse-binaries.");
			e.printStackTrace();
			Assume.assumeNoException(e);	// stop test silently
			return null;
		} catch(NoClassDefFoundError e) {
			System.out.println("This might fail on machines without fuse-binaries.");
			e.printStackTrace();
			Assume.assumeNoException(e);	// stop test silently
			return null;
		}
		return wrapper;
	}

	@Test
	public void testOpenFile() throws Exception {
		System.out.println("Had commit: " + DEFAULT_COMMIT);
		String runSh = IOUtils.toString(helper.openFile(DEFAULT_COMMIT, "README.md"));
		assertTrue("Had: " + runSh, StringUtils.isNotEmpty(runSh));

		try {
			IOUtils.toString(helper.openFile(DEFAULT_COMMIT, "src"));
			fail("Should catch exception here");
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage().contains("src"));
		}
		try {
			IOUtils.toString(helper.openFile(DEFAULT_COMMIT, "src/org"));
			fail("Should catch exception here");
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage().contains("src/org"));
		}
	}

	@Test
	public void testReadElementsAt() throws Exception {
		System.out.println("Had commit: " + DEFAULT_COMMIT);
		assertEquals("[main, test]", helper.readElementsAt(DEFAULT_COMMIT, "src").toString());
		assertEquals("[dstadler]", helper.readElementsAt(DEFAULT_COMMIT, "src/main/java/org").toString());

		try {
			helper.readElementsAt(DEFAULT_COMMIT, "run.sh");
			fail("Should catch exception here");
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage().contains("run.sh"));
		}

		String list = helper.readElementsAt(DEFAULT_COMMIT, "").toString();
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
		Collection<String> allCommits = helper.allCommits(null);
		int size = allCommits.size();
		assertTrue("Had size: " + size, size > 3);
		assertTrue(allCommits.contains(DEFAULT_COMMIT));
	}

	@Test
	public void testallCommits() throws NoHeadException, GitAPIException, IOException {
		int size = helper.allCommits("zz").size();
		assertEquals("Had size: " + size, 0, size);
		
		Collection<String> allCommits = helper.allCommits(null);
		assertTrue(allCommits.size() > 0);
		assertTrue(allCommits.contains(DEFAULT_COMMIT));
		
		allCommits = helper.allCommits(allCommits.iterator().next().substring(0, 2));
		assertTrue(allCommits.size() > 0);
		
		allCommits = helper.allCommits("00");
		assertFalse(allCommits.contains(DEFAULT_COMMIT));
		
		allCommits = helper.allCommits(DEFAULT_COMMIT.substring(0,2));
		assertTrue(allCommits.contains(DEFAULT_COMMIT));
	}

	@Test
	public void testAllCommitSubs() throws NoHeadException, GitAPIException, IOException {
		Collection<String> subs = helper.allCommitSubs();
		int subSize = subs.size();
		assertTrue("Had: " + subs, subSize > 3);
		
		for(String tup : subs) {
			assertEquals("Had: " + tup, 2, tup.length());
			assertTrue("Had: " + tup, tup.matches("[a-f0-9]{2}"));
		}
		
		assertTrue(subs.contains(DEFAULT_COMMIT.substring(0,2)));
	}

	@Ignore("local test")
	@Test
	public void testAllCommitSubsJenkins() throws NoHeadException, GitAPIException, IOException {
		helper.close();
		helper = new JGitHelper("/opt/jenkins/jenkins.git/.git");
		
		System.out.println("warmup old");
		for(int i = 0;i < 3;i++) {
			int size = helper.allCommitSubs().size();
			assertTrue("Had size: " + size, size > 3);
			System.out.print("." + size);
		}

		System.out.println("run old");
		long start = System.currentTimeMillis();
		for(int i = 0;i < 10;i++) {
			int size = helper.allCommitSubs().size();
			assertTrue("Had size: " + size, size > 3);
			System.out.print("." + size);
		}
		System.out.println("avg.time old: " + (System.currentTimeMillis() - start)/10);
	}

	@Ignore("local test")
	@Test
	public void testAllCommitsJenkins() throws NoHeadException, GitAPIException, IOException {
		helper.close();
		helper = new JGitHelper("/opt/jenkins/jenkins.git/.git");
		//helper = new JGitHelper("/opt/poi/.git");
		
		System.out.println("warmup");
		for(int i = 0;i < 3;i++) {
			int size = helper.allCommits(null).size();
			assertTrue("Had size: " + size, size > 3);
			System.out.print("." + size);
		}

		System.out.println("run");
		long start = System.currentTimeMillis();
		for(int i = 0;i < 10;i++) {
			int size = helper.allCommits(null).size();
			assertTrue("Had size: " + size, size > 3);
			System.out.print("." + size);
		}
		System.out.println("avg.time old: " + (System.currentTimeMillis() - start)/10);
	}
}
