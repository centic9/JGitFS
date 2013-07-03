package org.dstadler.jgitfs;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import net.fusejna.DirectoryFiller;
import net.fusejna.ErrorCodes;
import net.fusejna.FuseException;
import net.fusejna.StatWrapperFactory;
import net.fusejna.StructStat.StatWrapper;
import net.fusejna.types.TypeMode.NodeType;

import org.dstadler.jgitfs.util.FuseUtils;
import org.dstadler.jgitfs.util.JGitHelperTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class JGitFilesystemTest {
	private static final String DEFAULT_COMMIT_SUB = JGitHelperTest.DEFAULT_COMMIT.substring(0,2);
	private static final String DEFAULT_COMMIT_PREFIX = JGitHelperTest.DEFAULT_COMMIT.substring(2);
	private static final String DEFAULT_COMMIT_PATH = "/commit/" + DEFAULT_COMMIT_SUB + "/" + DEFAULT_COMMIT_PREFIX;

	private JGitFilesystem fs;
	
	@Before
	public void setUp() throws IOException {
		fs = new JGitFilesystem(".", false);
	}

	@After
	public void tearDown() throws IOException {
		fs.close();
	}
	@Test
	public void testConstructClose() throws IOException {
		// do nothing here, just construct and close the fs in before/after...
	}

	@Test
	public void testConstructMountClose() throws IOException, FuseException {
		File mountPoint = mount();
		
		unmount(mountPoint);
	}

	@Test
	public void testGetAttr() throws IOException, FuseException {
		File mountPoint = mount();

		StatWrapper stat = StatWrapperFactory.create();
		assertEquals(0, fs.getattr("/", stat));
		assertEquals(NodeType.DIRECTORY, stat.type());
		assertEquals(0, fs.getattr("/commit", stat));
		assertEquals(NodeType.DIRECTORY, stat.type());
		assertEquals(0, fs.getattr("/tag", stat));
		assertEquals(NodeType.DIRECTORY, stat.type());
		assertEquals(0, fs.getattr("/branch", stat));
		assertEquals(NodeType.DIRECTORY, stat.type());
		assertEquals(0, fs.getattr("/commit/0a", stat));
		assertEquals(NodeType.DIRECTORY, stat.type());
		assertEquals(0, fs.getattr(DEFAULT_COMMIT_PATH, stat));
		assertEquals(NodeType.DIRECTORY, stat.type());
		assertEquals(0, fs.getattr(DEFAULT_COMMIT_PATH + "/README.md", stat));
		assertEquals(NodeType.FILE, stat.type());
		assertEquals(0, fs.getattr("/branch/master", stat));
		assertEquals(NodeType.SYMBOLIC_LINK, stat.type());
		assertEquals(0, fs.getattr("/tag/testtag", stat));
		assertEquals(NodeType.SYMBOLIC_LINK, stat.type());

		// invalid file-name causes IllegalStateException
		String path = DEFAULT_COMMIT_PATH + "/notexist.txt";
		try {
			fs.getattr(path, stat);
			fail("Should throw exception as this should not occur");
		} catch (IllegalStateException e) {
			assertTrue(e.toString(), e.toString().contains("Error reading type"));
			assertTrue(e.toString(), e.toString().contains(path));
		}
		// invalid top-level-dir causes ENOENT
		assertEquals(-ErrorCodes.ENOENT(), fs.getattr("/notexistingmain", stat));
		
		unmount(mountPoint);
	}

	@Test
	public void testRead() {
		assertEquals(100, fs.read(DEFAULT_COMMIT_PATH + "/README.md", ByteBuffer.allocate(100), 100, 0, null));
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
			assertTrue(e.toString(), e.toString().contains("exceeds VM limit"));
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
	
	final List<String> filledFiles = new ArrayList<String>();
	DirectoryFiller filler = new DirectoryFiller() {
		
		@Override
		public boolean add(String... files) {
			for(String file : files) {
				filledFiles.add(file);
			}
			return true;
		}
		
		@Override
		public boolean add(Iterable<String> files) {
			for(String file : files) {
				filledFiles.add(file);
			}
			return true;
		}
	};
	
	@Test
	public void testReadDir() {
		fs.readdir("/", filler);
		assertEquals("[/commit, /branch, /tag]", filledFiles.toString());
		
		filledFiles.clear();
		fs.readdir("/tag", filler);
		assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("testtag"));

		filledFiles.clear();
		fs.readdir("/branch", filler);
		assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("master"));

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
	}
	
	@Test
	public void testReadDirPathFails() {
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
		fs.readdir("/tag", filler);
		assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("testtag"));
	}

	@Test
	public void testReadDirTagFails() throws IOException {
		fs.close();
		fs.close();

		// for some reason this does not fail, seems the Git repository still works even if closed
		fs.readdir("/tag", filler);
		assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("testtag"));
	}

	@Test
	public void testReadDirBranch() {
		fs.readdir("/branch", filler);
		assertTrue("Had: " + filledFiles.toString(), filledFiles.contains("master"));
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
		assertEquals(0, fs.readlink("/tag/testtag", buffer, 100));
		
		String target = new String(buffer.array());
		assertTrue("Had: " + target, target.startsWith("../commit"));
	}
	
	@Test
	public void testReadLinkBranch() {
		ByteBuffer buffer = ByteBuffer.allocate(100);
		assertEquals(0, fs.readlink("/branch/master", buffer, 100));
		
		String target = new String(buffer.array());
		assertTrue("Had: " + target, target.startsWith("../commit"));
	}

	@Test
	public void testReadLinkUnknown() {
		try {
			fs.readlink("/branch/notexisting", null, 0);
			fail("Should throw exception as this should not occur");
		} catch (IllegalStateException e) {
			assertTrue(e.toString(), e.toString().contains("Error reading commit"));
			assertTrue(e.toString(), e.toString().contains("/branch/notexisting"));
		}
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

		mountPoint.delete();
	}
}
