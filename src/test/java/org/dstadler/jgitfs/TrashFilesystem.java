package org.dstadler.jgitfs;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomUtils;

import java.io.*;
import java.util.Stack;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test application to put some load on the resulting filesystems
 */
public class TrashFilesystem {
    private static final BlockingQueue<File> queue = new ArrayBlockingQueue<>(1000);

    private static final long start = System.currentTimeMillis();
    private static final AtomicLong pushedFiles = new AtomicLong(0);
    private static final AtomicLong consumedFiles = new AtomicLong(0);

    public static void main(String[] args) throws IOException, InterruptedException {
        // starting two threads each was actually slower on my Laptop!
        PopulateThread populateThread = new PopulateThread(new File("/fs"));
        populateThread.start();

        ConsumeThread consumeThread = new ConsumeThread();
        consumeThread.start();

        ConsumeThread consumeThread2 = new ConsumeThread();
        consumeThread2.start();

        System.out.println("Press enter to stop");
        //noinspection ResultOfMethodCallIgnored
        System.in.read();
        System.out.println("Stopping threads");

        populateThread.shouldStop();
        consumeThread.shouldStop();
        consumeThread2.shouldStop();

        populateThread.join();
        consumeThread.join();
        consumeThread2.join();
    }

    private static class PopulateThread extends Thread {
        private final File startDir;
        private volatile boolean shouldStop = false;

        public PopulateThread(File startDir) {
            super("Producer");

            this.startDir = startDir;
        }

        public void shouldStop() {
            shouldStop = true;
        }

        @Override
        public void run() {
            Stack<File> path = new Stack<>();
            File current = startDir;
            while (!shouldStop) {
                // for directories go one down with probability 50%
                if (current.equals(startDir) || (current.isDirectory() && RandomUtils.insecure().randomInt(0, 100) >= 40)) {
                    File[] files = current.listFiles();
                    if (files != null && files.length > 0) {
                        path.push(current);
                        current = files[RandomUtils.insecure().randomInt(0, files.length)];
                        pushFile(current);
                    } else {
                        // read the file and go one up again
                        pushFile(current);
                        if (!path.isEmpty()) {
                            current = path.pop();
                        }
                    }
                } else {
                    // read the file/directory and go one up again
                    pushFile(current);
                    if (!path.isEmpty()) {
                        current = path.pop();
                    }
                }
            }
        }

        private void pushFile(File current) {
            try {
                long pushed = pushedFiles.incrementAndGet();
                if (pushed % 200 == 0) {
                    System.out.printf("Pushing file (%d/%d/%d/%.2f per sec): %s%n",
                            queue.size(), pushed, consumedFiles.get(), pushed / ((double) (System.currentTimeMillis() - start) / 1000),
                            current);
                }
                queue.put(current);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static class ConsumeThread extends Thread {
        private volatile boolean shouldStop = false;

        public ConsumeThread() {
            super("Producer");
        }

        public void shouldStop() {
            shouldStop = true;
        }

        @Override
        public void run() {
            byte[] bytes = new byte[1024];
            while (!shouldStop) {
                try {
                    File file = queue.poll(5, TimeUnit.SECONDS);
                    if (file != null) {
                        long consumed = consumedFiles.incrementAndGet();
                        if (consumed % 200 == 0) {
                            System.out.printf("Handling file (%d/%d/%d/%.2f per sec): %s%n",
                                    queue.size(), pushedFiles.get(), consumed, consumed / ((double) (System.currentTimeMillis() - start) / 1000),
                                    file);
                        }

                        // trigger some accesses to the attributes

                        //noinspection ResultOfMethodCallIgnored
                        file.canExecute();
                        //noinspection ResultOfMethodCallIgnored
                        file.canRead();
                        //noinspection ResultOfMethodCallIgnored
                        file.canWrite();
                        //noinspection ResultOfMethodCallIgnored
                        file.isHidden();
                        //noinspection ResultOfMethodCallIgnored
                        file.lastModified();
                        //noinspection ResultOfMethodCallIgnored
                        file.length();

                        // then read the directory or the file-contents
                        if (file.isDirectory()) {
                            //noinspection ResultOfMethodCallIgnored
                            file.list();
                        } else {
                            try (InputStream str = new FileInputStream(file)) {
                                IOUtils.read(str, bytes);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
