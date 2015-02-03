package org.dstadler.jgitfs;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.junit.Assume;
import org.junit.Test;


public class JGitFSTest {

    @Test
    public void testMainNoArg() throws Exception {
        // calls System.exit(): JGitFS.main(new String[] {});
    }

    @Test
    public void testMainOneArg() throws Exception {
        // calls System.exit(): JGitFS.main(new String[] {"some"});
    }

    @Test
    public void testConstruct() throws Exception {
        assertNotNull(new JGitFS());
    }

    @Test
    public void testMainInvalidLocation() throws Exception {
        try {
            JGitFS.main(new String[] {"invalidrepo", "somemountpoint"});
            fail("Should throw exception with invalid git repository");
        } catch (IllegalStateException e) {
            // expected
        }
    }


    @Test
    public void testMainCurrentProject() throws Exception {
        // does not finish as it waits for application to stop: JGitFS.main(new String[] {".", "/fs/testrepo"});
    }


    @Test
    public void testMainMultiple() throws Exception {
        try {
            // if we have one that works and the last one an invalid one we get an exception, but did the mounting
            // for the first one
            JGitFS.main(new String[] {".", System.getProperty("java.io.tmpdir") + "/testrepo", "invalidrepo", "somemountpoint"});
            fail("Should throw exception with invalid git repository");
        } catch (IOException e) {
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
            File mountPoint = new File(System.getProperty("java.io.tmpdir") + "/testrepo");
            JGitFS.mount(".", mountPoint);
            JGitFS.list();
            assertTrue(JGitFS.unmount("."));
            
            JGitFS.mount(".", mountPoint);
            JGitFS.list();
            assertTrue(JGitFS.unmount(mountPoint.getPath()));
        } catch (IOException e) {
            // happens when run in CloudBees, but could not find out details...
            Assume.assumeNoException("In some CI environments this will fail", e);
        }
    }
}
