package org.dstadler.jgit;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import net.fusejna.StatWrapperFactory;
import net.fusejna.StructStat.StatWrapper;

import org.dstadler.jgitfs.util.JGitHelper;
import org.junit.Assume;


public class ValidateGitRepository {

	public static void main(String[] args) throws IOException {
		final StatWrapper wrapper = getStatsWrapper();
		long count = 0;
		for(String arg : args) {
			JGitHelper jgitHelper = new JGitHelper(arg);
			
			Set<String> allCommitSubs = jgitHelper.allCommitSubs();
			System.out.println("Found " + allCommitSubs.size() + " subs");
			Collection<String> allCommits = jgitHelper.allCommits(null);
			System.out.println("Found " + allCommits.size() + " commits");
			for(String commit : allCommits) {
				// commit and sub match
				assertTrue(allCommitSubs.contains(commit.substring(0, 2)));
				
				// list all files recursively
				count = readRecursive(wrapper, count, jgitHelper, commit, "");
			}
			
			System.out.println("Directory " + arg + " validated");
			jgitHelper.close();
		}
	}

	private static long readRecursive(final StatWrapper wrapper, long count, JGitHelper jgitHelper, String commit, String path) throws IOException {
		List<String> items = jgitHelper.readElementsAt(commit, path);
		//System.out.println("Found " + items.size() + " items in commit " + commit);
		for(String item : items) {
			jgitHelper.readType(commit, item, wrapper);
			switch (wrapper.type()) {
				case FILE:
					InputStream stream = jgitHelper.openFile(commit, item);
					stream.close();
					break;
				case SYMBOLIC_LINK:
					jgitHelper.readSymlink(commit, item);
					break;
				case DIRECTORY:
					// TODO: readRecursive(wrapper, count, jgitHelper, commit, path + item + "/");
					break;
				default:
					throw new IllegalStateException("Had unkonwn type: " + wrapper.type());
			}
			System.out.print(".");
			if(count % 100 == 0) {
				System.out.println(count);
			}
			count++;
		}
		return count;
	}

	private static StatWrapper getStatsWrapper() {
		final StatWrapper wrapper;
		try {
			wrapper = StatWrapperFactory.create();
		} catch (UnsatisfiedLinkError e) {
			System.out.println("This might fail on machines without fuse-binaries.");
			e.printStackTrace();
			Assume.assumeNoException(e);	// stop test silently
			return null;
		} catch(NoClassDefFoundError e) {
			System.out.println("This might fail on machines without fuse-binaries.");
			e.printStackTrace();
			Assume.assumeNoException(e);	// stop test silently
			return null;
		}
		return wrapper;
	}
}
