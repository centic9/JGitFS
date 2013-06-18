package org.dstadler.jgitfs.util;


public class GitUtils {
	public final static String COMMIT_SLASH = "/commit/";
	public final static int COMMIT_SLASH_LENGTH = COMMIT_SLASH.length();
	
	public final static String BRANCH_SLASH = "/branch/";
	public final static String TAG_SLASH = "/tag/";
	
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
		return path.startsWith(COMMIT_SLASH) && path.length() > (COMMIT_SLASH_LENGTH + SHA1_LENGTH + 2) && !path.endsWith(".hidden");
	}
}
