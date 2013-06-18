package org.dstadler.jgitfs.util;


public class GitUtils {
	public static boolean isTagDir(final String path) {
		return path.matches("/tag/[^/]+");
	}

	public static boolean isBranchDir(final String path) {
		return path.matches("/branch/[^/]+");
	}

	public static boolean isCommitDir(final String path) {
		return path.matches("/commit/[a-f0-9]{2}");
	}
}
