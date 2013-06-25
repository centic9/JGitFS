package org.dstadler.jgitfs.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;


public class GitUtilsTest {

	@Test
	public void testIsCommitSub() {
		assertFalse(GitUtils.isCommitSub(""));
		assertFalse(GitUtils.isCommitSub("/"));
		assertFalse(GitUtils.isCommitSub("/something"));
		assertFalse(GitUtils.isCommitSub("/branch"));
		assertFalse(GitUtils.isCommitSub("/tag"));
		assertFalse(GitUtils.isCommitSub("/commit"));
		assertFalse(GitUtils.isCommitSub("/commit/00/"));
		assertFalse(GitUtils.isCommitSub("/commit/0g"));
		assertFalse(GitUtils.isCommitSub("/commit/fg"));
		assertFalse(GitUtils.isCommitSub("/commit/zz"));
		
		assertTrue(GitUtils.isCommitSub("/commit/00"));
		assertTrue(GitUtils.isCommitSub("/commit/ff"));
		assertTrue(GitUtils.isCommitSub("/commit/ae"));
	}

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
		assertFalse(GitUtils.isCommitDir("/commit/00"));
		assertFalse(GitUtils.isCommitDir("/commit/ab"));
		
		assertTrue(GitUtils.isCommitDir("/commit/12/34567890123456789012345678901234567890"));
	}
	
	@Test
	public void testIsCommitSubDir() {
		assertFalse(GitUtils.isCommitSubDir(""));
		assertFalse(GitUtils.isCommitSubDir("/"));
		assertFalse(GitUtils.isCommitSubDir("/something"));
		assertFalse(GitUtils.isCommitSubDir("/branch"));
		assertFalse(GitUtils.isCommitSubDir("/tag"));
		assertFalse(GitUtils.isCommitSubDir("/commit"));
		assertFalse(GitUtils.isCommitSubDir("/commit/00/"));
		assertFalse(GitUtils.isCommitSubDir("/commit/0g"));
		assertFalse(GitUtils.isCommitSubDir("/commit/fg"));
		assertFalse(GitUtils.isCommitSubDir("/commit/zz"));
		assertFalse(GitUtils.isCommitSubDir("/commit/00"));
		assertFalse(GitUtils.isCommitSubDir("/commit/ab"));
		assertFalse(GitUtils.isCommitSubDir("/commit/12/34567890123456789012345678901234567890"));
		assertFalse(GitUtils.isCommitSubDir("/commit/12/34567890123456789012345678901234567890/"));
		assertFalse(GitUtils.isCommitSubDir("/commit/12/34567890123456789012345678901234567890/file123/.hidden"));
		
		assertTrue(GitUtils.isCommitSubDir("/commit/12/34567890123456789012345678901234567890/file123"));
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
		assertFalse(GitUtils.isBranchDir("/branch/ae/.hidden"));
		
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
		assertFalse(GitUtils.isTagDir("/branch/ae/.hidden"));

		assertTrue(GitUtils.isTagDir("/tag/asdfasd"));
		assertTrue(GitUtils.isTagDir("/tag/fg"));
		assertTrue(GitUtils.isTagDir("/tag/zz"));
		assertTrue(GitUtils.isTagDir("/tag/00"));
		assertTrue(GitUtils.isTagDir("/tag/ff"));
		assertTrue(GitUtils.isTagDir("/tag/ae"));
		assertTrue(GitUtils.isTagDir("/tag/asdfasd_aldsjfasd asdlkjasdj.,.;_:;:öÖLP\"=)==\"§\"§%\"!§)$§\""));
	}

	@Test
	public void testGetUID() throws IOException {
		assertTrue(GitUtils.getUID() >= 0);
	}

	@Test
	public void testGetGID() throws IOException {
		assertTrue(GitUtils.getGID() >= 0);
	}
}
