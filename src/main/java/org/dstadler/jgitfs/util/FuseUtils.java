package org.dstadler.jgitfs.util;

import java.io.File;
import java.io.IOException;

import net.fusejna.FuseException;
import net.fusejna.FuseJna;



public class FuseUtils {
	// utility class should not be constructed
	private FuseUtils() {
	}

	public static void prepareMountpoint(File mountPoint) throws IOException {
		// if mountpoint exists, try to unmount it before re-using it
		try {
			FuseJna.unmount(mountPoint);
		} catch (FuseException e) {
			// ignored here
		}

		// does not exist => create it
		if(!mountPoint.exists()) {
			if(!mountPoint.mkdirs()) {
				throw new IOException("Could not create mountpoint at " + mountPoint.getAbsolutePath());
			}

			// done, cannot be mounted
			return;
		}

		// if mountpoint exists, try to unmount it before re-using it
		try {
			FuseJna.unmount(mountPoint);
		} catch (FuseException e) {
			// ignored here
		}
	}
}
