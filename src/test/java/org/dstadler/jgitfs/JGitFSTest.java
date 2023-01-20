package org.dstadler.jgitfs;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.dstadler.commons.testing.PrivateConstructorCoverage;
import org.dstadler.commons.testing.TestHelpers;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;


public class JGitFSTest {

    @Test
    public void testMainNoArg() {
        // calls System.exit(): JGitFS.main(new String[] {});
    }

    @Test
    public void testMainOneArg() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> JGitFS.main("invalidrepo"));
    }

    @Test
    public void testMainOneArgGit() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> JGitFS.main("invalidrepo/git"));
    }

    // helper method to get coverage of the unused constructor
    @Test
    public void testPrivateConstructor() throws Exception {
       PrivateConstructorCoverage.executePrivateConstructor(JGitFS.class);
    }

    @Test
    public void testMainInvalidLocation() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> JGitFS.main("invalidrepo", "somemountpoint"));
    }


    @Test
    public void testMainCurrentProject() {
        // does not finish as it waits for application to stop: JGitFS.main(new String[] {".", "/fs/testrepo"});
    }


    @Test
    public void testMainMultiple() throws Exception {
        Assume.assumeFalse("Not executed on Windows for now because of https://github.com/EtiennePerot/fuse-jna/issues/44", SystemUtils.IS_OS_WINDOWS);
        try {
            // if we have one that works and the last one an invalid one we get an exception, but did the mounting
            // for the first one
            File mountPoint = File.createTempFile("JGitFSTest", ".dir2");
            assertTrue(mountPoint.delete());
            try {
                JGitFS.main(".", mountPoint.getPath(), "invalidrepo", "somemountpoint");
            } finally {
                FileUtils.deleteDirectory(mountPoint);
            }
            fail("Should throw exception with invalid git repository");
        } catch (@SuppressWarnings("unused") IOException e) {
            // happens when run in CloudBees, but could not find out details...
        } catch (IllegalStateException e) {
            assertTrue("Had: " + e.getMessage(), e.getMessage().contains("invalidrepo"));
        }
    }


    @Test
    public void testMount() throws Exception {
        try {
            assertFalse(JGitFS.unmount("notexisting"));

            // if we have one that works and the last one an invalid one we get an exception, but did the mounting
            // for the first one
            File mountPoint = File.createTempFile("JGitFSTest", ".dir");
            assertTrue(mountPoint.delete());
            try {
                JGitFS.mount(".", mountPoint);
                JGitFS.list();
                assertTrue(JGitFS.unmount("."));

                JGitFS.mount("./.git", mountPoint);
                JGitFS.list();
                assertTrue(JGitFS.unmount("./.git"));

                JGitFS.mount("./.git/", mountPoint);
                JGitFS.list();
                assertTrue(JGitFS.unmount("./.git/"));

                JGitFS.mount(".", mountPoint);
                JGitFS.list();
                assertTrue(JGitFS.unmount(mountPoint.getPath()));
            } finally {
                FileUtils.deleteDirectory(mountPoint);
            }
        } catch (IOException e) {
            // happens when run in CloudBees, but could not find out details...
            Assume.assumeNoException("In some CI environments this will fail", e);
        } catch (UnsatisfiedLinkError e) {
            Assume.assumeNoException("Will fail on Windows", e);
        }
    }

    @Test
    public void testMountGitDirTwice() throws Exception {
        try {
            // if we have one that works and the last one an invalid one we get an exception, but did the mounting
            // for the first one
            File mountPoint = File.createTempFile("JGitFSTest", ".dir");
            assertTrue(mountPoint.delete());
            try {
                JGitFS.mount(".", mountPoint);
                try {
                    JGitFS.list();

                    try {
                        JGitFS.mount(".", mountPoint);
                        fail("Should fail due to double mount here");
                    } catch (IllegalArgumentException e) {
                        assertTrue(e.getMessage().contains("already mounted"));
                    }
                } finally {
                    assertTrue(JGitFS.unmount(mountPoint.getPath()));
                }
            } finally {
                FileUtils.deleteDirectory(mountPoint);
            }
        } catch (IOException e) {
            // happens when run in CloudBees, but could not find out details...
            Assume.assumeNoException("In some CI environments this will fail", e);
        } catch (UnsatisfiedLinkError e) {
            Assume.assumeNoException("Will fail on Windows", e);
        }
    }

    @Test
    public void testMountPointTwice() throws Exception {
        try {
            // if we have one that works and the last one an invalid one we get an exception, but did the mounting
            // for the first one
            File mountPoint = File.createTempFile("JGitFSTest", ".dir");
            assertTrue(mountPoint.delete());
            try {
                JGitFS.mount(".", mountPoint);
                try {
                    JGitFS.list();

                    try {
                        JGitFS.mount("someother", mountPoint);
                        fail("Should fail due to double mount here");
                    } catch (IllegalArgumentException e) {
                        TestHelpers.assertContains(e, "already used for mount at");
                    }
                } finally {
                    assertTrue(JGitFS.unmount(mountPoint.getPath()));
                }
            } finally {
                FileUtils.deleteDirectory(mountPoint);
            }
        } catch (IOException e) {
            // happens when run in CloudBees, but could not find out details...
            Assume.assumeNoException("In some CI environments this will fail", e);
        } catch (UnsatisfiedLinkError e) {
            Assume.assumeNoException("Will fail on Windows", e);
        }
    }
}
