package org.dstadler.jgitfs.util;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;


public class GitUtilsTest {

    @Test
    public void testCoverage() {
        assertNotNull(new GitUtils());
    }
    
	@Test
	public void testIsCommitSub() {
		assertFalse(GitUtils.isCommitSub(""));
		assertFalse(GitUtils.isCommitSub("/"));
		assertFalse(GitUtils.isCommitSub("/something"));
		assertFalse(GitUtils.isCommitSub("/branch"));
		assertFalse(GitUtils.isCommitSub("/tag"));
		assertFalse(GitUtils.isCommitSub("/commit"));
		assertFalse(GitUtils.isCommitSub(GitUtils.COMMIT_SLASH + "00/"));
		assertFalse(GitUtils.isCommitSub(GitUtils.COMMIT_SLASH + "0g"));
		assertFalse(GitUtils.isCommitSub(GitUtils.COMMIT_SLASH + "fg"));
		assertFalse(GitUtils.isCommitSub(GitUtils.COMMIT_SLASH + "zz"));
		
		assertTrue(GitUtils.isCommitSub(GitUtils.COMMIT_SLASH + "00"));
		assertTrue(GitUtils.isCommitSub(GitUtils.COMMIT_SLASH + "ff"));
		assertTrue(GitUtils.isCommitSub(GitUtils.COMMIT_SLASH + "ae"));
	}

	@Test
	public void testIsCommitDir() {
		assertFalse(GitUtils.isCommitDir(""));
		assertFalse(GitUtils.isCommitDir("/"));
		assertFalse(GitUtils.isCommitDir("/something"));
		assertFalse(GitUtils.isCommitDir("/branch"));
		assertFalse(GitUtils.isCommitDir("/tag"));
		assertFalse(GitUtils.isCommitDir("/commit"));
		assertFalse(GitUtils.isCommitDir(GitUtils.COMMIT_SLASH + "00/"));
		assertFalse(GitUtils.isCommitDir(GitUtils.COMMIT_SLASH + "0g"));
		assertFalse(GitUtils.isCommitDir(GitUtils.COMMIT_SLASH + "fg"));
		assertFalse(GitUtils.isCommitDir(GitUtils.COMMIT_SLASH + "zz"));
		assertFalse(GitUtils.isCommitDir(GitUtils.COMMIT_SLASH + "00"));
		assertFalse(GitUtils.isCommitDir(GitUtils.COMMIT_SLASH + "ab"));
		
		assertTrue(GitUtils.isCommitDir(GitUtils.COMMIT_SLASH + "12/34567890123456789012345678901234567890"));
	}
	
	@Test
	public void testIsCommitSubDir() {
		assertFalse(GitUtils.isCommitSubDir(""));
		assertFalse(GitUtils.isCommitSubDir("/"));
		assertFalse(GitUtils.isCommitSubDir("/something"));
		assertFalse(GitUtils.isCommitSubDir("/branch"));
		assertFalse(GitUtils.isCommitSubDir("/tag"));
		assertFalse(GitUtils.isCommitSubDir("/commit"));
		assertFalse(GitUtils.isCommitSubDir(GitUtils.COMMIT_SLASH + "00/"));
		assertFalse(GitUtils.isCommitSubDir(GitUtils.COMMIT_SLASH + "0g"));
		assertFalse(GitUtils.isCommitSubDir(GitUtils.COMMIT_SLASH + "fg"));
		assertFalse(GitUtils.isCommitSubDir(GitUtils.COMMIT_SLASH + "zz"));
		assertFalse(GitUtils.isCommitSubDir(GitUtils.COMMIT_SLASH + "00"));
		assertFalse(GitUtils.isCommitSubDir(GitUtils.COMMIT_SLASH + "ab"));
		assertFalse(GitUtils.isCommitSubDir(GitUtils.COMMIT_SLASH + "12/34567890123456789012345678901234567890"));
		assertFalse(GitUtils.isCommitSubDir(GitUtils.COMMIT_SLASH + "12/34567890123456789012345678901234567890/"));
		assertFalse(GitUtils.isCommitSubDir(GitUtils.COMMIT_SLASH + "12/34567890123456789012345678901234567890/file123/.hidden"));
		
		assertTrue(GitUtils.isCommitSubDir(GitUtils.COMMIT_SLASH + "12/34567890123456789012345678901234567890/file123"));
	}


    @Test
    public void testIsSubmoduleSub() {
        assertFalse(GitUtils.isSubmoduleSub(""));
        assertFalse(GitUtils.isSubmoduleSub("/"));
        assertFalse(GitUtils.isSubmoduleSub("/something"));
        assertFalse(GitUtils.isSubmoduleSub("/branch"));
        assertFalse(GitUtils.isSubmoduleSub("/tag"));
        assertFalse(GitUtils.isSubmoduleSub("/commit"));
        assertFalse(GitUtils.isSubmoduleSub(GitUtils.SUBMODULE_SLASH + "00/"));
        assertFalse(GitUtils.isSubmoduleSub(GitUtils.SUBMODULE_SLASH + "0g"));
        assertFalse(GitUtils.isSubmoduleSub(GitUtils.SUBMODULE_SLASH + "fg"));
        assertFalse(GitUtils.isSubmoduleSub(GitUtils.SUBMODULE_SLASH + "zz"));
        
        assertTrue(GitUtils.isSubmoduleSub(GitUtils.SUBMODULE_SLASH + "sub1/00"));
        assertTrue(GitUtils.isSubmoduleSub(GitUtils.SUBMODULE_SLASH + "sub2/ff"));
        assertTrue(GitUtils.isSubmoduleSub(GitUtils.SUBMODULE_SLASH + "sub43/ae"));
    }

    @Test
    public void testIsSubmoduleDir() {
        assertFalse(GitUtils.isSubmoduleDir(""));
        assertFalse(GitUtils.isSubmoduleDir("/"));
        assertFalse(GitUtils.isSubmoduleDir("/something"));
        assertFalse(GitUtils.isSubmoduleDir("/branch"));
        assertFalse(GitUtils.isSubmoduleDir("/tag"));
        assertFalse(GitUtils.isSubmoduleDir("/commit"));
        assertFalse(GitUtils.isSubmoduleDir(GitUtils.SUBMODULE_SLASH + "00/"));
        assertFalse(GitUtils.isSubmoduleDir(GitUtils.SUBMODULE_SLASH + "0g"));
        assertFalse(GitUtils.isSubmoduleDir(GitUtils.SUBMODULE_SLASH + "fg"));
        assertFalse(GitUtils.isSubmoduleDir(GitUtils.SUBMODULE_SLASH + "zz"));
        assertFalse(GitUtils.isSubmoduleDir(GitUtils.SUBMODULE_SLASH + "00"));
        assertFalse(GitUtils.isSubmoduleDir(GitUtils.SUBMODULE_SLASH + "ab"));
        
        assertTrue(GitUtils.isSubmoduleDir(GitUtils.SUBMODULE_SLASH + "sub234/12/34567890123456789012345678901234567890"));
    }
    
    @Test
    public void testIsSubmoduleSubDir() {
        assertFalse(GitUtils.isSubmoduleSubDir(""));
        assertFalse(GitUtils.isSubmoduleSubDir("/"));
        assertFalse(GitUtils.isSubmoduleSubDir("/something"));
        assertFalse(GitUtils.isSubmoduleSubDir("/branch"));
        assertFalse(GitUtils.isSubmoduleSubDir("/tag"));
        assertFalse(GitUtils.isSubmoduleSubDir("/commit"));
        assertFalse(GitUtils.isSubmoduleSubDir(GitUtils.SUBMODULE_SLASH + "00/"));
        assertFalse(GitUtils.isSubmoduleSubDir(GitUtils.SUBMODULE_SLASH + "0g"));
        assertFalse(GitUtils.isSubmoduleSubDir(GitUtils.SUBMODULE_SLASH + "fg"));
        assertFalse(GitUtils.isSubmoduleSubDir(GitUtils.SUBMODULE_SLASH + "zz"));
        assertFalse(GitUtils.isSubmoduleSubDir(GitUtils.SUBMODULE_SLASH + "00"));
        assertFalse(GitUtils.isSubmoduleSubDir(GitUtils.SUBMODULE_SLASH + "ab"));
        assertFalse(GitUtils.isSubmoduleSubDir(GitUtils.SUBMODULE_SLASH + "12/34567890123456789012345678901234567890/file123"));
        assertFalse(GitUtils.isSubmoduleSubDir(GitUtils.SUBMODULE_SLASH + "12/34567890123456789012345678901234567890"));
        assertFalse(GitUtils.isSubmoduleSubDir(GitUtils.SUBMODULE_SLASH + "12/34567890123456789012345678901234567890/"));
        assertFalse(GitUtils.isSubmoduleSubDir(GitUtils.SUBMODULE_SLASH + "sub123/12/34567890123456789012345678901234567890"));
        assertFalse(GitUtils.isSubmoduleSubDir(GitUtils.SUBMODULE_SLASH + "sub123/12/34567890123456789012345678901234567890/"));
        assertFalse(GitUtils.isSubmoduleSubDir(GitUtils.SUBMODULE_SLASH + "sub123/12/34567890123456789012345678901234567890/file123/.hidden"));
        
        assertTrue(GitUtils.isSubmoduleSubDir(GitUtils.SUBMODULE_SLASH + "sub123/12/34567890123456789012345678901234567890/file123"));
    }

    @Test
	public void testIsBranchDir() {
		assertFalse(GitUtils.isBranchDir(""));
		assertFalse(GitUtils.isBranchDir("/"));
		assertFalse(GitUtils.isBranchDir("/something"));
		assertFalse(GitUtils.isBranchDir("/tag"));
		assertFalse(GitUtils.isBranchDir("/commit"));
		assertFalse(GitUtils.isBranchDir("/submodule"));
		assertFalse(GitUtils.isBranchDir("/branch"));
        assertFalse(GitUtils.isBranchDir("ae/.hidden"));
		assertFalse(GitUtils.isBranchDir(GitUtils.BRANCH_SLASH + "00/"));
		assertFalse(GitUtils.isBranchDir(GitUtils.BRANCH_SLASH + "asdfasd/sjwekw"));
		assertFalse(GitUtils.isBranchDir(GitUtils.BRANCH_SLASH + "asdfasd_aldsjfasd asdlkjasdj.,.;_:;:öÖLP\"=)==\"§\"§%/\"!§)$§\""));
		assertFalse(GitUtils.isBranchDir(GitUtils.BRANCH_SLASH + "ae/.hidden"));
		
		assertTrue(GitUtils.isBranchDir(GitUtils.BRANCH_SLASH + "asdfasd"));
		assertTrue(GitUtils.isBranchDir(GitUtils.BRANCH_SLASH + "fg"));
		assertTrue(GitUtils.isBranchDir(GitUtils.BRANCH_SLASH + "zz"));
		assertTrue(GitUtils.isBranchDir(GitUtils.BRANCH_SLASH + "00"));
		assertTrue(GitUtils.isBranchDir(GitUtils.BRANCH_SLASH + "ff"));
		assertTrue(GitUtils.isBranchDir(GitUtils.BRANCH_SLASH + "ae"));
		assertTrue(GitUtils.isBranchDir(GitUtils.BRANCH_SLASH + "asdfasd_aldsjfasd asdlkjasdj.,.;_:;:öÖLP\"=)==\"§\"§%\"!§)$§\""));
	}

	@Test
	public void testIsTagDir() {
		assertFalse(GitUtils.isTagDir(""));
		assertFalse(GitUtils.isTagDir("/"));
		assertFalse(GitUtils.isTagDir("/something"));
		assertFalse(GitUtils.isTagDir("/branch"));
		assertFalse(GitUtils.isTagDir("/commit"));
		assertFalse(GitUtils.isTagDir("/submodule"));
		assertFalse(GitUtils.isTagDir("/tag"));
        assertFalse(GitUtils.isTagDir("ae/.hidden"));
		assertFalse(GitUtils.isTagDir(GitUtils.TAG_SLASH + "00/"));
		assertFalse(GitUtils.isTagDir(GitUtils.TAG_SLASH + "asdfasd/sjwekw"));
		assertFalse(GitUtils.isTagDir(GitUtils.TAG_SLASH + "asdfasd_aldsjfasd asdlkjasdj.,.;_:;:öÖLP\"=)==\"§\"§%/\"!§)$§\""));
		assertFalse(GitUtils.isTagDir(GitUtils.BRANCH_SLASH + "ae/.hidden"));

		assertTrue(GitUtils.isTagDir(GitUtils.TAG_SLASH + "asdfasd"));
		assertTrue(GitUtils.isTagDir(GitUtils.TAG_SLASH + "fg"));
		assertTrue(GitUtils.isTagDir(GitUtils.TAG_SLASH + "zz"));
		assertTrue(GitUtils.isTagDir(GitUtils.TAG_SLASH + "00"));
		assertTrue(GitUtils.isTagDir(GitUtils.TAG_SLASH + "ff"));
		assertTrue(GitUtils.isTagDir(GitUtils.TAG_SLASH + "ae"));
		assertTrue(GitUtils.isTagDir(GitUtils.TAG_SLASH + "asdfasd_aldsjfasd asdlkjasdj.,.;_:;:öÖLP\"=)==\"§\"§%\"!§)$§\""));
	}

	@Test
	public void testIsRemoteDir() {
		assertFalse(GitUtils.isRemoteDir(""));
		assertFalse(GitUtils.isRemoteDir("/"));
		assertFalse(GitUtils.isRemoteDir("/something"));
		assertFalse(GitUtils.isRemoteDir("/branch"));
		assertFalse(GitUtils.isRemoteDir("/commit"));
		assertFalse(GitUtils.isRemoteDir("/submodule"));
		assertFalse(GitUtils.isRemoteDir("/tag"));
        assertFalse(GitUtils.isRemoteDir("ae/.hidden"));
		assertFalse(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "00/"));
		assertFalse(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "asdfasd/sjwekw"));
		assertFalse(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "asdfasd_aldsjfasd asdlkjasdj.,.;_:;:öÖLP\"=)==\"§\"§%/\"!§)$§\""));
		assertFalse(GitUtils.isRemoteDir(GitUtils.BRANCH_SLASH + "ae/.hidden"));

		assertTrue(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "asdfasd"));
		assertTrue(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "fg"));
		assertTrue(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "zz"));
		assertTrue(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "00"));
		assertTrue(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "ff"));
		assertTrue(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "ae"));
		assertTrue(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "asdfasd_aldsjfasd asdlkjasdj.,.;_:;:öÖLP\"=)==\"§\"§%\"!§)$§\""));
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
