package org.dstadler.jgitfs.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.NoSuchElementException;

import org.apache.commons.lang3.tuple.Pair;
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
        assertFalse(GitUtils.isCommitSub("/submodule"));
        assertFalse(GitUtils.isCommitSub("/stash"));
        assertFalse(GitUtils.isCommitSub("/stashorig"));
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
		assertFalse(GitUtils.isCommitDir("/submodule"));
		assertFalse(GitUtils.isCommitDir("/stash"));
		assertFalse(GitUtils.isCommitDir("/stashorig"));
		assertFalse(GitUtils.isCommitDir(GitUtils.COMMIT_SLASH + "00/"));
		assertFalse(GitUtils.isCommitDir(GitUtils.COMMIT_SLASH + "0g"));
		assertFalse(GitUtils.isCommitDir(GitUtils.COMMIT_SLASH + "fg"));
		assertFalse(GitUtils.isCommitDir(GitUtils.COMMIT_SLASH + "zz"));
		assertFalse(GitUtils.isCommitDir(GitUtils.COMMIT_SLASH + "00"));
		assertFalse(GitUtils.isCommitDir(GitUtils.COMMIT_SLASH + "ab"));

		assertTrue(GitUtils.isCommitDir(GitUtils.COMMIT_SLASH + "12/34567890123456789012345678901234567890"));
	}

	@Test
	public void testSplitSubmodule() {
	    assertTrue(GitUtils.isSubmodulePath("/submodule/name/file"));
	    Pair<String,String> ret = GitUtils.splitSubmodule("/submodule/name/file");
	    assertEquals("name", ret.getLeft());
	    assertEquals("/file", ret.getRight());

	    assertTrue(GitUtils.isSubmodulePath("/submodule/name/"));
        ret = GitUtils.splitSubmodule("/submodule/name/");
        assertEquals("name", ret.getLeft());
        assertEquals("/", ret.getRight());

        assertTrue(GitUtils.isSubmodulePath("/submodule/name"));
        ret = GitUtils.splitSubmodule("/submodule/name");
        assertEquals("name", ret.getLeft());
        assertEquals("/", ret.getRight());

        assertFalse(GitUtils.isSubmodulePath("/submodule/"));
        try {
            GitUtils.splitSubmodule("/submodule/");
        } catch (NoSuchElementException e) {
            assertTrue(e.getMessage().contains("/submodule/"));
        }

        assertFalse(GitUtils.isSubmodulePath("/branches/master/"));
        try {
            GitUtils.splitSubmodule("/branches/master/");
        } catch (NoSuchElementException e) {
            assertTrue(e.getMessage().contains("/branches/master/"));
        }
	}

	@Test
	public void testIsCommitSubDir() {
		assertFalse(GitUtils.isCommitSubDir(""));
		assertFalse(GitUtils.isCommitSubDir("/"));
		assertFalse(GitUtils.isCommitSubDir("/something"));
		assertFalse(GitUtils.isCommitSubDir("/branch"));
		assertFalse(GitUtils.isCommitSubDir("/tag"));
		assertFalse(GitUtils.isCommitSubDir("/commit"));
		assertFalse(GitUtils.isCommitSubDir("/submodule"));
		assertFalse(GitUtils.isCommitSubDir("/stash"));
		assertFalse(GitUtils.isCommitSubDir("/stashorig"));
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
        assertFalse(GitUtils.isSubmoduleName(""));
        assertFalse(GitUtils.isSubmoduleName("/"));
        assertFalse(GitUtils.isSubmoduleName("/something"));
        assertFalse(GitUtils.isSubmoduleName("/branch"));
        assertFalse(GitUtils.isSubmoduleName("/tag"));
        assertFalse(GitUtils.isSubmoduleName("/commit"));
        assertFalse(GitUtils.isSubmoduleName("/submodule"));
        assertFalse(GitUtils.isSubmoduleName("/stash"));
        assertFalse(GitUtils.isSubmoduleName("/stashorig"));
        assertFalse(GitUtils.isSubmoduleName(GitUtils.SUBMODULE_SLASH + "00/"));
        assertFalse(GitUtils.isSubmoduleName(GitUtils.SUBMODULE_SLASH + "zz/123"));

        assertTrue(GitUtils.isSubmoduleName(GitUtils.SUBMODULE_SLASH + "sub1"));
        assertTrue(GitUtils.isSubmoduleName(GitUtils.SUBMODULE_SLASH + "sub2"));
        assertTrue(GitUtils.isSubmoduleName(GitUtils.SUBMODULE_SLASH + "sub43"));
    }

    @Test
    public void testIsSubmodule() {
        assertFalse(GitUtils.isSubmodulePath(""));
        assertFalse(GitUtils.isSubmodulePath("/"));
        assertFalse(GitUtils.isSubmodulePath("/something"));
        assertFalse(GitUtils.isSubmodulePath("/branch"));
        assertFalse(GitUtils.isSubmodulePath("/tag"));
        assertFalse(GitUtils.isSubmodulePath("/commit"));
        assertFalse(GitUtils.isSubmodulePath("/submodule"));
        assertFalse(GitUtils.isSubmodulePath("/stash"));
        assertFalse(GitUtils.isSubmodulePath("/stashorig"));

        assertTrue(GitUtils.isSubmodulePath(GitUtils.SUBMODULE_SLASH + "00/"));
        assertTrue(GitUtils.isSubmodulePath(GitUtils.SUBMODULE_SLASH + "12/34567890123456789012345678901234567890/file123"));
        assertTrue(GitUtils.isSubmodulePath(GitUtils.SUBMODULE_SLASH + "12/34567890123456789012345678901234567890"));
        assertTrue(GitUtils.isSubmodulePath(GitUtils.SUBMODULE_SLASH + "12/34567890123456789012345678901234567890/"));
        assertTrue(GitUtils.isSubmodulePath(GitUtils.SUBMODULE_SLASH + "sub123/12/34567890123456789012345678901234567890"));
        assertTrue(GitUtils.isSubmodulePath(GitUtils.SUBMODULE_SLASH + "sub123/12/34567890123456789012345678901234567890/"));
        assertTrue(GitUtils.isSubmodulePath(GitUtils.SUBMODULE_SLASH + "sub123/12/34567890123456789012345678901234567890/file123/.hidden"));
        assertTrue(GitUtils.isSubmodulePath(GitUtils.SUBMODULE_SLASH + "sub123/12/34567890123456789012345678901234567890/file123"));

        assertTrue(GitUtils.isSubmodulePath(GitUtils.SUBMODULE_SLASH + "some submodule name with some special character203921q§$\"§=$ÖÄ"));
        assertTrue(GitUtils.isSubmodulePath(GitUtils.SUBMODULE_SLASH + "0g"));
        assertTrue(GitUtils.isSubmodulePath(GitUtils.SUBMODULE_SLASH + "fg"));
        assertTrue(GitUtils.isSubmodulePath(GitUtils.SUBMODULE_SLASH + "zz"));
        assertTrue(GitUtils.isSubmodulePath(GitUtils.SUBMODULE_SLASH + "00"));
        assertTrue(GitUtils.isSubmodulePath(GitUtils.SUBMODULE_SLASH + "ab"));
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
		assertFalse(GitUtils.isBranchDir("/stash"));
		assertFalse(GitUtils.isBranchDir("/stashorig"));
        assertFalse(GitUtils.isBranchDir("ae/.hidden"));
		assertFalse(GitUtils.isBranchDir(GitUtils.BRANCH_SLASH + "00/"));
		assertFalse(GitUtils.isBranchDir(GitUtils.BRANCH_SLASH + "asdfasd/sjwekw"));
		assertFalse(GitUtils.isBranchDir(GitUtils.BRANCH_SLASH + "asdfasd_aldsjfasd asdlkjasdj.,.;_:;:öÖLP\"=)==\"§\"§%/\"!§)$§\""));
		assertFalse(GitUtils.isBranchDir(GitUtils.BRANCH_SLASH + "ae/.hidden"));
        assertFalse(GitUtils.isBranchDir(GitUtils.BRANCH_SLASH + "asdfasd/.hidden"));
        assertFalse(GitUtils.isBranchDir(GitUtils.BRANCH_SLASH + ".hidden"));

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
		assertFalse(GitUtils.isTagDir("/tag"));
		assertFalse(GitUtils.isTagDir("/submodule"));
		assertFalse(GitUtils.isTagDir("/stash"));
		assertFalse(GitUtils.isTagDir("/stashorig"));
        assertFalse(GitUtils.isTagDir("ae/.hidden"));
		assertFalse(GitUtils.isTagDir(GitUtils.TAG_SLASH + "00/"));
		assertFalse(GitUtils.isTagDir(GitUtils.TAG_SLASH + "asdfasd/sjwekw"));
		assertFalse(GitUtils.isTagDir(GitUtils.TAG_SLASH + "asdfasd_aldsjfasd asdlkjasdj.,.;_:;:öÖLP\"=)==\"§\"§%/\"!§)$§\""));
		assertFalse(GitUtils.isTagDir(GitUtils.TAG_SLASH + "ae/.hidden"));
		assertFalse(GitUtils.isTagDir(GitUtils.TAG_SLASH + "asdfasd/.hidden"));
		assertFalse(GitUtils.isTagDir(GitUtils.TAG_SLASH + ".hidden"));

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
		assertFalse(GitUtils.isRemoteDir("/tag"));
		assertFalse(GitUtils.isRemoteDir("/submodule"));
		assertFalse(GitUtils.isRemoteDir("/stash"));
		assertFalse(GitUtils.isRemoteDir("/stashorig"));
        assertFalse(GitUtils.isRemoteDir("ae/.hidden"));
		assertFalse(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "00/"));
		assertFalse(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "asdfasd/sjwekw"));
		assertFalse(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "asdfasd_aldsjfasd asdlkjasdj.,.;_:;:öÖLP\"=)==\"§\"§%/\"!§)$§\""));
		assertFalse(GitUtils.isRemoteDir(GitUtils.BRANCH_SLASH + "ae/.hidden"));
        assertFalse(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "asdfasd/.hidden"));
        assertFalse(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + ".hidden"));

		assertTrue(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "asdfasd"));
		assertTrue(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "fg"));
		assertTrue(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "zz"));
		assertTrue(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "00"));
		assertTrue(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "ff"));
		assertTrue(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "ae"));
		assertTrue(GitUtils.isRemoteDir(GitUtils.REMOTE_SLASH + "asdfasd_aldsjfasd asdlkjasdj.,.;_:;:öÖLP\"=)==\"§\"§%\"!§)$§\""));
	}


    @Test
    public void testIsStashDir() {
        assertFalse(GitUtils.isStashDir(""));
        assertFalse(GitUtils.isStashDir("/"));
        assertFalse(GitUtils.isStashDir("/something"));
        assertFalse(GitUtils.isStashDir("/tag"));
        assertFalse(GitUtils.isStashDir("/commit"));
        assertFalse(GitUtils.isStashDir("/submodule"));
        assertFalse(GitUtils.isStashDir("/branch"));
        assertFalse(GitUtils.isStashDir("/stash"));
        assertFalse(GitUtils.isStashDir("/stashorig"));
        assertFalse(GitUtils.isStashDir("ae/.hidden"));
        assertFalse(GitUtils.isStashDir(GitUtils.STASH_SLASH + "00/"));
        assertFalse(GitUtils.isStashDir(GitUtils.STASH_SLASH + "asdfasd/sjwekw"));
        assertFalse(GitUtils.isStashDir(GitUtils.STASH_SLASH + "asdfasd_aldsjfasd asdlkjasdj.,.;_:;:öÖLP\"=)==\"§\"§%/\"!§)$§\""));
        assertFalse(GitUtils.isStashDir(GitUtils.STASH_SLASH + "ae/.hidden"));
        assertFalse(GitUtils.isStashDir(GitUtils.STASH_SLASH + "asdfasd/.hidden"));
        assertFalse(GitUtils.isStashDir(GitUtils.STASH_SLASH + ".hidden"));

        assertTrue(GitUtils.isStashDir(GitUtils.STASH_SLASH + "asdfasd"));
        assertTrue(GitUtils.isStashDir(GitUtils.STASH_SLASH + "fg"));
        assertTrue(GitUtils.isStashDir(GitUtils.STASH_SLASH + "zz"));
        assertTrue(GitUtils.isStashDir(GitUtils.STASH_SLASH + "00"));
        assertTrue(GitUtils.isStashDir(GitUtils.STASH_SLASH + "ff"));
        assertTrue(GitUtils.isStashDir(GitUtils.STASH_SLASH + "ae"));
        assertTrue(GitUtils.isStashDir(GitUtils.STASH_SLASH + "asdfasd_aldsjfasd asdlkjasdj.,.;_:;:öÖLP\"=)==\"§\"§%\"!§)$§\""));
    }

    @Test
    public void testIsStashOrigDir() {
        assertFalse(GitUtils.isStashOrigDir(""));
        assertFalse(GitUtils.isStashOrigDir("/"));
        assertFalse(GitUtils.isStashOrigDir("/something"));
        assertFalse(GitUtils.isStashOrigDir("/tag"));
        assertFalse(GitUtils.isStashOrigDir("/commit"));
        assertFalse(GitUtils.isStashOrigDir("/submodule"));
        assertFalse(GitUtils.isStashOrigDir("/branch"));
        assertFalse(GitUtils.isStashOrigDir("/stash"));
        assertFalse(GitUtils.isStashOrigDir("/stashorig"));
        assertFalse(GitUtils.isStashOrigDir("ae/.hidden"));
        assertFalse(GitUtils.isStashOrigDir(GitUtils.STASHORIG_SLASH + "00/"));
        assertFalse(GitUtils.isStashOrigDir(GitUtils.STASHORIG_SLASH + "asdfasd/sjwekw"));
        assertFalse(GitUtils.isStashOrigDir(GitUtils.STASHORIG_SLASH + "asdfasd_aldsjfasd asdlkjasdj.,.;_:;:öÖLP\"=)==\"§\"§%/\"!§)$§\""));
        assertFalse(GitUtils.isStashOrigDir(GitUtils.STASHORIG_SLASH + "ae/.hidden"));
        assertFalse(GitUtils.isStashOrigDir(GitUtils.STASHORIG_SLASH + "asdfasd/.hidden"));
        assertFalse(GitUtils.isStashOrigDir(GitUtils.STASHORIG_SLASH + ".hidden"));

        assertTrue(GitUtils.isStashOrigDir(GitUtils.STASHORIG_SLASH + "asdfasd"));
        assertTrue(GitUtils.isStashOrigDir(GitUtils.STASHORIG_SLASH + "fg"));
        assertTrue(GitUtils.isStashOrigDir(GitUtils.STASHORIG_SLASH + "zz"));
        assertTrue(GitUtils.isStashOrigDir(GitUtils.STASHORIG_SLASH + "00"));
        assertTrue(GitUtils.isStashOrigDir(GitUtils.STASHORIG_SLASH + "ff"));
        assertTrue(GitUtils.isStashOrigDir(GitUtils.STASHORIG_SLASH + "ae"));
        assertTrue(GitUtils.isStashOrigDir(GitUtils.STASHORIG_SLASH + "asdfasd_aldsjfasd asdlkjasdj.,.;_:;:öÖLP\"=)==\"§\"§%\"!§)$§\""));
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
