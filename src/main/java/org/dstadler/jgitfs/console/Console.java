package org.dstadler.jgitfs.console;

import java.io.File;
import java.io.IOException;
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

    public void run() throws IOException {
        ConsoleReader reader = new ConsoleReader();

        reader.setPrompt("jgitfs> ");

        reader.addCompleter(new FileNameCompleter());
        reader.addCompleter(new StringsCompleter(Arrays.asList(new String[] {
                "mount",
                "unmount",
                "list",
                "exit",
                "quit",
                "cls",
        })));

        String line;
        PrintWriter out = new PrintWriter(reader.getOutput());

        while ((line = reader.readLine()) != null) {
            if (line.startsWith("mount")) {
                String[] cmd = line.split("\\s+");
                if(cmd.length < 3) {
                    out.println("Invalid command");
                    help(out);
                } else {
                    //out.println("Mounting " + cmd[1] + " at " + cmd[2]);
                    try {
                        JGitFS.mount(cmd[1], new File(cmd[2]));
                    } catch (IOException | FuseException e) {
                        e.printStackTrace(out);
                    }
                }
            } else if (line.startsWith("unmount") || line.startsWith("umount")) {
                String[] cmd = line.split("\\s+");
                if(cmd.length < 2) {
                    out.println("Invalid command");
                    help(out);
                } else {
                    //out.println("Umounting " + cmd[1]);
                    try {
                        JGitFS.unmount(cmd[1]);
                    } catch (IOException e) {
                        e.printStackTrace(out);
                    }
                }
            } else if (line.startsWith("list")) {
                JGitFS.list();
            } else {
                help(out);
            }

            if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                break;
            }
            if (line.equalsIgnoreCase("cls")) {
                reader.clearScreen();
            }
        }
    }

    private void help(PrintWriter out) {
        out.println("mount <git-dir> <mountpoint>");
        out.println("umount <git-dir>|<mountpoint>");
        out.println("list ... list current mounts");
        out.println("quit ... quit the applicatoin");
        out.println("exit ... quit the application");
        out.println("cls  ... clear the screen");
    }
}