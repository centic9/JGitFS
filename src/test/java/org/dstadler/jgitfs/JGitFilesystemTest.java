package org.dstadler.jgitfs;

import com.google.common.collect.Iterables;
import net.fusejna.DirectoryFiller;
import net.fusejna.ErrorCodes;
import net.fusejna.FuseException;
import net.fusejna.StatWrapperFactory;
import net.fusejna.StructStat.StatWrapper;
import net.fusejna.types.TypeMode.NodeType;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.dstadler.commons.testing.ThreadTestHelper;
import org.dstadler.jgitfs.util.FuseUtils;
import org.dstadler.jgitfs.util.JGitHelper;
import org.dstadler.jgitfs.util.JGitHelperTest;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


public class JGitFilesystemTest {
    private static final String DEFAULT_COMMIT_SUB = JGitHelperTest.DEFAULT_COMMIT.substring(0, 2);
    private static final String DEFAULT_COMMIT_PREFIX = JGitHelperTest.DEFAULT_COMMIT.substring(2);
    private static final String DEFAULT_COMMIT_PATH = "/commit/" + DEFAULT_COMMIT_SUB + "/" + DEFAULT_COMMIT_PREFIX;

    private JGitFilesystem fs;

    private static boolean hasStashes = false;

    @BeforeAll
    public static void setUpClass() throws GitAPIException, IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder.setGitDir(new File(".git"))
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build()) {
            try (Git git = new Git(repository)) {
                hasStashes = !git.stashList().call().isEmpty();
            }
        }
    }

    @BeforeEach
    public void setUp() throws IOException {
        fs = new JGitFilesystem(".", false);
    }

    @AfterEach
    public void tearDown() throws IOException {
        fs.close();
    }

    @Test
    public void testConstructClose() {
        // do nothing here, just construct and close the fs in before/after...
    }

    @Test
    public void testConstructMountClose() throws Exception {
        Assumptions.assumeFalse(SystemUtils.IS_OS_WINDOWS, "Mounting the filesystem does not work on Windows");

        assertFalse(fs.isMounted(),
                "Not nounted at start");

        // ensure that we can actually load FUSE-binaries before we try to mount/unmount
        // an assumption will fail if the binaries are missing
        assertNotNull(getStatsWrapper());

        assertFalse(fs.isMounted(),
                "Not mounted after getStatsWrapper");

        File mountPoint = mount();

        assertTrue(fs.isMounted(),
                "Mounted after calling mount");

        unmount(mountPoint);

        assertFalse(fs.isMounted(),
                "Not mounted any more after calling unmount");
    }

    @Test
    public void testGetStats() {
        assertTrue(fs.getStats().toString().contains("getattr,0"), "Had: " + fs.getStats());
        assertTrue(fs.getStats().toString().contains("read,0"), "Had: " + fs.getStats());
        assertTrue(fs.getStats().toString().contains("readdir,0"), "Had: " + fs.getStats());
        assertTrue(fs.getStats().toString().contains("readlink,0"), "Had: " + fs.getStats());

        StatWrapper stat = getStatsWrapper();
        assertNotNull(stat);
        fs.getattr("", null);
        assertTrue(fs.getStats().toString().contains("getattr,1"), "Had: " + fs.getStats());
    }

    @Test
    public void testGetAttr() {
        StatWrapper stat = getStatsWrapper();
        assertNotNull(stat);
        assertEquals(0, fs.getattr("/", stat));
        assertEquals(NodeType.DIRECTORY, stat.type());
        assertEquals(0, fs.getattr("/commit", stat));
        assertEquals(NodeType.DIRECTORY, stat.type());
        assertEquals(0, fs.getattr("/tag", stat));
        assertEquals(NodeType.DIRECTORY, stat.type());
        assertEquals(0, fs.getattr("/branch", stat));
        assertEquals(NodeType.DIRECTORY, stat.type());
        assertEquals(0, fs.getattr("/remote", stat));
        assertEquals(NodeType.DIRECTORY, stat.type());
        assertEquals(0, fs.getattr("/stash", stat));
        assertEquals(NodeType.DIRECTORY, stat.type());
        assertEquals(0, fs.getattr("/stashorig", stat));
        assertEquals(NodeType.DIRECTORY, stat.type());
        assertEquals(0, fs.getattr("/commit/0a", stat));
        assertEquals(NodeType.DIRECTORY, stat.type());
        assertEquals(0, fs.getattr(DEFAULT_COMMIT_PATH, stat));
        assertEquals(NodeType.DIRECTORY, stat.type());
        assertEquals(0, fs.getattr(DEFAULT_COMMIT_PATH + "/README.md", stat));
        assertEquals(NodeType.FILE, stat.type());
        assertEquals(0, fs.getattr("/branch/master", stat));
        assertEquals(NodeType.SYMBOLIC_LINK, stat.type());
        assertEquals(0, fs.getattr("/stash/stash@{0}", stat));
        assertEquals(NodeType.SYMBOLIC_LINK, stat.type());
        assertEquals(0, fs.getattr("/stashorig/stash@{0}", stat));
        assertEquals(NodeType.SYMBOLIC_LINK, stat.type());
        assertEquals(0, fs.getattr("/tag/testtag", stat));
        assertEquals(NodeType.SYMBOLIC_LINK, stat.type());
        assertEquals(0, fs.getattr("/remote/origin_master", stat));
        assertEquals(NodeType.SYMBOLIC_LINK, stat.type());

        // invalid file-name causes IllegalStateException
        String path = DEFAULT_COMMIT_PATH + "/notexist.txt";
        assertEquals(-ErrorCodes.ENOENT(), fs.getattr(path, stat));
        // invalid top-level-dir causes ENOENT
        assertEquals(-ErrorCodes.ENOENT(), fs.getattr("/notexistingmain", stat));

        // hidden dirs are not found and not printed to stdout
        assertEquals(-ErrorCodes.ENOENT(), fs.getattr("/.Trash", stat));
        assertEquals(-ErrorCodes.ENOENT(), fs.getattr("/tag/123/.hidden", stat));
        assertEquals(-ErrorCodes.ENOENT(), fs.getattr("/branch/123/.hidden", stat));
        assertEquals(-ErrorCodes.ENOENT(), fs.getattr("/remote/123/.hidden", stat));
        assertEquals(-ErrorCodes.ENOENT(), fs.getattr("/stash/123/.hidden", stat));
        assertEquals(-ErrorCodes.ENOENT(), fs.getattr("/stashorig/123/.hidden", stat));
        assertEquals(-ErrorCodes.ENOENT(), fs.getattr("/master/some/file/direct/.hidden", stat));

        assertFalse(fs.getStats().toString().contains("getattr,0"), "Had: " + fs.getStats());
    }

    @Test
    public void testRead() {
        assertEquals(100, fs.read(DEFAULT_COMMIT_PATH + "/README.md", ByteBuffer.allocate(100), 100, 0, null));

        assertFalse(fs.getStats().toString().contains("read,0"), "Had: " + fs.getStats());
    }

    @Test
    public void testReadTooMuch() {
        int read = fs.read(DEFAULT_COMMIT_PATH + "/README.md", ByteBuffer.allocate(100000), 100000, 0, null);
        assertEquals(4816, read);
    }

    @Test
    public void testReadWayTooMuch() {
        try {
            fs.read(DEFAULT_COMMIT_PATH + "/README.md", ByteBuffer.allocate(100000), Integer.MAX_VALUE, 0, null);
            fail("Should throw exception as this should not occur");
        } catch (OutOfMemoryError e) {
            assertTrue(e.toString().contains("exceeds VM limit") ||
                            e.toString().contains("Java heap space"),
                    e.toString());
        }
    }

    @Test
    public void testReadFails() {
        try {
            fs.read("/somepath", null, 0, 0, null);
            fail("Should throw exception as this should not occur");
        } catch (IllegalStateException e) {
            assertTrue(e.toString().contains("Error reading contents"), e.toString());
            assertTrue(e.toString().contains("/somepath"), e.toString());
        }
    }

    @Test
    public void testReadDir() {
        final List<String> filledFiles = new ArrayList<>();
        DirectoryFiller filler = new DirectoryFillerImplementation(filledFiles);

        fs.readdir("/", filler);
        assertEquals("[/branch, /commit, /remote, /tag, /submodule, /stash, /stashorig]", filledFiles.toString());

        filledFiles.clear();
        fs.readdir("/tag", filler);
        assertTrue(filledFiles.contains("testtag"), "Had: " + filledFiles);

        filledFiles.clear();
        fs.readdir("/branch", filler);
        assertTrue(filledFiles.contains("master"), "Had: " + filledFiles);
        assertTrue(filledFiles.contains("refs_heads_master"), "Had: " + filledFiles);

        filledFiles.clear();
        fs.readdir("/remote", filler);
        assertTrue(filledFiles.contains("origin_master"), "Had: " + filledFiles);
        assertTrue(filledFiles.contains("refs_remotes_origin_master"), "Had: " + filledFiles);

        if (hasStashes) {
            filledFiles.clear();
            fs.readdir("/stash", filler);
            assertTrue(filledFiles.contains("stash@{0}"), "Had: " + filledFiles);

            filledFiles.clear();
            fs.readdir("/stashorig", filler);
            assertTrue(filledFiles.contains("stash@{0}"), "Had: " + filledFiles);
        }

        filledFiles.clear();
        fs.readdir("/commit", filler);
        assertTrue(filledFiles.contains(DEFAULT_COMMIT_SUB), "Had: " + filledFiles);

        filledFiles.clear();
        fs.readdir("/commit/" + DEFAULT_COMMIT_SUB, filler);
        assertTrue(filledFiles.contains(DEFAULT_COMMIT_PREFIX), "Had: " + filledFiles);

        filledFiles.clear();
        fs.readdir(DEFAULT_COMMIT_PATH, filler);
        assertTrue(filledFiles.contains("README.md"), "Had: " + filledFiles);

        filledFiles.clear();
        fs.readdir(DEFAULT_COMMIT_PATH + "/src", filler);
        assertEquals("[main, test]", filledFiles.toString(), "Had: " + filledFiles);

        filledFiles.clear();
        fs.readdir("/submodule", filler);
        assertTrue(filledFiles.isEmpty(), "Had: " + filledFiles);

        assertFalse(fs.getStats().toString().contains("readdir,0"), "Had: " + fs.getStats());
    }

    @Test
    public void testReadDirPathFails() {
        final List<String> filledFiles = new ArrayList<>();
        DirectoryFiller filler = new DirectoryFillerImplementation(filledFiles);

        String path = DEFAULT_COMMIT_PATH + "/notexisting";
        try {
            fs.readdir(path, filler);
            fail("Should throw exception as this should not occur");
        } catch (IllegalStateException e) {
            assertTrue(e.toString().contains("Error reading elements of path"), e.toString());
            assertTrue(e.toString().contains(path), e.toString());
        }
    }

    @Test
    public void testReadDirTag() {
        final List<String> filledFiles = new ArrayList<>();
        DirectoryFiller filler = new DirectoryFillerImplementation(filledFiles);

        fs.readdir("/tag", filler);
        assertTrue(filledFiles.contains("testtag"), "Had: " + filledFiles);
    }

    @Test
    public void testReadDirTagFails() throws IOException {
        final List<String> filledFiles = new ArrayList<>();
        DirectoryFiller filler = new DirectoryFillerImplementation(filledFiles);

        fs.close();
        fs.close();

        // for some reason this does not fail, seems the Git repository still works even if closed
        fs.readdir("/tag", filler);
        assertTrue(filledFiles.contains("testtag"), "Had: " + filledFiles);
    }

    @Test
    public void testReadDirBranch() throws IOException {
        // the check further down failed in CI, verify that JGitHelper reports the correct ones
        try (JGitHelper helper = new JGitHelper(".")) {
            List<String> branches = helper.getBranches();
            assertTrue(branches.contains("master"), "Had: " + branches);
            assertTrue(branches.contains("refs_heads_master"), "Had: " + branches);
        }

        final List<String> filledFiles = new ArrayList<>();
        DirectoryFiller filler = new DirectoryFillerImplementation(filledFiles);

        fs.readdir("/branch", filler);
        assertTrue(filledFiles.contains("master"), "Had: " + filledFiles);
        assertTrue(filledFiles.contains("refs_heads_master"), "Had: " + filledFiles);
    }

    @Test
    public void testReadDirRemote() {
        final List<String> filledFiles = new ArrayList<>();
        DirectoryFiller filler = new DirectoryFillerImplementation(filledFiles);

        fs.readdir("/remote", filler);
        assertTrue(filledFiles.contains("origin_master"), "Had: " + filledFiles);
        assertTrue(filledFiles.contains("refs_remotes_origin_master"), "Had: " + filledFiles);
    }

    @Test
    public void testReadDirStash() throws IOException {
        Assumptions.assumeTrue(hasStashes, "Cannot test stashes without having local stashes");

        // the check further down failed in CI, verify that JGitHelper reports the correct ones
        try (JGitHelper helper = new JGitHelper(".")) {
            List<String> stashes = helper.getStashes();
            assertTrue(stashes.contains("stash@{0}"), "Had: " + stashes);
        }

        final List<String> filledFiles = new ArrayList<>();
        DirectoryFiller filler = new DirectoryFillerImplementation(filledFiles);

        fs.readdir("/stash", filler);
        assertTrue(filledFiles.contains("stash@{0}"), "Had: " + filledFiles);
    }

    @Test
    public void testReadDirStashOrig() throws IOException {
        Assumptions.assumeTrue(hasStashes, "Cannot test stashes without having local stashes");

        // the check further down failed in CI, verify that JGitHelper reports the correct ones
        try (JGitHelper helper = new JGitHelper(".")) {
            List<String> stashes = helper.getStashes();
            assertTrue(stashes.contains("stash@{0}"), "Had: " + stashes);
        }

        final List<String> filledFiles = new ArrayList<>();
        DirectoryFiller filler = new DirectoryFillerImplementation(filledFiles);

        fs.readdir("/stashorig", filler);
        assertTrue(filledFiles.contains("stash@{0}"), "Had: " + filledFiles);
    }

    @Test
    public void testReadDirFails() {
        try {
            fs.readdir("/somepath", null);
            fail("Should throw exception as this should not occur");
        } catch (IllegalStateException e) {
            assertTrue(e.toString().contains("Error reading directories"), e.toString());
            assertTrue(e.toString().contains("/somepath"), e.toString());
        }
    }

    @Test
    public void testReadLinkTag() {
        ByteBuffer buffer = ByteBuffer.allocate(100);
        int readlink = fs.readlink("/tag/testtag", buffer, 100);
        assertEquals(0, readlink, "Had: " + readlink + ": " + new String(buffer.array()));

        String target = new String(buffer.array(), 0, buffer.position());
        assertTrue(target.startsWith("../commit"), "Had: " + target);

        assertFalse(fs.getStats().toString().contains("readlink,0"), "Had: " + fs.getStats());
    }

    @Test
    public void testReadLinkBranch() {
        ByteBuffer buffer = ByteBuffer.allocate(100);
        int readlink = fs.readlink("/branch/master", buffer, 100);
        assertEquals(0, readlink, "Had: " + readlink + ": " + new String(buffer.array()));

        String target = new String(buffer.array(), 0, buffer.position());
        assertTrue(target.startsWith("../commit"), "Had: " + target);
    }

    @Test
    public void testReadLinkRemote() {
        ByteBuffer buffer = ByteBuffer.allocate(100);
        int readlink = fs.readlink("/remote/origin_master", buffer, 100);
        assertEquals(0, readlink, "Had: " + readlink + ": " + new String(buffer.array()));

        String target = new String(buffer.array(), 0, buffer.position());
        assertTrue(target.startsWith("../commit"), "Had: " + target);
    }

    @Test
    public void testReadLinkStash() {
        Assumptions.assumeTrue(hasStashes, "Cannot test stashes without having local stashes");

        ByteBuffer buffer = ByteBuffer.allocate(100);
        int readlink = fs.readlink("/stash/stash@{0}", buffer, 100);
        assertEquals(0, readlink, "Had: " + readlink + ": " + new String(buffer.array()));

        String target = new String(buffer.array(), 0, buffer.position());
        assertTrue(target.startsWith("../commit"), "Had: " + target);
    }

    @Test
    public void testReadLinkStashOrig() {
        Assumptions.assumeTrue(hasStashes, "Cannot test stashes without having local stashes");

        ByteBuffer buffer = ByteBuffer.allocate(100);
        int readlink = fs.readlink("/stashorig/stash@{0}", buffer, 100);
        assertEquals(0, readlink, "Had: " + readlink + ": " + new String(buffer.array()));

        String target = new String(buffer.array(), 0, buffer.position());
        assertTrue(target.startsWith("../commit"), "Had: " + target);
    }

    @Test
    public void testReadLinkRemoteFails() {
        ByteBuffer buffer = ByteBuffer.allocate(100);
        int readlink = fs.readlink("/remote/nonexisting_master", buffer, 100);
        assertEquals(-ErrorCodes.ENOENT(), readlink, "Had: " + readlink + ": " + new String(buffer.array()));

        assertEquals(0, buffer.position());
    }

    @Test
    public void testReadLinkExceedSize() {
        ByteBuffer buffer = ByteBuffer.allocate(21);
        try {
            fs.readlink("/tag/testtag", buffer, 21);
            fail("Should catch exception here");
        } catch (@SuppressWarnings("unused") BufferOverflowException e) {
            // expected...
        }
    }

    @Test
    public void testReadLinkDifferentSize() {
        ByteBuffer buffer = ByteBuffer.allocate(21);
        try {
            fs.readlink("/tag/testtag", buffer, 30);
            fail("Should catch exception here");
        } catch (@SuppressWarnings("unused") BufferOverflowException e) {
            // expected...
        }
    }

    @Test
    public void testReadLinkUnknown() {
        ByteBuffer buffer = ByteBuffer.allocate(100);
        int readlink = fs.readlink("/branch/notexisting", null, 0);
        assertEquals(-ErrorCodes.ENOENT(), readlink, "Had: " + readlink + ": " + new String(buffer.array()));

        assertEquals(0, buffer.position());
    }

    @Test
    public void testReadLinkFails() {
        try {
            fs.readlink("/somepath", null, 0);
            fail("Should throw exception as this should not occur");
        } catch (IllegalStateException e) {
            assertTrue(e.toString().contains("Error reading commit"), e.toString());
            assertTrue(e.toString().contains("/somepath"), e.toString());
        }
    }

    private File mount() throws IOException, UnsatisfiedLinkError, FuseException {
        File mountPoint = File.createTempFile("git-fs-mount", ".test");
        assertTrue(mountPoint.delete());

        FuseUtils.prepareMountpoint(mountPoint);

        fs.mount(mountPoint, false);

        assertTrue(fs.isMounted(),
                "Mounted after mounting");

        return mountPoint;
    }

    private void unmount(File mountPoint) throws IOException, FuseException, InterruptedException {
        fs.unmount();

        // Fuse invokes netFuseFilesystem._destroy() asynchronously,
        // so we should give it a bit of time until we expect it to have finished
        for (int i = 0; i < 10; i++) {
            if (!fs.isMounted()) {
                break;
            }

            Thread.sleep(100);
        }

        assertFalse(fs.isMounted(),
                "Not mounted any more after calling unmount");

        assertTrue(mountPoint.delete());
    }

    public static StatWrapper getStatsWrapper() {
        final StatWrapper wrapper;
        try {
            wrapper = StatWrapperFactory.create();
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            System.out.println("This might fail on machines without fuse-binaries.");
            e.printStackTrace();
            Assumptions.abort(ExceptionUtils.getStackTrace(e));    // stop test silently
            return null;
        }
        return wrapper;
    }

    private static final int NUMBER_OF_THREADS = 9;
    private static final int NUMBER_OF_TESTS = 500;

    @Test
    @Disabled("takes too long currently, need to revisit later")
    public void testMultipleThreads() throws Throwable {
        ThreadTestHelper helper =
                new ThreadTestHelper(NUMBER_OF_THREADS, NUMBER_OF_TESTS);

        helper.executeTest(new ThreadTestHelper.TestRunnable() {
            @Override
            public void doEnd(int threadnum) {
                // do stuff at the end ...
            }

            @Override
            public void run(int threadnum, int iter) {
                switch (RandomUtils.insecure().randomInt(0, 5)) {
                    case 0:
                        testGetAttr();
                        break;
                    case 1:
                        testRead();
                        break;
                    case 2:
                        testReadDir();
                        break;
                    case 3:
                        testReadLinkBranch();
                        break;
                    case 4:
                        testReadLinkTag();
                        break;
                    /*case 6:
                        //testReadLinkStash();
                        break;
                    case 7:
                        //testReadLinkStashOrig();
                        break;
					case 8:
						//testWalkRecursively();
						break;*/
                    default:
                        throw new IllegalStateException("Invalid random number");
                }
            }
        });
    }

    @Test
    public void testWalkRecursively() {
        StatWrapper stat = getStatsWrapper();
        assertNotNull(stat);
        assertEquals(0, fs.getattr("/", stat));

        final List<String> filledFiles = new ArrayList<>();
        DirectoryFiller filler = new DirectoryFillerImplementation(filledFiles);

        assertEquals(0, fs.readdir("/", filler));
        assertEquals("[/branch, /commit, /remote, /tag, /submodule, /stash, /stashorig]", filledFiles.toString());

        for (String file : new ArrayList<>(filledFiles)) {
            assertEquals(0, fs.getattr(file, stat));
            assertEquals(0, fs.readdir(file, filler));
        }

        filledFiles.clear();
        assertEquals(0, fs.readdir("/commit", filler));
        for (String file : new ArrayList<>(filledFiles)) {
            assertEquals(0, fs.getattr("/commit/" + file, stat));
            filledFiles.clear();
            assertEquals(0, fs.readdir("/commit/" + file, filler));
            for (String subfile : new ArrayList<>(filledFiles)) {
                assertEquals(0, fs.getattr("/commit/" + file + "/" + subfile, stat));
                filledFiles.clear();
                assertEquals(0, fs.readdir("/commit/" + file + "/" + subfile, filler));
            }
        }

        filledFiles.clear();
        assertEquals(0, fs.readdir("/branch", filler));
        for (String file : new ArrayList<>(filledFiles)) {
            assertEquals(0, fs.getattr("/branch/" + file, stat));
            assertEquals(NodeType.SYMBOLIC_LINK, stat.type());
            //fs.readlink("/branch/" + file, ByteBuffer.allocate(capacity), size)
        }

        filledFiles.clear();
        assertEquals(0, fs.readdir("/remote", filler));
        for (String file : new ArrayList<>(filledFiles)) {
            assertEquals(0, fs.getattr("/remote/" + file, stat));
            assertEquals(NodeType.SYMBOLIC_LINK, stat.type());
            //fs.readlink("/branch/" + file, ByteBuffer.allocate(capacity), size)
        }

        filledFiles.clear();
        assertEquals(0, fs.readdir("/stash", filler));
        for (String file : new ArrayList<>(filledFiles)) {
            assertEquals(0, fs.getattr("/stash/" + file, stat));
            assertEquals(NodeType.SYMBOLIC_LINK, stat.type());
            //fs.readlink("/branch/" + file, ByteBuffer.allocate(capacity), size)
        }

        filledFiles.clear();
        assertEquals(0, fs.readdir("/stashorig", filler));
        for (String file : new ArrayList<>(filledFiles)) {
            assertEquals(0, fs.getattr("/stashorig/" + file, stat));
            assertEquals(NodeType.SYMBOLIC_LINK, stat.type());
            //fs.readlink("/branch/" + file, ByteBuffer.allocate(capacity), size)
        }
    }

    @Test
    public void testWithTestData() {
        ByteBuffer buffer = ByteBuffer.allocate(1000);
        assertEquals(0, fs.readlink("/branch/master", buffer, 1000));
        verifyData(buffer, -2);
    }

    @Test
    public void testWithTestDataRemote() {
        ByteBuffer buffer = ByteBuffer.allocate(1000);
        assertEquals(0, fs.readlink("/remote/origin_master", buffer, 1000));
        verifyData(buffer, -2);
    }

    @Test
    public void testStashWithTestData() {
        Assumptions.assumeTrue(hasStashes, "Cannot test stashes without having local stashes");

        ByteBuffer buffer = ByteBuffer.allocate(1000);
        assertEquals(0, fs.readlink("/stash/stash@{0}", buffer, 1000));
        verifyData(buffer, 0);
    }

    @Test
    public void testStashOrigWithTestData() {
        Assumptions.assumeTrue(hasStashes, "Cannot test stashes without having local stashes");

        ByteBuffer buffer = ByteBuffer.allocate(1000);
        assertEquals(0, fs.readlink("/stashorig/stash@{0}", buffer, 1000));
        verifyData(buffer, 0);
    }

    private void verifyData(ByteBuffer bufferIn, int submoduleReturn) {
        ByteBuffer buffer = bufferIn;
        assertEquals(1000 - 51, buffer.remaining(), "A commit-ish link should be written to the buffer, but had: " + new String(buffer.array(), 0, buffer.position()));
        // e.g. ../commit/43/27273e69afcd040ba1b4d3766ea1f43e0024f3
        String commit = new String(buffer.array(), 0, buffer.position()).substring(2);

        // check that the test-data is there
        final List<String> filledFiles = new ArrayList<>();
        DirectoryFiller filler = new DirectoryFillerImplementation(filledFiles);
        assertEquals(0, fs.readdir(commit + "/src/test/data", filler));
        assertEquals(4, filledFiles.size(), "Had: " + filledFiles);
        assertTrue(filledFiles.contains("emptytestfile"));
        assertTrue(filledFiles.contains("one"));
        assertTrue(filledFiles.contains("symlink"));
        assertTrue(filledFiles.contains("rellink"));


        // check type of files
        final StatWrapper wrapper = getStatsWrapper();
        assertNotNull(wrapper);
        assertEquals(0, fs.getattr(commit + "/src/test/data", wrapper));
        assertEquals(NodeType.DIRECTORY, wrapper.type());
        assertEquals(0, fs.getattr(commit + "/src/test/data/emptytestfile", wrapper));
        assertEquals(NodeType.FILE, wrapper.type());
        assertEquals(0, fs.getattr(commit + "/src/test/data/one", wrapper));
        assertEquals(NodeType.FILE, wrapper.type());
        assertEquals(0, fs.getattr(commit + "/src/test/data/symlink", wrapper));
        assertEquals(NodeType.SYMBOLIC_LINK, wrapper.type());
        assertEquals(0, fs.getattr(commit + "/src/test/data/rellink", wrapper));
        assertEquals(NodeType.SYMBOLIC_LINK, wrapper.type());
        assertEquals(submoduleReturn, fs.getattr(commit + "/fuse-jna", wrapper));
        assertEquals(NodeType.SYMBOLIC_LINK, wrapper.type());

        // check that the empty file is actually empty
        buffer = ByteBuffer.allocate(1000);
        assertEquals(0, fs.read(commit + "/src/test/data/emptytestfile", buffer, 1000, 0, null));
        assertEquals(1000, buffer.remaining(), "No data should be written to the buffer");

        // check that the file has the correct content
        buffer = ByteBuffer.allocate(1000);
        assertEquals(2, fs.read(commit + "/src/test/data/one", buffer, 1000, 0, null));
        assertEquals(998, buffer.remaining(), "Only two bytes should be written to the buffer");
        assertEquals("1", new String(buffer.array(), 0, 1));

        // check that we can read the symlink
        buffer = ByteBuffer.allocate(1000);
        assertEquals(3, fs.read(commit + "/src/test/data/symlink", buffer, 1000, 0, null));
        assertEquals(997, buffer.remaining(), "Three bytes should be written to the buffer");
        assertEquals("one", new String(buffer.array(), 0, buffer.position()));

        buffer = ByteBuffer.allocate(1000);
        assertEquals(21, fs.read(commit + "/src/test/data/rellink", buffer, 1000, 0, null));
        assertEquals(979, buffer.remaining(), "21 bytes should be written to the buffer");
        assertEquals("../../../build.gradle", new String(buffer.array(), 0, buffer.position()));

        // reading the link-target of symlinks should return the correct link
        buffer = ByteBuffer.allocate(1000);
        assertEquals(0, fs.readlink(commit + "/src/test/data/symlink", buffer, 1000));
        assertEquals(997, buffer.remaining(), "Three bytes should be written to the buffer");
        assertEquals("one", new String(buffer.array(), 0, buffer.position()));

        buffer = ByteBuffer.allocate(1000);
        assertEquals(0, fs.readlink(commit + "/src/test/data/rellink", buffer, 1000));
        assertEquals(979, buffer.remaining(), "21 bytes should be written to the buffer");
        assertEquals("../../../build.gradle", new String(buffer.array(), 0, buffer.position()));
    }

    protected static final class DirectoryFillerImplementation implements DirectoryFiller {
        private final List<String> filledFiles;

        DirectoryFillerImplementation(List<String> filledFiles) {
            this.filledFiles = filledFiles;
        }

        @Override
        public boolean add(String... files) {
            Collections.addAll(filledFiles, files);
            return true;
        }

        @Override
        public boolean add(Iterable<String> files) {
            assertTrue(Iterables.addAll(filledFiles, files));
            return true;
        }
    }
}
