package org.dstadler.jgitfs;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang3.tuple.Pair;
import org.dstadler.jgitfs.console.Console;
import org.dstadler.jgitfs.util.FuseUtils;

import com.google.common.base.Preconditions;

import net.fusejna.FuseException;

/**
 * Main class which handles commandline parsing and starts up the {@link JGitFilesystem}.
 * <p>
 * It also ensures that the mountpoint is unmounted if there is still a FUSE-mount
 * active on it.
 *
 * @author dominik.stadler
 */
public class JGitFS {
    private static final ConcurrentMap<String, Pair<File, JGitFilesystem>> mounts = new ConcurrentHashMap<>();

    /**
     * Main method for JGitFS.
     *
     * @param args The initial filesystems to mount and their mountpoint
     * @throws FuseException If mounting fails.
     * @throws IOException   If the given Git repository cannot be read or some other error happens during file access.
     */
    public static void main(final String... args) throws FuseException, IOException {
        if (args.length % 2 != 0) {
            System.err.println("Usage: GitFS <git-repo> <mountpoint> ...");
            System.exit(1);
        }

        try {
            for (int i = 0; i < args.length; i += 2) {
                mount(args[i], new File(args[i + 1]));
            }

            new Console().run(System.in, System.out);
        } finally {
            // ensure that we try to close all filesystems that we created
            for (Pair<File, JGitFilesystem> gitFS : mounts.values()) {
                gitFS.getRight().close();
            }
        }
    }

    /**
     * Unmount the given mounting and free related system resources.
     *
     * @param dirOrMountPoint Either the location of the git repository or the mount point.
     * @return true if the directory was unmounted successfully, false if it was not found and an
     * exception is thrown if an error occurs during unmounting.
     * @throws IOException If an error occurred while closing the Git repository or while unmounting the filesystem.
     */
    public static boolean unmount(String dirOrMountPoint) throws IOException {
        for (Map.Entry<String, Pair<File, JGitFilesystem>> entry : mounts.entrySet()) {
            String gitDir = entry.getKey();
            if (gitDir.equals(dirOrMountPoint) ||
                    entry.getValue().getLeft().getPath().equals(dirOrMountPoint)) {
                System.out.println("Unmounting git repository at " + gitDir + " at mountpoint " + entry.getValue().getLeft());
                entry.getValue().getRight().close();
                mounts.remove(gitDir);
                return true;
            }
        }

        System.out.println("Could not find " + dirOrMountPoint);
        return false;
    }

    /**
     * Create a mount of the given git repository at the given mount point. Will throw an exception
     * if any of the mount operations fail or either the git repository is already mounted or the
     * mount point is already used for another mount.
     *
     * @param gitDir     The git-repository to mount
     * @param mountPoint The point in the filesystem where the Git Repository should appear.
     * @throws IOException              If a file operation fails during creating the mount.
     * @throws UnsatisfiedLinkError     If an internal error occurs while setting up the mount.
     * @throws FuseException            If an internal error occurs while setting up the mount.
     * @throws IllegalArgumentException If the git repository is already mounted somewhere or the
     *                                  mount point is already used for another mount operation.
     */
    public static void mount(String gitDir, File mountPoint)
            throws IOException, UnsatisfiedLinkError, FuseException, IllegalArgumentException {
        System.out.println("Mounting git repository at " + gitDir + " at mountpoint " + mountPoint);

        // don't allow double-mounting of the git-directory although it should theoretically work on different mountpoints
        Preconditions.checkArgument(!mounts.containsKey(gitDir),
                "Cannot mount git directory '%s' which is already mounted to %s.", gitDir, mounts.get(gitDir) == null ? null : mounts.get(gitDir).getLeft());

        // don't allow double-mounting on the same mount-point, this will fail anyway
        for (Map.Entry<String, Pair<File, JGitFilesystem>> entry : mounts.entrySet()) {
            Preconditions.checkArgument(!entry.getValue().getKey().equals(mountPoint),
                    "Cannot mount to mount point '%s' which is already used for mount at %s.", mountPoint, entry.getKey());
        }

        // now create the Git filesystem
        JGitFilesystem gitFS = new JGitFilesystem(gitDir, false);

        // ensure that we do not have a previous mount lingering on the mountpoint
        FuseUtils.prepareMountpoint(mountPoint);

        // mount the filesystem. If this is the last mount-point that was specified and no console is used
        // then block until the filesystem is unmounted
        gitFS.mount(mountPoint, false);

        mounts.put(gitDir, Pair.of(mountPoint, gitFS));
    }

    /**
     * Prints out a list of currently mounted git repositories.
     */
    public static void list() {
        // determine the max length of the name of the mounted repository and the mount point
        int maxKey = 1, maxValue = 1;
        for (Map.Entry<String, Pair<File, JGitFilesystem>> entry : mounts.entrySet()) {
            maxKey = Math.max(maxKey, entry.getKey().length());
            maxValue = Math.max(maxValue, entry.getValue().getLeft().getAbsolutePath().length());
        }

        TreeMap<String, Pair<File, JGitFilesystem>> sortedMap = new TreeMap<>(String::compareToIgnoreCase);
        sortedMap.putAll(mounts);
        for (Map.Entry<String, Pair<File, JGitFilesystem>> entry : sortedMap.entrySet()) {
            List<Pair<String, Long>> stats = entry.getValue().getRight().getStats();
            StringBuilder statsStr = new StringBuilder();
            for (Pair<String, Long> stat : stats) {
                statsStr.append(String.format("%8s: %8d, ", stat.getKey(), stat.getValue()));
            }
            System.out.printf(
                    "%-" + maxKey + "s mounted at %-" + maxValue + "s, stats: %s%n",
                    entry.getKey(), entry.getValue().getLeft().getAbsolutePath(), statsStr);
        }
    }
}
