package org.dstadler.jgitfs.util;


public class GitUtils {
	private final static String COMMIT_SLASH = "/commit/";
	private final static int COMMIT_SLASH_LENGTH = COMMIT_SLASH.length();
	private final static int SHA1_LENGTH = 40;
	
	public static boolean isTagDir(final String path) {
		return path.matches("/tag/[^/]+") && !path.endsWith(".hidden");
	}

	public static boolean isBranchDir(final String path) {
		return path.matches("/branch/[^/]+") && !path.endsWith(".hidden");
	}

	public static boolean isCommitTupel(final String path) {
		return path.matches("/commit/[a-f0-9]{2}");
	}
	
	public static boolean isCommitDir(final String path) {
		// 8 for /commit/, 40 + 1 for commitish plus one slash
		return path.startsWith(COMMIT_SLASH) && path.length() == (COMMIT_SLASH_LENGTH + SHA1_LENGTH + 1);
	}

	public static boolean isCommitSubDir(final String path) {
		// 8 for /commit/, 40 + 2 for commitish plus two slashes
		return path.startsWith(COMMIT_SLASH) && path.length() > (COMMIT_SLASH_LENGTH + SHA1_LENGTH + 2);
	}
}
