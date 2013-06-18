package org.dstadler.jgitfs.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;


public class GitUtilsTest {

	@Test
	public void testIsCommitDir() {
		assertFalse(GitUtils.isCommitDir(""));
		assertFalse(GitUtils.isCommitDir("/"));
		assertFalse(GitUtils.isCommitDir("/something"));
		assertFalse(GitUtils.isCommitDir("/branch"));
		assertFalse(GitUtils.isCommitDir("/tag"));
		assertFalse(GitUtils.isCommitDir("/commit"));
		assertFalse(GitUtils.isCommitDir("/commit/00/"));
		assertFalse(GitUtils.isCommitDir("/commit/0g"));
		assertFalse(GitUtils.isCommitDir("/commit/fg"));
		assertFalse(GitUtils.isCommitDir("/commit/zz"));
		
		assertTrue(GitUtils.isCommitDir("/commit/00"));
		assertTrue(GitUtils.isCommitDir("/commit/ff"));
		assertTrue(GitUtils.isCommitDir("/commit/ae"));
	}

	@Test
	public void testIsBranchDir() {
		assertFalse(GitUtils.isBranchDir(""));
		assertFalse(GitUtils.isBranchDir("/"));
		assertFalse(GitUtils.isBranchDir("/something"));
		assertFalse(GitUtils.isBranchDir("/tag"));
		assertFalse(GitUtils.isBranchDir("/commit"));
		assertFalse(GitUtils.isBranchDir("/branch"));
		assertFalse(GitUtils.isBranchDir("/branch/00/"));
		assertFalse(GitUtils.isBranchDir("/branch/asdfasd/sjwekw"));
		assertFalse(GitUtils.isBranchDir("/branch/asdfasd_aldsjfasd asdlkjasdj.,.;_:;:öÖLP\"=)==\"§\"§%/\"!§)$§\""));

		assertTrue(GitUtils.isBranchDir("/branch/asdfasd"));
		assertTrue(GitUtils.isBranchDir("/branch/fg"));
		assertTrue(GitUtils.isBranchDir("/branch/zz"));
		assertTrue(GitUtils.isBranchDir("/branch/00"));
		assertTrue(GitUtils.isBranchDir("/branch/ff"));
		assertTrue(GitUtils.isBranchDir("/branch/ae"));
		assertTrue(GitUtils.isBranchDir("/branch/asdfasd_aldsjfasd asdlkjasdj.,.;_:;:öÖLP\"=)==\"§\"§%\"!§)$§\""));
	}


	@Test
	public void testIsTagDir() {
		assertFalse(GitUtils.isTagDir(""));
		assertFalse(GitUtils.isTagDir("/"));
		assertFalse(GitUtils.isTagDir("/something"));
		assertFalse(GitUtils.isTagDir("/branch"));
		assertFalse(GitUtils.isTagDir("/commit"));
		assertFalse(GitUtils.isTagDir("/tag"));
		assertFalse(GitUtils.isTagDir("/tag/00/"));
		assertFalse(GitUtils.isTagDir("/tag/asdfasd/sjwekw"));
		assertFalse(GitUtils.isTagDir("/tag/asdfasd_aldsjfasd asdlkjasdj.,.;_:;:öÖLP\"=)==\"§\"§%/\"!§)$§\""));

		assertTrue(GitUtils.isTagDir("/tag/asdfasd"));
		assertTrue(GitUtils.isTagDir("/tag/fg"));
		assertTrue(GitUtils.isTagDir("/tag/zz"));
		assertTrue(GitUtils.isTagDir("/tag/00"));
		assertTrue(GitUtils.isTagDir("/tag/ff"));
		assertTrue(GitUtils.isTagDir("/tag/ae"));
		assertTrue(GitUtils.isTagDir("/tag/asdfasd_aldsjfasd asdlkjasdj.,.;_:;:öÖLP\"=)==\"§\"§%\"!§)$§\""));
	}
}
