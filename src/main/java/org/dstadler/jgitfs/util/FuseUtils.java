package org.dstadler.jgitfs.util;

import java.io.File;
import java.io.IOException;

import net.fusejna.FuseJna;



public class FuseUtils {
	public static void prepareMountpoint(File mountPoint) throws IOException {
		// if mountpoint exists, try to unmount it before re-using it
		FuseJna.unmount(mountPoint);

		// does not exist => create it
		if(!mountPoint.exists()) {
			if(!mountPoint.mkdirs()) {
				throw new IOException("Could not create mountpoint at " + mountPoint.getAbsolutePath());
			}

			// done, cannot be mounted
			return;
		}

		// if mountpoint exists, try to unmount it before re-using it
		FuseJna.unmount(mountPoint);
	}
}
