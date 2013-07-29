package org.dstadler.jgitfs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.fusejna.FuseException;

import org.dstadler.jgitfs.util.FuseUtils;

/**
 * Main class which handles commandline parsing and starts up the {@link JGitFilesystem}.
 *
 * It also ensures that the mountpoint is unmounted if there is still a FUSE-mount
 * active on it.
 *
 * @author dominik.stadler
 */
public class JGitFS
{
	/**
	 * Main method for JGitFS.
	 *
	 * @param args
	 * @throws FuseException If mounting fails.
	 * @throws IOException If the given Git repository cannot be read or some other error happens during file access.
	 */
	public static void main(final String... args) throws FuseException, IOException
	{
		if (args.length % 2 != 0 || args.length == 0) {
			System.err.println("Usage: GitFS <git-repo> <mountpoint> ...");
			System.exit(1);
		}

		List<JGitFilesystem> gitFSList = new ArrayList<JGitFilesystem>(args.length/2);
		try {
		for(int i = 0;i < args.length;i+=2) {
			String gitDir = args[i];
			File mountPoint = new File(args[i+1]);

			System.out.println("Mounting git repository at " + gitDir + " at mountpoint " + mountPoint);

			// now create the Git filesystem
			@SuppressWarnings("resource")
			JGitFilesystem gitFS = new JGitFilesystem(gitDir, false);
			gitFSList.add(gitFS);

			// ensure that we do not have a previous mount lingering on the mountpoint
			FuseUtils.prepareMountpoint(mountPoint);

			// mount the filesystem. This actually blocks until the filesystem is unmounted
			gitFS.mount(mountPoint, i == (args.length-2));
			}
		} finally {
			// ensure that we try to close all filesystems that we created
			for(JGitFilesystem gitFS : gitFSList) {
				gitFS.close();
			}
		}
	}
}
