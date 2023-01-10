package org.dstadler.jgitfs;

import net.fusejna.DirectoryFiller;
import net.fusejna.StructStat;
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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class JGitFilesystemSubmoduleTest {
    private static final String CLONE_URL = "https://github.com/githubtraining/example-dependency.git";
    private static final File CLONE_DIR = new File(System.getProperty("java.io.tmpdir"), "example-dependency");

    private static final String SUBMODULE_COMMIT_PATH = "/submodule/js/commit/c7/30509025c6e81947102b2d77bc4dc1cade9489";

    private JGitFilesystem fs;

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
        fs = new JGitFilesystem(CLONE_DIR.getAbsolutePath(), false);
    }

    @After
    public void tearDown() throws IOException {
        fs.close();
    }

    @Test
    public void testSimple() {
        ByteBuffer buffer = ByteBuffer.allocate(1000);
        assertEquals(0, fs.readlink("/branch/master", buffer, 1000));

        assertEquals("A commit-ish link should be written to the buffer, but had: " + new String(buffer.array(), 0, buffer.position()),
                1000 - 51, buffer.remaining());
        // e.g. ../commit/43/27273e69afcd040ba1b4d3766ea1f43e0024f3
        String commit = new String(buffer.array(), 0, buffer.position()).substring(2);

        // check type of files
        final StructStat.StatWrapper wrapper = JGitFilesystemTest.getStatsWrapper();
        assertNotNull(wrapper);
        assertEquals(0, fs.getattr(commit + "/css", wrapper));
        assertEquals(NodeType.DIRECTORY, wrapper.type());
        assertEquals(0, fs.getattr(commit + "/css/default.css", wrapper));
        assertEquals(NodeType.FILE, wrapper.type());
        assertEquals(0, fs.getattr(commit + "/js", wrapper));
        assertEquals(NodeType.SYMBOLIC_LINK, wrapper.type());
    }

    @Test
    public void testSubmodulesGitLink() {
        ByteBuffer buffer = ByteBuffer.allocate(1000);

        assertEquals(0, fs.readlink("/branch/master", buffer, 1000));
        assertEquals("A commit-ish link should be written to the buffer, but had: " + new String(buffer.array(), 0, buffer.position()),
                1000 - 51, buffer.remaining());
        // e.g. ../commit/43/27273e69afcd040ba1b4d3766ea1f43e0024f3
        String commit = new String(buffer.array(), 0, buffer.position()).substring(2);

        // check that we can read the gitlink
        buffer = ByteBuffer.allocate(1000);
        assertEquals(0, fs.readlink(commit + "/js", buffer, 1000));
        assertEquals("Incorrect number of bytes written to the buffer", 930, buffer.remaining());

        assertEquals("../../../submodule/js/commit/c3/c588713233609f5bbbb2d9e7f3fb4a660f3f72",
                new String(buffer.array(), 0, buffer.position()));

        StatWrapper stat = JGitFilesystemTest.getStatsWrapper();
        assertNotNull(stat);
        assertEquals(0, fs.getattr("/submodule/js", stat));
        assertEquals(NodeType.DIRECTORY, stat.type());

        assertEquals(0, fs.getattr(SUBMODULE_COMMIT_PATH + "/README.md", stat));
        assertEquals(NodeType.FILE, stat.type());

        assertEquals(100, fs.read(SUBMODULE_COMMIT_PATH + "/app.js", ByteBuffer.allocate(100), 100, 0, null));

        final List<String> filledFiles = new ArrayList<>();
        DirectoryFiller filler = new JGitFilesystemTest.DirectoryFillerImplementation(filledFiles);

        fs.readdir("/submodule/js/", filler);
        assertEquals("[/branch, /commit, /remote, /tag, /submodule, /stash, /stashorig]", filledFiles.toString());

        filledFiles.clear();
        fs.readdir("/submodule/js", filler);
        assertEquals("[/branch, /commit, /remote, /tag, /submodule, /stash, /stashorig]", filledFiles.toString());

        filledFiles.clear();
        fs.readdir(SUBMODULE_COMMIT_PATH, filler);
        assertEquals("[README.md, app.js]",
                filledFiles.toString());

//        assertEquals(0, fs.getattr("/commit/10/180121602b3aa3b7c6b4bdf15878b0e34bc378/js/build.gradle", stat));
//        assertEquals(NodeType.FILE, stat.type());

    }
}
