package org.dstadler.jgitfs.console;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Arrays;

import jline.console.ConsoleReader;
import jline.console.completer.FileNameCompleter;
import jline.console.completer.StringsCompleter;
import net.fusejna.FuseException;

import org.dstadler.jgitfs.JGitFS;

/**
 * Simple implementation of a console for JGitFS using jline2.
 *
 * @author dominik.stadler
 */
public class Console {

    public void run(final InputStream inStream, final OutputStream outStream) throws IOException {
        try (ConsoleReader reader = new ConsoleReader("JGitFS", inStream, outStream, null)) {
            reader.setPrompt("jgitfs> ");

            reader.addCompleter(new FileNameCompleter());
            reader.addCompleter(new StringsCompleter(Arrays.asList("mount",
                    "unmount",
                    "list",
                    "exit",
                    "quit",
                    "cls")));

            // TODO: the completers do not seem to work, is there more to do to make them work?

            String line;
            PrintWriter out = new PrintWriter(reader.getOutput());

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("mount")) {
                    String[] cmd = line.split("\\s+");
                    if (cmd.length < 2) {
                        out.println("Invalid command");
                        help(out);
                    } else if (cmd.length == 2) {
                        // try to find mountpoint automatically with only one argument

                        String name = new File(cmd[1]).getName();
                        if ("git".equals(name)) {
                            // use a different name than "git" for automatically finding the name
                            // for the mount
                            name = new File(cmd[1]).getParentFile().getName();
                        }

                        out.println("Using mountpoint " + "/fs/" + name + " for repository at " + cmd[1]);
                        try {
                            JGitFS.mount(cmd[1], new File("/fs/" + name));
                        } catch (IllegalArgumentException | IllegalStateException | IOException | FuseException e) {
                            e.printStackTrace(out);
                        }
                    } else {
                        //out.println("Mounting " + cmd[1] + " at " + cmd[2]);
                        try {
                            JGitFS.mount(cmd[1], new File(cmd[2]));
                        } catch (IllegalArgumentException | IllegalStateException | IOException | FuseException e) {
                            e.printStackTrace(out);
                        }
                    }
                } else if (line.startsWith("unmount") || line.startsWith("umount")) {
                    String[] cmd = line.split("\\s+");
                    if (cmd.length < 2) {
                        out.println("Invalid command");
                        help(out);
                    } else {
                        //out.println("Unmounting " + cmd[1]);
                        try {
                            JGitFS.unmount(cmd[1]);
                        } catch (IOException e) {
                            e.printStackTrace(out);
                        }
                    }
                } else if (line.startsWith("list")) {
                    JGitFS.list();
                } else if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                    break;
                } else if (line.equalsIgnoreCase("cls")) {
                    reader.clearScreen();
                } else {
                    help(out);
                }
            }

            // ensure that all content is written to the screen at the end to make unit tests stable
            reader.flush();
        }
    }

    private void help(PrintWriter out) {
        out.println("mount <git-dir> <mountpoint>");
        out.println("umount <git-dir>|<mountpoint>");
        out.println("list ... list current mounts");
        out.println("quit ... quit the application");
        out.println("exit ... quit the application");
        out.println("cls  ... clear the screen");
    }
}
