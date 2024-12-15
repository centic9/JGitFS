package org.dstadler.jgitfs.console;

import jline.console.ConsoleReader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConsoleTest {
    @BeforeAll
    public static void setUpClass() {
        String travis = System.getenv("RUNNING_IN_TRAVIS");
        Assumptions.assumeTrue(travis == null || !travis.equalsIgnoreCase("true"),
                "Disable this test when running on travis");
    }

    @Test
    public void testRunQuit() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("quit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue(outStr.contains("jgitfs>"), "Had: " + outStr);
        assertTrue(outStr.contains("quit"), "Had: " + outStr);
    }

    @Test
    public void testRunEOF() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue(outStr.contains("jgitfs>"), "Had: " + outStr);
        assertFalse(outStr.contains("quit"), "Had: " + outStr);
    }

    @Test
    public void testRunInvalidCommand() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("blabla\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue(outStr.contains("jgitfs>"), "Had: " + outStr);
        assertTrue(outStr.contains("quit"), "Had: " + outStr);
        assertTrue(outStr.contains("mountpoint"), "Had: " + outStr);
        assertTrue(outStr.contains("blabla"), "Had: " + outStr);
    }

    @Test
    public void testRunExit() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("exit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue(outStr.contains("jgitfs>"), "Had: " + outStr);
        assertTrue(outStr.contains("exit"), "Had: " + outStr);
    }

    @Test
    public void testRunHelp() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("help\nquit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue(outStr.contains("jgitfs>"), "Had: " + outStr);
        assertTrue(outStr.contains("help"), "Had: " + outStr);
        assertTrue(outStr.contains("umount"), "Had: " + outStr);
        assertTrue(outStr.contains("exit"), "Had: " + outStr);
        assertTrue(outStr.contains("list"), "Had: " + outStr);
        assertTrue(outStr.contains("mountpoint"), "Had: " + outStr);
        assertTrue(outStr.contains("git-dir"), "Had: " + outStr);
        assertTrue(outStr.contains("cls"), "Had: " + outStr);
        assertTrue(outStr.contains("quit"), "Had: " + outStr);
    }

    @Test
    public void testList() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("list\nexit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue(outStr.contains("jgitfs>"), "Had: " + outStr);
        assertTrue(outStr.contains("list"), "Had: " + outStr);
        assertTrue(outStr.contains("exit"), "Had: " + outStr);
    }

    @Test
    public void testCls() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("cls\nexit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue(outStr.contains("jgitfs>"), "Had: " + outStr);
        assertTrue(outStr.contains("cls"), "Had: " + outStr);
        assertTrue(outStr.contains("exit"), "Had: " + outStr);
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
        assertTrue(outStr.contains("jgitfs>"), "Had: " + outStr);
        assertTrue(outStr.contains("cls"), "Had: " + outStr);
        assertTrue(outStr.contains("exit"), "Had: " + outStr);
    }

    @Test
    public void testMountFails() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("mount\nexit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue(outStr.contains("jgitfs>"), "Had: " + outStr);
        assertTrue(outStr.contains("mount"), "Had: " + outStr);
        assertTrue(outStr.contains("exit"), "Had: " + outStr);
    }

    @Test
    public void testMountAutomount() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("mount test\nexit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue(outStr.contains("jgitfs>"), "Had: " + outStr);
        assertTrue(outStr.contains("mount"), "Had: " + outStr);
        assertTrue(outStr.contains("Could not find git repository at test/.git"), "Had: " + outStr);
        assertTrue(outStr.contains("exit"), "Had: " + outStr);
    }

    @Test
    public void testMountAutomountGit() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("mount test/git\nexit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue(outStr.contains("jgitfs>"), "Had: " + outStr);
        assertTrue(outStr.contains("mount"), "Had: " + outStr);
        assertTrue(outStr.contains("Could not find git repository at test/git/.git"), "Had: " + outStr);
        assertTrue(outStr.contains("exit"), "Had: " + outStr);
    }

    @Test
    public void testMountInvalidDir() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("mount test test\nexit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue(outStr.contains("jgitfs>"), "Had: " + outStr);
        assertTrue(outStr.contains("mount"), "Had: " + outStr);
        assertTrue(outStr.contains("Could not find git repository at test/.git"), "Had: " + outStr);
        assertTrue(outStr.contains("exit"), "Had: " + outStr);
    }

    @Test
    public void testUnmountFails() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("unmount\nexit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue(outStr.contains("jgitfs>"), "Had: " + outStr);
        assertTrue(outStr.contains("unmount"), "Had: " + outStr);
        assertTrue(outStr.contains("exit"), "Had: " + outStr);
    }

    @Test
    public void testUnmount() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("unmount test\nexit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue(outStr.contains("jgitfs>"), "Had: " + outStr);
        assertTrue(outStr.contains("unmount"), "Had: " + outStr);
        assertTrue(outStr.contains("exit"), "Had: " + outStr);
    }

    @Test
    public void testUmount() throws Exception {
        Console console = new Console();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        console.run(new ByteArrayInputStream("umount test\nexit\n".getBytes()), out);
        out.close();

        String outStr = out.toString();
        assertTrue(outStr.contains("jgitfs>"), "Had: " + outStr);
        assertTrue(outStr.contains("umount"), "Had: " + outStr);
        assertTrue(outStr.contains("exit"), "Had: " + outStr);
    }
}
