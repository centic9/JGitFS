package org.dstadler.jgitfs.console;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.Test;

public class ConsoleTest {

	@Test
	public void testRunQuit() throws Exception {
		Console console = new Console();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		console.run(new ByteArrayInputStream("quit\n".getBytes()), out);

		String outStr = new String(out.toByteArray());
		assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
		assertTrue("Had: " + outStr, outStr.contains("quit"));
	}

	@Test
	public void testRunEOF() throws Exception {
		Console console = new Console();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		console.run(new ByteArrayInputStream("".getBytes()), out);

		String outStr = new String(out.toByteArray());
		assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
		assertFalse("Had: " + outStr, outStr.contains("quit"));
	}

	@Test
	public void testRunInvalidCommand() throws Exception {
		Console console = new Console();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		console.run(new ByteArrayInputStream("blabla\n".getBytes()), out);

		String outStr = new String(out.toByteArray());
		assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
		assertTrue("Had: " + outStr, outStr.contains("quit"));
		assertTrue("Had: " + outStr, outStr.contains("mountpoint"));
		assertTrue("Had: " + outStr, outStr.contains("blabla"));
	}

	@Test
	public void testRunExit() throws Exception {
		Console console = new Console();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		console.run(new ByteArrayInputStream("exit\n".getBytes()), out);

		String outStr = new String(out.toByteArray());
		assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
		assertTrue("Had: " + outStr, outStr.contains("exit"));
	}

	@Test
	public void testRunHelp() throws Exception {
		Console console = new Console();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		console.run(new ByteArrayInputStream("help\nquit\n".getBytes()), out);

		String outStr = new String(out.toByteArray());
		assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
		assertTrue("Had: " + outStr, outStr.contains("help"));
		assertTrue("Had: " + outStr, outStr.contains("umount"));
		assertTrue("Had: " + outStr, outStr.contains("exit"));
		assertTrue("Had: " + outStr, outStr.contains("list"));
		assertTrue("Had: " + outStr, outStr.contains("mountpoint"));
		assertTrue("Had: " + outStr, outStr.contains("git-dir"));
		assertTrue("Had: " + outStr, outStr.contains("cls"));
		assertTrue("Had: " + outStr, outStr.contains("quit"));
	}

	@Test
	public void testList() throws Exception {
		Console console = new Console();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		console.run(new ByteArrayInputStream("list\nexit\n".getBytes()), out);

		String outStr = new String(out.toByteArray());
		assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
		assertTrue("Had: " + outStr, outStr.contains("list"));
		assertTrue("Had: " + outStr, outStr.contains("exit"));
	}

	@Test
	public void testCls() throws Exception {
		Console console = new Console();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		console.run(new ByteArrayInputStream("cls\nexit\n".getBytes()), out);

		String outStr = new String(out.toByteArray());
		assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
		assertTrue("Had: " + outStr, outStr.contains("cls"));
		assertTrue("Had: " + outStr, outStr.contains("exit"));
	}

	@Test
	public void testMountFails() throws Exception {
		Console console = new Console();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		console.run(new ByteArrayInputStream("mount\nexit\n".getBytes()), out);

		String outStr = new String(out.toByteArray());
		assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
		assertTrue("Had: " + outStr, outStr.contains("mount"));
		assertTrue("Had: " + outStr, outStr.contains("exit"));
	}

	@Test
	public void testMount() throws Exception {
		Console console = new Console();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		console.run(new ByteArrayInputStream("mount test test\nexit\n".getBytes()), out);

		String outStr = new String(out.toByteArray());
		assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
		assertTrue("Had: " + outStr, outStr.contains("mount"));
		assertTrue("Had: " + outStr, outStr.contains("Could not find git repository at test/.git"));
		assertTrue("Had: " + outStr, outStr.contains("exit"));
	}

	@Test
	public void testUnmountFails() throws Exception {
		Console console = new Console();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		console.run(new ByteArrayInputStream("unmount\nexit\n".getBytes()), out);

		String outStr = new String(out.toByteArray());
		assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
		assertTrue("Had: " + outStr, outStr.contains("unmount"));
		assertTrue("Had: " + outStr, outStr.contains("exit"));
	}

	@Test
	public void testUnmount() throws Exception {
		Console console = new Console();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		console.run(new ByteArrayInputStream("unmount test\nexit\n".getBytes()), out);

		String outStr = new String(out.toByteArray());
		assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
		assertTrue("Had: " + outStr, outStr.contains("unmount"));
		assertTrue("Had: " + outStr, outStr.contains("exit"));
	}

	@Test
	public void testUmount() throws Exception {
		Console console = new Console();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		console.run(new ByteArrayInputStream("umount test\nexit\n".getBytes()), out);

		String outStr = new String(out.toByteArray());
		assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
		assertTrue("Had: " + outStr, outStr.contains("umount"));
		assertTrue("Had: " + outStr, outStr.contains("exit"));
	}
}
