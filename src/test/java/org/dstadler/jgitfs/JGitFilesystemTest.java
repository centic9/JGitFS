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
import org.dstadler.commons.testing.ThreadTestHelper;
import org.dstadler.jgitfs.util.FuseUtils;
import org.dstadler.jgitfs.util.JGitHelper;
import org.dstadler.jgitfs.util.JGitHelperTest;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.*;

import java.io.File;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;


public class JGitFilesystemTest {
	private static final String DEFAULT_COMMIT_SUB = JGitHelperTest.DEFAULT_COMMIT.substring(0,2);
	private static final String DEFAULT_COMMIT_PREFIX = JGitHelperTest.DEFAULT_COMMIT.substring(2);
	private static final String DEFAULT_COMMIT_PATH = "/commit/" + DEFAULT_COMMIT_SUB + "/" + DEFAULT_COMMIT_PREFIX;

	private static final String SUBMODULE_COMMIT_PATH = "/submodule/fuse-jna/commit/0f/a3ca5246abc9553f97212232b07ea76bf74596";

	private JGitFilesystem fs;

    private static boolean hasStashes = false;

	@BeforeClass
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

	@Before
	public void setUp() throws IOException {
		fs = new JGitFilesystem(".", false);
	}

	@After
	public void tearDown() throws IOException {
		fs.close();
	}

	@Test
	public void testConstructClose() {
		// do nothing here, just construct and close the fs in before/after...
	}

	@Test
	public void testConstructMountClose() throws UnsatisfiedLinkError, IOException, FuseException {
		Assume.assumeFalse("Mounting the filesystem does not work on Windows", SystemUtils.IS_OS_WINDOWS);

		// ensure that we can actually load FUSE-binaries before we try to mount/unmount
		// an assumption will fail if the binaries are missing
		assertNotNull(getStatsWrapper());

		File mountPoint = mount();

		unmount(mountPoint);
	}

	@Test
    public void testGetStats() {
        assertTrue("Had: " + fs.getStats(), fs.getStats().toString().contains("getattr,0"));
        assertTrue("Had: " + fs.getStats(), fs.getStats().toString().contains("read,0"));
        assertTrue("Had: " + fs.getStats(), fs.getStats().toString().contains("readdir,0"));
        assertTrue("Had: " + fs.getStats(), fs.getStats().toString().contains("readlink,0"));

		StatWrapper stat = getStatsWrapper();
		assertNotNull(stat);
        fs.getattr("", null);
		assertTrue("Had: " + fs.getStats(), fs.getStats().toString().contains("getattr,1"));
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
        
        assertFalse("Had: " + fs.getStats(), fs.getStats().toString().contains("getattr,0"));
	}

	@Test
	public void testRead() {
		assertEquals(100, fs.read(DEFAULT_COMMIT_PATH + "/README.md", ByteBuffer.allocate(100), 100, 0, null));

        assertFalse("Had: " + fs.getStats(), fs.getStats().toString().contains("read,0"));
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
			assertTrue(e.toString(),
					e.toString().contains("exceeds VM limit") ||
					e.toString().contains("Java heap space"));
		}
	}

	@Test
	public void testReadFails() {
		try {
			fs.read("/somepath", null, 0, 0, null);
			fail("Should throw exception as this should not occur");
		} catch (IllegalStateException e) {
			assertTrue(e.toString(), e.toString().contains("Error reading contents"));
			assertTrue(e.toString(), e.toString().contains("/somepath"));
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
		assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("testtag"));

		filledFiles.clear();
		fs.readdir("/branch", filler);
		assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("master"));
		assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("refs_heads_master"));

		filledFiles.clear();
		fs.readdir("/remote", filler);
		assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("origin_master"));
		assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("refs_remotes_origin_master"));

		if(hasStashes) {
	        filledFiles.clear();
	        fs.readdir("/stash", filler);
	        assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("stash@{0}"));

	        filledFiles.clear();
	        fs.readdir("/stashorig", filler);
	        assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("stash@{0}"));
		}

        filledFiles.clear();
		fs.readdir("/commit", filler);
		assertTrue("Had: " + filledFiles.toString(), filledFiles.contains(DEFAULT_COMMIT_SUB));

		filledFiles.clear();
		fs.readdir("/commit/" + DEFAULT_COMMIT_SUB, filler);
		assertTrue("Had: " + filledFiles.toString(), filledFiles.contains(DEFAULT_COMMIT_PREFIX));

		filledFiles.clear();
		fs.readdir(DEFAULT_COMMIT_PATH, filler);
		assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("README.md"));

		filledFiles.clear();
		fs.readdir(DEFAULT_COMMIT_PATH + "/src", filler);
		assertEquals("Had: " + filledFiles.toString(), "[main, test]", filledFiles.toString());

        filledFiles.clear();
        fs.readdir("/submodule", filler);
        assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("fuse-jna"));

        assertFalse("Had: " + fs.getStats(), fs.getStats().toString().contains("readdir,0"));
	}

	@Test
	public void testReadDirPathFails() {
		final List<String> filledFiles = new ArrayList<>();
		DirectoryFiller filler = new DirectoryFillerImplementation(filledFiles);

		String path = DEFAULT_COMMIT_PATH + "/notexisting";
		try {
			filledFiles.clear();
			fs.readdir(path, filler);
			fail("Should throw exception as this should not occur");
		} catch (IllegalStateException e) {
			assertTrue(e.toString(), e.toString().contains("Error reading elements of path"));
			assertTrue(e.toString(), e.toString().contains(path));
		}
	}

	@Test
	public void testReadDirTag() {
		final List<String> filledFiles = new ArrayList<>();
		DirectoryFiller filler = new DirectoryFillerImplementation(filledFiles);

		fs.readdir("/tag", filler);
		assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("testtag"));
	}

	@Test
	public void testReadDirTagFails() throws IOException {
		final List<String> filledFiles = new ArrayList<>();
		DirectoryFiller filler = new DirectoryFillerImplementation(filledFiles);

		fs.close();
		fs.close();

		// for some reason this does not fail, seems the Git repository still works even if closed
		fs.readdir("/tag", filler);
		assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("testtag"));
	}

	@Test
	public void testReadDirBranch() throws IOException {
		// the check further down failed in CI, verify that JGitHelper reports the correct ones
		try (JGitHelper helper = new JGitHelper(".")) {
			List<String> branches = helper.getBranches();
			assertTrue("Had: " + branches.toString(), branches.contains("master"));
			assertTrue("Had: " + branches.toString(), branches.contains("refs_heads_master"));
		}

		final List<String> filledFiles = new ArrayList<>();
		DirectoryFiller filler = new DirectoryFillerImplementation(filledFiles);

		fs.readdir("/branch", filler);
		assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("master"));
		assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("refs_heads_master"));
	}

	@Test
	public void testReadDirRemote() {
		final List<String> filledFiles = new ArrayList<>();
		DirectoryFiller filler = new DirectoryFillerImplementation(filledFiles);

		fs.readdir("/remote", filler);
		assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("origin_master"));
		assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("refs_remotes_origin_master"));
	}

    @Test
    public void testReadDirStash() throws IOException {
    	Assume.assumeTrue("Cannot test stashes without having local stashes", hasStashes);

        // the check further down failed in CI, verify that JGitHelper reports the correct ones
        try (JGitHelper helper = new JGitHelper(".")) {
            List<String> stashes = helper.getStashes();
            assertTrue("Had: " + stashes.toString(), stashes.contains("stash@{0}"));
        }

        final List<String> filledFiles = new ArrayList<>();
        DirectoryFiller filler = new DirectoryFillerImplementation(filledFiles);

        fs.readdir("/stash", filler);
        assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("stash@{0}"));
    }

    @Test
    public void testReadDirStashOrig() throws IOException {
    	Assume.assumeTrue("Cannot test stashes without having local stashes", hasStashes);

        // the check further down failed in CI, verify that JGitHelper reports the correct ones
        try (JGitHelper helper = new JGitHelper(".")) {
            List<String> stashes = helper.getStashes();
            assertTrue("Had: " + stashes.toString(), stashes.contains("stash@{0}"));
        }

        final List<String> filledFiles = new ArrayList<>();
        DirectoryFiller filler = new DirectoryFillerImplementation(filledFiles);

        fs.readdir("/stashorig", filler);
        assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("stash@{0}"));
    }

	@Test
	public void testReadDirFails() {
		try {
			fs.readdir("/somepath", null);
			fail("Should throw exception as this should not occur");
		} catch (IllegalStateException e) {
			assertTrue(e.toString(), e.toString().contains("Error reading directories"));
			assertTrue(e.toString(), e.toString().contains("/somepath"));
		}
	}

	@Test
	public void testReadLinkTag() {
		ByteBuffer buffer = ByteBuffer.allocate(100);
		int readlink = fs.readlink("/tag/testtag", buffer, 100);
		assertEquals("Had: " + readlink + ": " + new String(buffer.array()), 0, readlink);

		String target = new String(buffer.array(), 0, buffer.position());
		assertTrue("Had: " + target, target.startsWith("../commit"));

        assertFalse("Had: " + fs.getStats(), fs.getStats().toString().contains("readlink,0"));
	}

	@Test
	public void testReadLinkBranch() {
		ByteBuffer buffer = ByteBuffer.allocate(100);
		int readlink = fs.readlink("/branch/master", buffer, 100);
		assertEquals("Had: " + readlink + ": " + new String(buffer.array()), 0, readlink);

		String target = new String(buffer.array(), 0, buffer.position());
		assertTrue("Had: " + target, target.startsWith("../commit"));
	}

	@Test
	public void testReadLinkRemote() {
		ByteBuffer buffer = ByteBuffer.allocate(100);
		int readlink = fs.readlink("/remote/origin_master", buffer, 100);
		assertEquals("Had: " + readlink + ": " + new String(buffer.array()), 0, readlink);

		String target = new String(buffer.array(), 0, buffer.position());
		assertTrue("Had: " + target, target.startsWith("../commit"));
	}

    @Test
    public void testReadLinkStash() {
    	Assume.assumeTrue("Cannot test stashes without having local stashes", hasStashes);

        ByteBuffer buffer = ByteBuffer.allocate(100);
        int readlink = fs.readlink("/stash/stash@{0}", buffer, 100);
        assertEquals("Had: " + readlink + ": " + new String(buffer.array()), 0, readlink);

        String target = new String(buffer.array(), 0, buffer.position());
        assertTrue("Had: " + target, target.startsWith("../commit"));
    }

    @Test
    public void testReadLinkStashOrig() {
    	Assume.assumeTrue("Cannot test stashes without having local stashes", hasStashes);

        ByteBuffer buffer = ByteBuffer.allocate(100);
        int readlink = fs.readlink("/stashorig/stash@{0}", buffer, 100);
        assertEquals("Had: " + readlink + ": " + new String(buffer.array()), 0, readlink);

        String target = new String(buffer.array(), 0, buffer.position());
        assertTrue("Had: " + target, target.startsWith("../commit"));
    }

	@Test
	public void testReadLinkRemoteFails() {
		ByteBuffer buffer = ByteBuffer.allocate(100);
		int readlink = fs.readlink("/remote/nonexisting_master", buffer, 100);
		assertEquals("Had: " + readlink + ": " + new String(buffer.array()), -ErrorCodes.ENOENT(), readlink);

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
		assertEquals("Had: " + readlink + ": " + new String(buffer.array()), -ErrorCodes.ENOENT(), readlink);

		assertEquals(0, buffer.position());
	}

	@Test
	public void testReadLinkFails() {
		try {
			fs.readlink("/somepath", null, 0);
			fail("Should throw exception as this should not occur");
		} catch (IllegalStateException e) {
			assertTrue(e.toString(), e.toString().contains("Error reading commit"));
			assertTrue(e.toString(), e.toString().contains("/somepath"));
		}
	}

	private File mount() throws IOException, UnsatisfiedLinkError, FuseException {
		File mountPoint = File.createTempFile("git-fs-mount", ".test");
		assertTrue(mountPoint.delete());

		FuseUtils.prepareMountpoint(mountPoint);

		fs.mount(mountPoint, false);

		return mountPoint;
	}

	private void unmount(File mountPoint) throws IOException, FuseException {
		fs.unmount();

		assertTrue(mountPoint.delete());
	}

	public static StatWrapper getStatsWrapper() {
		final StatWrapper wrapper;
		try {
			wrapper = StatWrapperFactory.create();
		} catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
			System.out.println("This might fail on machines without fuse-binaries.");
			e.printStackTrace();
			Assume.assumeNoException(e);	// stop test silently
			return null;
		}
		return wrapper;
	}

	private static final int NUMBER_OF_THREADS = 9;
	private static final int NUMBER_OF_TESTS = 500;

	@Test
	@Ignore("takes too long currently, need to revisit later")
    public void testMultipleThreads() throws Throwable {
        ThreadTestHelper helper =
            new ThreadTestHelper(NUMBER_OF_THREADS, NUMBER_OF_TESTS);

        helper.executeTest(new ThreadTestHelper.TestRunnable() {
            @Override
            public void doEnd(int threadnum) throws Exception {
                // do stuff at the end ...
            }

            @Override
            public void run(int threadnum, int iter) throws Exception {
            	switch (RandomUtils.nextInt(0,6)) {
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
						testReadLinkBranch();
						break;
					case 5:
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

		for(String file : new ArrayList<>(filledFiles)) {
			assertEquals(0, fs.getattr(file, stat));
			assertEquals(0, fs.readdir(file, filler));
		}

		filledFiles.clear();
		assertEquals(0, fs.readdir("/commit", filler));
		for(String file : new ArrayList<>(filledFiles)) {
			assertEquals(0, fs.getattr("/commit/" + file, stat));
			filledFiles.clear();
			assertEquals(0, fs.readdir("/commit/" + file, filler));
			for(String subfile : new ArrayList<>(filledFiles)) {
				assertEquals(0, fs.getattr("/commit/" + file + "/" + subfile, stat));
				filledFiles.clear();
				assertEquals(0, fs.readdir("/commit/" + file + "/" + subfile, filler));
			}
		}

		filledFiles.clear();
		assertEquals(0, fs.readdir("/branch", filler));
		for(String file : new ArrayList<>(filledFiles)) {
			assertEquals(0, fs.getattr("/branch/" + file, stat));
			assertEquals(NodeType.SYMBOLIC_LINK, stat.type());
			//fs.readlink("/branch/" + file, ByteBuffer.allocate(capacity), size)
		}

		filledFiles.clear();
		assertEquals(0, fs.readdir("/remote", filler));
		for(String file : new ArrayList<>(filledFiles)) {
			assertEquals(0, fs.getattr("/remote/" + file, stat));
			assertEquals(NodeType.SYMBOLIC_LINK, stat.type());
			//fs.readlink("/branch/" + file, ByteBuffer.allocate(capacity), size)
		}

		filledFiles.clear();
        assertEquals(0, fs.readdir("/stash", filler));
        for(String file : new ArrayList<>(filledFiles)) {
            assertEquals(0, fs.getattr("/stash/" + file, stat));
            assertEquals(NodeType.SYMBOLIC_LINK, stat.type());
            //fs.readlink("/branch/" + file, ByteBuffer.allocate(capacity), size)
        }

        filledFiles.clear();
        assertEquals(0, fs.readdir("/stashorig", filler));
        for(String file : new ArrayList<>(filledFiles)) {
            assertEquals(0, fs.getattr("/stashorig/" + file, stat));
            assertEquals(NodeType.SYMBOLIC_LINK, stat.type());
            //fs.readlink("/branch/" + file, ByteBuffer.allocate(capacity), size)
        }
	}

	@Test
	public void testWithTestData() {
		ByteBuffer buffer = ByteBuffer.allocate(1000);
		assertEquals(0, fs.readlink("/branch/master", buffer, 1000));
		verifyData(buffer);
    }

    @Test
    public void testSubmodulesGitLink() {
        ByteBuffer buffer = ByteBuffer.allocate(1000);

        assertEquals(0, fs.readlink("/branch/master", buffer, 1000));
        assertEquals("A commit-ish link should be written to the buffer, but had: " + new String(buffer.array(), 0, buffer.position()),
                1000-51, buffer.remaining());
        // e.g. ../commit/43/27273e69afcd040ba1b4d3766ea1f43e0024f3
        String commit = new String(buffer.array(), 0, buffer.position()).substring(2);

        // check that we can read the gitlink
        buffer = ByteBuffer.allocate(1000);
        assertEquals(0, fs.readlink(commit + "/fuse-jna", buffer, 1000));
        assertEquals("Incorrect number of bytes written to the buffer", 924, buffer.remaining());
        assertEquals("../../../submodule/fuse-jna/commit/ec/ed84f81ad6d2547d47384809a789cf9f4ed3d7",
				new String(buffer.array(), 0, buffer.position()));

        StatWrapper stat = getStatsWrapper();
		assertNotNull(stat);
        assertEquals(0, fs.getattr("/submodule/fuse-jna", stat));
        assertEquals(NodeType.DIRECTORY, stat.type());

        assertEquals(0, fs.getattr(SUBMODULE_COMMIT_PATH + "/build.gradle", stat));
        assertEquals(NodeType.FILE, stat.type());

        assertEquals(100, fs.read(SUBMODULE_COMMIT_PATH + "/LICENSE", ByteBuffer.allocate(100), 100, 0, null));

        final List<String> filledFiles = new ArrayList<>();
        DirectoryFiller filler = new DirectoryFillerImplementation(filledFiles);

        fs.readdir("/submodule/fuse-jna/", filler);
        assertEquals("[/branch, /commit, /remote, /tag, /submodule, /stash, /stashorig]", filledFiles.toString());

        filledFiles.clear();
        fs.readdir("/submodule/fuse-jna", filler);
        assertEquals("[/branch, /commit, /remote, /tag, /submodule, /stash, /stashorig]", filledFiles.toString());

        filledFiles.clear();
        fs.readdir(SUBMODULE_COMMIT_PATH, filler);
        assertEquals("[.classpath, .gitignore, .project, .settings, .travis.yml, LICENSE, README.md, build.gradle, examples, lib, res, src]",
                filledFiles.toString());

//        assertEquals(0, fs.getattr("/commit/10/180121602b3aa3b7c6b4bdf15878b0e34bc378/fuse-jna/build.gradle", stat));
//        assertEquals(NodeType.FILE, stat.type());

    }

	@Test
	public void testWithTestDataRemote() {
		ByteBuffer buffer = ByteBuffer.allocate(1000);
		assertEquals(0, fs.readlink("/remote/origin_master", buffer, 1000));
        verifyData(buffer);
	}

    @Test
    public void testStashWithTestData() {
    	Assume.assumeTrue("Cannot test stashes without having local stashes", hasStashes);

        ByteBuffer buffer = ByteBuffer.allocate(1000);
        assertEquals(0, fs.readlink("/stash/stash@{0}", buffer, 1000));
        verifyData(buffer);
    }

    @Test
    public void testStashOrigWithTestData() {
    	Assume.assumeTrue("Cannot test stashes without having local stashes", hasStashes);

        ByteBuffer buffer = ByteBuffer.allocate(1000);
        assertEquals(0, fs.readlink("/stashorig/stash@{0}", buffer, 1000));
        verifyData(buffer);
    }

    private void verifyData(ByteBuffer bufferIn) {
        ByteBuffer buffer = bufferIn;
        assertEquals("A commit-ish link should be written to the buffer, but had: " + new String(buffer.array(), 0, buffer.position()),
                1000-51, buffer.remaining());
        // e.g. ../commit/43/27273e69afcd040ba1b4d3766ea1f43e0024f3
        String commit = new String(buffer.array(), 0, buffer.position()).substring(2);

        // check that the test-data is there
        final List<String> filledFiles = new ArrayList<>();
        DirectoryFiller filler = new DirectoryFillerImplementation(filledFiles);
        assertEquals(0, fs.readdir(commit + "/src/test/data", filler));
        assertEquals("Had: " + filledFiles, 4, filledFiles.size());
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
        assertEquals(0, fs.getattr(commit + "/fuse-jna", wrapper));
        assertEquals(NodeType.SYMBOLIC_LINK, wrapper.type());

        // check that the empty file is actually empty
        buffer = ByteBuffer.allocate(1000);
        assertEquals(0, fs.read(commit + "/src/test/data/emptytestfile", buffer, 1000, 0, null));
        assertEquals("No data should be written to the buffer", 1000, buffer.remaining());

        // check that the file has the correct content
        buffer = ByteBuffer.allocate(1000);
        assertEquals(2, fs.read(commit + "/src/test/data/one", buffer, 1000, 0, null));
        assertEquals("Only two bytes should be written to the buffer", 998, buffer.remaining());
        assertEquals("1", new String(buffer.array(), 0, 1));

        // check that we can read the symlink
        buffer = ByteBuffer.allocate(1000);
        assertEquals(3, fs.read(commit + "/src/test/data/symlink", buffer, 1000, 0, null));
        assertEquals("Three bytes should be written to the buffer", 997, buffer.remaining());
        assertEquals("one", new String(buffer.array(), 0, buffer.position()));

        buffer = ByteBuffer.allocate(1000);
        assertEquals(21, fs.read(commit + "/src/test/data/rellink", buffer, 1000, 0, null));
        assertEquals("21 bytes should be written to the buffer", 979, buffer.remaining());
        assertEquals("../../../build.gradle", new String(buffer.array(), 0, buffer.position()));

        // reading the link-target of symlinks should return the correct link
        buffer = ByteBuffer.allocate(1000);
        assertEquals(0, fs.readlink(commit + "/src/test/data/symlink", buffer, 1000));
        assertEquals("Three bytes should be written to the buffer", 997, buffer.remaining());
        assertEquals("one", new String(buffer.array(), 0, buffer.position()));

        buffer = ByteBuffer.allocate(1000);
        assertEquals(0, fs.readlink(commit + "/src/test/data/rellink", buffer, 1000));
        assertEquals("21 bytes should be written to the buffer", 979, buffer.remaining());
        assertEquals("../../../build.gradle", new String(buffer.array(), 0, buffer.position()));
    }

    private final class DirectoryFillerImplementation implements DirectoryFiller {
		private final List<String> filledFiles;

		private DirectoryFillerImplementation(List<String> filledFiles) {
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
