package org.dstadler.jgitfs.util;

import net.fusejna.StructStat.StatWrapper;
import net.fusejna.types.TypeMode.NodeType;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.NoSuchElementException;

import static org.dstadler.jgitfs.JGitFilesystemTest.getStatsWrapper;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class JGitHelperSubmoduleTest {
    private static final String CLONE_URL = "https://github.com/githubtraining/example-dependency.git";
    private static final File CLONE_DIR = new File(System.getProperty("java.io.tmpdir"), "example-dependency");

    private static final String SUBMODULE_COMMIT = "c3c588713233609f5bbbb2d9e7f3fb4a660f3f72";

    private JGitHelper helper;

    @BeforeClass
    public static void setUpClass() throws GitAPIException {
        // clone sample repo if not available yet
        if (!CLONE_DIR.exists()) {
            Git.cloneRepository()
                    .setURI(CLONE_URL)
                    // this is important to also get the submodule checked out
                    .setCloneSubmodules(true)
                    .setDirectory(CLONE_DIR)
                    //.setProgressMonitor(new SimpleProgressMonitor())
                    .call().close();
        }
    }

    @Before
    public void setUp() throws IOException {
        helper = new JGitHelper(CLONE_DIR.getAbsolutePath());
    }

    @After
    public void tearDown() {
        helper.close();
    }

    @Test
    public void testGetSubmoduleAt() throws IOException {
        assertEquals("js", helper.getSubmoduleAt("js"));
        try {
            helper.getSubmoduleAt("notexisting");
            fail("Should catch exception here");
        } catch (@SuppressWarnings("unused") NoSuchElementException e) {
            // expected here
        }
    }


    @Test
    public void testConstructForSubmodule() throws Exception {
        try (JGitHelper subHelper = new JGitHelper(helper, "js")) {
            final StatWrapper wrapper = getStatsWrapper();
            assertNotNull(wrapper);

            subHelper.readType(SUBMODULE_COMMIT, "README.md", wrapper);
            assertEquals(NodeType.FILE, wrapper.type());

            subHelper.readType(SUBMODULE_COMMIT, "app.js", wrapper);
            assertEquals(NodeType.FILE, wrapper.type());
        }
    }

    @Test
    public void testAllSubmodules() throws IOException {
        assertEquals("[js]", helper.allSubmodules().toString());
    }


    @Test
    public void testGetSubmoduleHead() throws IOException {
        assertNotNull(helper.getSubmoduleHead("js"));
        try {
            helper.getSubmoduleHead("notexisting");
            fail("Should catch exception here");
        } catch (@SuppressWarnings("unused") NoSuchElementException e) {
            // expected here
        }
    }

    @Test
    public void testGetSubmodulePath() throws IOException {
        assertEquals("js", helper.getSubmodulePath("js"));
        try {
            helper.getSubmodulePath("notexisting");
            fail("Should catch exception here");
        } catch (@SuppressWarnings("unused") NoSuchElementException e) {
            // expected here
        }
    }
}
