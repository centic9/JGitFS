package org.dstadler.jgitfs;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.help.HelpFormatter;
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
    private static final String OPTION_TEST_ONLY = "t";

    public static final String USAGE_TEXT = "JGitFS [<option> ...] <git-repo> [<mountpoint>] ...";

    private final Options cmdLineOptions;

    private boolean noConsole;
    private boolean testOnly;
    private List<String> argList;

    public Commandline() {
        cmdLineOptions = new Options();

        cmdLineOptions.addOption(
                Option.builder(OPTION_NO_CONSOLE).
                        longOpt("no-console").
                        desc("Do not open the command-console").
                        get());
        cmdLineOptions.addOption(
                Option.builder(OPTION_TEST_ONLY).
                        longOpt("test-only").
                        desc("Only try to mount and then exit again").
                        get());
    }

    public void parse(String[] args) throws IOException {
        if (ArrayUtils.contains(args, "--help") || ArrayUtils.contains(args, "-h")) {
            HelpFormatter formatter = HelpFormatter.builder().setShowSince(false).get();
            formatter.printHelp(USAGE_TEXT, "", cmdLineOptions, "", true);
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

            if(cmdLineParser.hasOption(OPTION_TEST_ONLY)) {
                testOnly = true;
            }

            log.info("Having commandline options: " +
                    "no-console: " + noConsole +
                    "test-only: " + testOnly +
                    "remaining: " + argList);
        } catch (Exception e) {
            System.err.println("Error parsing arguments: " + ExceptionUtils.getStackTrace(e));
            HelpFormatter formatter = HelpFormatter.builder().setShowSince(false).get();
            formatter.printHelp(USAGE_TEXT, "", cmdLineOptions, "", true);
            throw new SystemExitException(e, 1);
        }
    }

    public boolean isNoConsole() {
        return noConsole;
    }

    public boolean isTestOnly() {
        return testOnly;
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
