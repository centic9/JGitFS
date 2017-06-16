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

    private static final AtomicLong pushedFiles = new AtomicLong(0);
    private static final AtomicLong consumedFiles = new AtomicLong(0);

    public static void main(String[] args) throws IOException, InterruptedException {
        PopulateThread populateThread = new PopulateThread(new File("/fs"));
        populateThread.start();

        ConsumeThread consumeThread = new ConsumeThread();
        consumeThread.start();

        System.out.println("Press enter to stop");
        //noinspection ResultOfMethodCallIgnored
        System.in.read();

        populateThread.shouldStop();
        consumeThread.shouldStop();

        populateThread.join();
        consumeThread.join();
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
            long count = 0;
            while(!shouldStop) {
                // go one down with probability 50%
                if(current.equals(startDir) || (current.isDirectory() && RandomUtils.nextInt(0, 100) >= 50)) {
                    File[] files = current.listFiles();
                    if(files != null && files.length > 0) {
                        path.push(current);
                        current = files[RandomUtils.nextInt(0, files.length)];
                    } else {
                        if(!path.isEmpty()) {
                            current = path.pop();
                        }
                    }
                } else {
                    // read the file and go one up again
                    try {
                        long pushed = pushedFiles.incrementAndGet();
                        count++;
                        if(count % 100 == 0) {
                            System.out.println("Using file (" + queue.size() + "/" + pushed + "/" + consumedFiles.get() + "): " + current);
                        }
                        queue.put(current);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if(!path.isEmpty()) {
                        current = path.pop();
                    }
                }
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
            long count = 0;
            while(!shouldStop) {
                try {
                    File file = queue.poll(5, TimeUnit.SECONDS);
                    if(file != null) {
                        long consumed = consumedFiles.incrementAndGet();
                        count++;
                        if(count % 100 == 0) {
                            System.out.println("Handling file (" + queue.size() + "/" + pushedFiles.get() + "/" + consumed + "): " + file);
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
                        if(file.isDirectory()) {
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
