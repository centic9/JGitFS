package org.dstadler.jgitfs.util;

import java.io.File;
import java.io.IOException;

import net.fusejna.FuseException;
import net.fusejna.FuseJna;

/**
 * Small utility class for functionality related to
 * the fusejna file system functionality.
 */
public class FuseUtils {
    // utility class should not be constructed
    private FuseUtils() {
    }

    /**
     * Make sure the given mount-point can be used for
     * mounting a filesystem.
     *
     * @param mountPoint The location to check.
     * @throws IOException If the mount-point is invalid
     */
    public static void prepareMountpoint(File mountPoint) throws IOException {
        // if mountpoint exists, try to unmount it before re-using it
        try {
            FuseJna.unmount(mountPoint);
        } catch (FuseException e) {
            // ignored here
        }

        // does not exist => create it
        if (!mountPoint.exists()) {
            if (!mountPoint.mkdirs()) {
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
