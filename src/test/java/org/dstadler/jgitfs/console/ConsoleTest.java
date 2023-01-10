package org.dstadler.jgitfs.console;

import jline.console.ConsoleReader;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConsoleTest {
    @BeforeClass
    public static void setUpClass() {
        String travis = System.getenv("RUNNING_IN_TRAVIS");
        Assume.assumeTrue("Disable this test when running on travis",
                travis == null || !travis.equalsIgnoreCase("true"));
    }

    @Test
    public void testRunQuit() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("quit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
        assertTrue("Had: " + outStr, outStr.contains("quit"));
    }

    @Test
    public void testRunEOF() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
        assertFalse("Had: " + outStr, outStr.contains("quit"));
    }

    @Test
    public void testRunInvalidCommand() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("blabla\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
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
        out.close();

        String outStr = out.toString();
        assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
        assertTrue("Had: " + outStr, outStr.contains("exit"));
    }

    @Test
    public void testRunHelp() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("help\nquit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
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
        out.close();

        String outStr = out.toString();
        assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
        assertTrue("Had: " + outStr, outStr.contains("list"));
        assertTrue("Had: " + outStr, outStr.contains("exit"));
    }

    @Test
    public void testCls() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("cls\nexit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
        assertTrue("Had: " + outStr, outStr.contains("cls"));
        assertTrue("Had: " + outStr, outStr.contains("exit"));
    }

    // try to narrow down a test-failure in travis
    @Test
    public void testClsTravis() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ConsoleReader reader = new ConsoleReader("JGitFS", new ByteArrayInputStream("cls\nexit\n".getBytes()), out, null)) {
            reader.setPrompt("jgitfs> ");

            String line;
            //PrintWriter out = new PrintWriter(reader.getOutput());

            while ((line = reader.readLine()) != null) {
                System.out.println("Had line: " + line);
                if (line.equalsIgnoreCase("cls")) {
                    reader.clearScreen();
                }
            }

            // ensure that all content is written to the screen at the end to make unit tests stable
            reader.flush();
        }

        out.close();

        String outStr = out.toString();
        assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
        assertTrue("Had: " + outStr, outStr.contains("cls"));
        assertTrue("Had: " + outStr, outStr.contains("exit"));
    }

    @Test
    public void testMountFails() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("mount\nexit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
        assertTrue("Had: " + outStr, outStr.contains("mount"));
        assertTrue("Had: " + outStr, outStr.contains("exit"));
    }

    @Test
    public void testMountAutomount() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("mount test\nexit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
        assertTrue("Had: " + outStr, outStr.contains("mount"));
        assertTrue("Had: " + outStr, outStr.contains("Could not find git repository at test/.git"));
        assertTrue("Had: " + outStr, outStr.contains("exit"));
    }

    @Test
    public void testMountAutomountGit() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("mount test/git\nexit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
        assertTrue("Had: " + outStr, outStr.contains("mount"));
        assertTrue("Had: " + outStr, outStr.contains("Could not find git repository at test/git/.git"));
        assertTrue("Had: " + outStr, outStr.contains("exit"));
    }

    @Test
    public void testMountInvalidDir() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("mount test test\nexit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
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
        out.close();

        String outStr = out.toString();
        assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
        assertTrue("Had: " + outStr, outStr.contains("unmount"));
        assertTrue("Had: " + outStr, outStr.contains("exit"));
    }

    @Test
    public void testUnmount() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("unmount test\nexit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
        assertTrue("Had: " + outStr, outStr.contains("unmount"));
        assertTrue("Had: " + outStr, outStr.contains("exit"));
    }

    @Test
    public void testUmount() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("umount test\nexit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue("Had: " + outStr, outStr.contains("jgitfs>"));
        assertTrue("Had: " + outStr, outStr.contains("umount"));
        assertTrue("Had: " + outStr, outStr.contains("exit"));
    }
}
