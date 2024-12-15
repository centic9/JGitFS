package org.dstadler.jgit;

import net.fusejna.StructStat.StatWrapper;
import org.dstadler.jgitfs.JGitFilesystemTest;
import org.dstadler.jgitfs.util.JGitHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;


public class ValidateGitRepository {

    public static void main(String[] args) throws IOException {
        final StatWrapper wrapper = JGitFilesystemTest.getStatsWrapper();
        long count = 0;
        for (String arg : args) {
            JGitHelper jgitHelper = new JGitHelper(arg);

            Set<String> allCommitSubs = jgitHelper.allCommitSubs();
            System.out.println("Found " + allCommitSubs.size() + " subs");
            Collection<String> allCommits = jgitHelper.allCommits(null);
            System.out.println("Found " + allCommits.size() + " commits");
            for (String commit : allCommits) {
                // commit and sub match
                assertTrue(allCommitSubs.contains(commit.substring(0, 2)));

                // list all files recursively
                count = readRecursive(wrapper, count, jgitHelper, commit, "");
            }
            //count = readRecursive(wrapper, count, jgitHelper, "38fb4462b297c4b0ff0f107912be8e6a36b08ff0", "");

            System.out.println("Directory " + arg + " validated");
            jgitHelper.close();
        }
    }

    private static long readRecursive(final StatWrapper wrapper, long count, JGitHelper jgitHelper, String commit, String path) throws IOException {
        long lCount = count;
        List<String> items = jgitHelper.readElementsAt(commit, path);
        //System.out.println("Found " + items.size() + " items in commit " + commit);
        for (String item : items) {
            jgitHelper.readType(commit, path + item, wrapper);
            switch (wrapper.type()) {
                case FILE:
                    InputStream stream = jgitHelper.openFile(commit, path + item);
                    stream.close();
                    break;
                case SYMBOLIC_LINK:
                    jgitHelper.readSymlink(commit, path + item);
                    break;
                case DIRECTORY:
                    lCount += readRecursive(wrapper, count, jgitHelper, commit, path + item + "/");
                    break;
                default:
                    throw new IllegalStateException("Had unknown type: " + wrapper.type());
            }
            System.out.print(".");
            if (lCount % 100 == 0) {
                System.out.println(lCount);
            }
            lCount++;
        }
        return lCount;
    }
}
