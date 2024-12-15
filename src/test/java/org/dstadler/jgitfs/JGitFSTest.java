package org.dstadler.jgitfs;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.dstadler.commons.testing.PrivateConstructorCoverage;
import org.dstadler.commons.testing.TestHelpers;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class JGitFSTest {

    @Test
    public void testMainNoArg() {
        // calls System.exit(): JGitFS.main(new String[] {});
    }

    @Test
    public void testMainOneArg() {
        assertThrows(IllegalStateException.class,
                () -> JGitFS.main("invalidrepo"));
    }

    @Test
    public void testMainOneArgGit() {
        assertThrows(IllegalStateException.class,
                () -> JGitFS.main("invalidrepo/git"));
    }

    @Test
    public void testMainOneArgFileDot() {
        assertThrows(IllegalStateException.class,
                () -> JGitFS.main("invalidrepo/."));
    }

    @Test
    public void testMainOneArgDot() throws Exception {
		Assumptions.assumeFalse(SystemUtils.IS_OS_WINDOWS,
				"Not executed on Windows for now because of https://github.com/EtiennePerot/fuse-jna/issues/40");

        try {
			JGitFS.main("--test-only", ".");
		} catch (IOException e) {
			// this can happen if /fs does not exist yet and cannot be created
			if (!e.getMessage().contains("Could not create mountpoint")) {
				throw e;
			}
		}
    }

    // helper method to get coverage of the unused constructor
    @Test
    public void testPrivateConstructor() throws Exception {
       PrivateConstructorCoverage.executePrivateConstructor(JGitFS.class);
    }

    @Test
    public void testMainInvalidLocation() {
        assertThrows(IllegalStateException.class,
                () -> JGitFS.main("invalidrepo", "somemountpoint"));
    }


    @Test
    public void testMainCurrentProject() {
        // does not finish as it waits for application to stop: JGitFS.main(new String[] {".", "/fs/testrepo"});
    }


    @Test
    public void testMainMultiple() throws Exception {
        Assumptions.assumeFalse(SystemUtils.IS_OS_WINDOWS,
				"Not executed on Windows for now because of https://github.com/EtiennePerot/fuse-jna/issues/44");

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
            assertTrue(e.getMessage().contains("invalidrepo"), "Had: " + e.getMessage());
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
            Assumptions.abort("In some CI environments this will fail\n" + ExceptionUtils.getStackTrace(e));
        } catch (UnsatisfiedLinkError e) {
            Assumptions.abort("Will fail on Windows\n" + ExceptionUtils.getStackTrace(e));
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
            Assumptions.abort("In some CI environments this will fail\n" + ExceptionUtils.getStackTrace(e));
        } catch (UnsatisfiedLinkError e) {
            Assumptions.abort("Will fail on Windows\n" + ExceptionUtils.getStackTrace(e));
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
            Assumptions.abort("In some CI environments this will fail\n" + ExceptionUtils.getStackTrace(e));
        } catch (UnsatisfiedLinkError e) {
            Assumptions.abort("Will fail on Windows\n" + ExceptionUtils.getStackTrace(e));
        }
    }
}
