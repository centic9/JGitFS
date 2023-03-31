package org.dstadler.jgitfs;

import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.cli.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.dstadler.commons.logging.jdk.LoggerFactory;

/**
 * Provide commandline parsing, error messages and getters for
 * the resulting flags/configuration values.
 */
public class Commandline {
    private final static Logger log = LoggerFactory.make();

    private static final String OPTION_NO_CONSOLE = "n";

    public static final String USAGE_TEXT = "JGitFS [<option> ...] <git-repo> [<mountpoint>] ...";

    private final Options cmdLineOptions;

    private boolean noConsole;
    private List<String> argList;

    public Commandline() {
        cmdLineOptions = new Options();

        cmdLineOptions.addOption(
                Option.builder(OPTION_NO_CONSOLE).
                        longOpt("no-console").
                        desc("Do not open the command-console").
                        build());
    }

    public void parse(String[] args) {
        if (ArrayUtils.contains(args, "--help") || ArrayUtils.contains(args, "-h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(USAGE_TEXT, cmdLineOptions);
            throw new SystemExitException(null, 1);
        }

        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine cmdLineParser = parser.parse(cmdLineOptions, args);

            // No args should remain
            argList = cmdLineParser.getArgList();

            if(cmdLineParser.hasOption(OPTION_NO_CONSOLE)) {
                noConsole = true;
            }

            log.info("Having commandline options: " +
                    "no-console: " + noConsole +
                    "remaining: " + argList);
        } catch (Exception e) {
            System.err.println("Error parsing arguments: " + ExceptionUtils.getStackTrace(e));
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(USAGE_TEXT, cmdLineOptions);
            throw new SystemExitException(e, 1);
        }
    }

    public boolean isNoConsole() {
        return noConsole;
    }

    public List<String> getArgList() {
        return argList;
    }

    public static class SystemExitException extends RuntimeException {
        public final int exitCode;

        public SystemExitException(Throwable cause, int exitCode) {
            super(cause);
            this.exitCode = exitCode;
        }

        public int getExitCode() {
            return exitCode;
        }
    }
}
