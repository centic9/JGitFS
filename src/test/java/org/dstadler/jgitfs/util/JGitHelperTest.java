package org.dstadler.jgitfs.util;

import net.fusejna.StructStat.StatWrapper;
import net.fusejna.types.TypeMode;
import net.fusejna.types.TypeMode.NodeType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import static org.dstadler.jgitfs.JGitFilesystemTest.getStatsWrapper;
import static org.junit.jupiter.api.Assertions.*;

public class JGitHelperTest {
    public final static String DEFAULT_COMMIT = "ede9797616a805d6cbeca376bfbbac9a8b7eb64f";
    private static final String SYMLINK_COMMIT = "e81ba32d8d51cdd1463e9a0b704059bd8ccbfd19";
    private static final String GITLINK_COMMIT = "ca1767dc76fe104d0b94fb2a5c962c82121be3da";
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private static boolean hasStashes = false;

    private JGitHelper helper;

    @BeforeAll
    public static void setUpClass() throws GitAPIException, IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder.setGitDir(new File(".git"))
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build()) {
            try (Git git = new Git(repository)) {
                List<Ref> tags = git.tagList().call();
                boolean found = false;
                for (Ref ref : tags) {
                    if (ref.getName().equals("refs/tags/testtag")) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    git.tag().setName("testtag").call();
                }

                hasStashes = !git.stashList().call().isEmpty();
            }
        }
    }

    @BeforeEach
    public void setUp() throws IOException {
        helper = new JGitHelper(".");
    }

    @AfterEach
    public void tearDown() {
        helper.close();
    }

    @Test
    public void test() throws Exception {
        assertNotNull(helper);
        assertTrue(helper.allCommits(null).size() > 0);
    }

    @Test
    public void testWithGitdir() throws Exception {
        JGitHelper lhelper = new JGitHelper("./.git");
        assertNotNull(lhelper);
        assertTrue(lhelper.allCommits(null).size() > 0);
        lhelper.close();
    }

    @Test
    public void testNotExistingDir() throws Exception {
        try {
            JGitHelper jGitHelper = new JGitHelper("notexisting");
            jGitHelper.close();
            fail("Should catch exception here");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("notexisting"));
        }
    }

    @Test
    public void testReadCommit() {
        assertEquals("abcd", helper.readCommit("abcd"));
        assertEquals("abcd", helper.readCommit("/commit/ab/cd"));
        assertEquals("1234567890123456789012345678901234567890", helper.readCommit("/commit/12/345678901234567890123456789012345678901234567890"));
    }

    @Test
    public void testReadPath() {
        assertEquals("", helper.readPath("abcd"));
        assertEquals("", helper.readPath("/commit/ab/cd"));
        assertEquals("blabla", helper.readPath("/commit/12/34567890123456789012345678901234567890/blabla"));
    }

    @Test
    public void testReadType() throws Exception {
        final StatWrapper wrapper = getStatsWrapper();
        assertNotNull(wrapper);

        System.out.println("Had commit: " + DEFAULT_COMMIT);
        helper.readType(DEFAULT_COMMIT, "src", wrapper);
        assertEquals(NodeType.DIRECTORY, wrapper.type());

        helper.readType(DEFAULT_COMMIT, "src/main/java/org", wrapper);
        assertEquals(NodeType.DIRECTORY, wrapper.type());

        helper.readType(DEFAULT_COMMIT, "README.md", wrapper);
        assertEquals(NodeType.FILE, wrapper.type());
        assertEquals(0, (wrapper.mode() & TypeMode.S_IXUSR));
        assertEquals(0, (wrapper.mode() & TypeMode.S_IXGRP));
        assertEquals(0, (wrapper.mode() & TypeMode.S_IXOTH));

        helper.readType(DEFAULT_COMMIT, "src/main/java/org/dstadler/jgitfs/JGitFS.java", wrapper);
        assertEquals(NodeType.FILE, wrapper.type());
        assertEquals(0, (wrapper.mode() & TypeMode.S_IXUSR));
        assertEquals(0, (wrapper.mode() & TypeMode.S_IXGRP));
        assertEquals(0, (wrapper.mode() & TypeMode.S_IXOTH));

        // need a newer commit for gitlink/symlink
        helper.readType(SYMLINK_COMMIT, "src/test/data/symlink", wrapper);
        assertEquals(NodeType.SYMBOLIC_LINK, wrapper.type());
        helper.readType(SYMLINK_COMMIT, "src/test/data/rellink", wrapper);
        assertEquals(NodeType.SYMBOLIC_LINK, wrapper.type());
        helper.readType(GITLINK_COMMIT, "fuse-jna", wrapper);
        assertEquals(NodeType.SYMBOLIC_LINK, wrapper.type());
    }

    @Test
    public void testReadTypeFails() throws Exception {
        final StatWrapper wrapper = getStatsWrapper();
        try {
            assertNotNull(wrapper);
            helper.readType(DEFAULT_COMMIT, "notexisting", wrapper);
            fail("Should catch exception here");
        } catch (FileNotFoundException e) {
            assertTrue(e.getMessage().contains("notexisting"));
        }
    }

    @Test
    public void testReadTypeExecutable() throws Exception {
        final StatWrapper wrapper = getStatsWrapper();
        assertNotNull(wrapper);

        // Look at a specific older commit to have an executable file
        helper.readType("355ea52f1e38b1c8e6537c093332180918808b68", "run.sh", wrapper);
        assertEquals(NodeType.FILE, wrapper.type());
        assertTrue((wrapper.mode() & TypeMode.S_IXUSR) != 0);
        assertTrue((wrapper.mode() & TypeMode.S_IXGRP) != 0);
        assertEquals(0, (wrapper.mode() & TypeMode.S_IXOTH));
    }

    @Test
    public void testOpenFile() throws Exception {
        System.out.println("Had commit: " + DEFAULT_COMMIT);
        String runSh = IOUtils.toString(helper.openFile(DEFAULT_COMMIT, "README.md"), CHARSET);
        assertTrue(StringUtils.isNotEmpty(runSh), "Had: " + runSh);
    }

    @Test
    public void testOpenFileFails() throws Exception {
        try {
            assertNotNull(IOUtils.toString(helper.openFile(DEFAULT_COMMIT, "src"), CHARSET));
            fail("Should catch exception here");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("src"));
        }

        try {
            assertNotNull(IOUtils.toString(helper.openFile(DEFAULT_COMMIT, "src/org"), CHARSET));
            fail("Should catch exception here");
        } catch (FileNotFoundException e) {
            assertTrue(e.getMessage().contains("src/org"));
        }

        try {
            assertNotNull(IOUtils.toString(helper.openFile(DEFAULT_COMMIT, "notexisting"), CHARSET));
            fail("Should catch exception here");
        } catch (FileNotFoundException e) {
            assertTrue(e.getMessage().contains("notexisting"));
        }
    }

    @Test
    public void testReadElementsAt() throws Exception {
        System.out.println("Had commit: " + DEFAULT_COMMIT);
        assertEquals("[main, test]", helper.readElementsAt(DEFAULT_COMMIT, "src").toString());
        assertEquals("[dstadler]", helper.readElementsAt(DEFAULT_COMMIT, "src/main/java/org").toString());

        String list = helper.readElementsAt(DEFAULT_COMMIT, "").toString();
        assertTrue(list.contains("src"), "Had: " + list);
        assertTrue(list.contains("README.md"), "Had: " + list);
        assertTrue(list.contains("build.gradle"), "Had: " + list);
    }

    @Test
    public void testReadElementsAtFails() throws Exception {
        try {
            helper.readElementsAt(DEFAULT_COMMIT, "run.sh");
            fail("Should catch exception here");
        } catch (FileNotFoundException e) {
            assertTrue(e.getMessage().contains("run.sh"));
        }

        try {
            helper.readElementsAt(DEFAULT_COMMIT, "README.md");
            fail("Should catch exception here");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("README.md"));
        }

        try {
            helper.readElementsAt(DEFAULT_COMMIT, "notexisting");
            fail("Should catch exception here");
        } catch (FileNotFoundException e) {
            assertTrue(e.getMessage().contains("notexisting"));
        }
    }

    @Test
    public void testGetBranchHeadCommit() throws IOException {
        assertNull(helper.getBranchHeadCommit("somebranch"));
        assertNotNull(helper.getBranchHeadCommit("master"));
        assertNotNull(helper.getBranchHeadCommit("refs_heads_master"));
    }

    @Test
    public void testGetRemoteHeadCommit() throws IOException {
        assertNull(helper.getRemoteHeadCommit("somebranch"));
        assertNotNull(helper.getRemoteHeadCommit("origin_master"));
        assertNotNull(helper.getRemoteHeadCommit("refs_remotes_origin_master"));
    }

    @Test
    public void testGetBranches() throws IOException {
        List<String> branches = helper.getBranches();
        assertTrue(branches.size() > 0);
        assertTrue(branches.contains("master"), "Had: " + branches);
        assertTrue(branches.contains("refs_heads_master"), "Had: " + branches);
    }

    @Test
    public void testGetRemotes() throws IOException {
        List<String> remotes = helper.getRemotes();
        assertTrue(remotes.size() > 0);
        assertTrue(remotes.contains("origin_master"), "Had: " + remotes);
        assertTrue(remotes.contains("refs_remotes_origin_master"), "Had: " + remotes);
    }

    @Test
    public void testGetTagHead() throws IOException {
        assertNull(helper.getTagHeadCommit("sometag"));
        assertNotNull(helper.getTagHeadCommit("testtag"));
        assertNotNull(helper.getTagHeadCommit("refs_tags_testtag"));
    }

    @Test
    public void testGetTags() throws IOException {
        List<String> tags = helper.getTags();
        assertTrue(tags.size() > 0);
        assertTrue(tags.contains("testtag"), "Had: " + tags);
        assertTrue(tags.contains("refs_tags_testtag"), "Had: " + tags);
    }

    @Test
    public void testGetStashHeadCommit() throws IOException {
        Assumptions.assumeTrue(hasStashes, "Cannot test stashes without having local stashes");

        assertNull(helper.getStashHeadCommit("somestash"));
        assertNotNull(helper.getStashHeadCommit("stash@{0}"));
    }

    @Test
    public void testGetStashOrigCommit() throws IOException {
        Assumptions.assumeTrue(hasStashes, "Cannot test stashes without having local stashes");

        assertNull(helper.getStashOrigCommit("somestash"));
        assertNotNull(helper.getStashOrigCommit("stash@{0}"));
    }

    @Test
    public void testGetStashes() throws IOException {
        Assumptions.assumeTrue(hasStashes, "Cannot test stashes without having local stashes");

        List<String> stashes = helper.getStashes();
        assertTrue(stashes.size() > 0);
        assertTrue(stashes.contains("stash@{0}"), "Had: " + stashes);
    }

    @Test
    public void testallCommitsNull() throws IOException {
        Collection<String> allCommits = helper.allCommits(null);
        int size = allCommits.size();
        assertTrue(size > 3, "Had size: " + size);
        assertTrue(allCommits.contains(DEFAULT_COMMIT));
    }

    @Test
    public void testallCommits() throws IOException {
        int size = helper.allCommits("zz").size();
        assertEquals(0, size, "Had size: " + size);

        Collection<String> allCommits = helper.allCommits(null);
        assertTrue(allCommits.size() > 0);
        assertTrue(allCommits.contains(DEFAULT_COMMIT));

        allCommits = helper.allCommits(allCommits.iterator().next().substring(0, 2));
        assertTrue(allCommits.size() > 0);

        allCommits = helper.allCommits("00");
        assertFalse(allCommits.contains(DEFAULT_COMMIT));

        allCommits = helper.allCommits(DEFAULT_COMMIT.substring(0, 2));
        assertTrue(allCommits.contains(DEFAULT_COMMIT));
    }

    @Test
    public void testAllCommitSubs() throws IOException {
        Collection<String> subs = helper.allCommitSubs();
        int subSize = subs.size();
        assertTrue(subSize > 3, "Had: " + subs);

        for (String tup : subs) {
            assertEquals(2, tup.length(), "Had: " + tup);
            assertTrue(tup.matches("[a-f0-9]{2}"), "Had: " + tup);
        }

        assertTrue(subs.contains(DEFAULT_COMMIT.substring(0, 2)));
    }

    @Disabled("local test")
    @Test
    public void testAllCommitSubsJenkins() throws IOException {
        helper.close();
        helper = new JGitHelper("/opt/jenkins/jenkins.git/.git");
        //helper = new JGitHelper("G:\\workspaces\\linux\\.git");

        runAllCommitSubs();
    }

    @Disabled("local test")
    @Test
    public void testAllCommitSubsPOI() throws IOException {
        helper.close();
        helper = new JGitHelper("/opt/poi/.git");

        runAllCommitSubs();
    }

    private void runAllCommitSubs() throws IOException {
        System.out.println("warmup");
        for (int i = 0; i < 3; i++) {
            int size = helper.allCommitSubs().size();
            assertTrue(size > 3, "Had size: " + size);
            System.out.print("." + size);
        }

        System.out.println("\nrun");
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            int size = helper.allCommitSubs().size();
            assertTrue(size > 3, "Had size: " + size);
            System.out.print("." + size);
        }
        System.out.println("avg.time: " + (System.currentTimeMillis() - start) / 10);
    }

    @Disabled("local test")
    @Test
    public void testAllCommitsJenkins() throws IOException {
        helper.close();
        helper = new JGitHelper("/opt/jenkins/jenkins.git/.git");
        //helper = new JGitHelper("G:\\workspaces\\linux\\.git");

        runAllCommits();
    }

    @Disabled("local test")
    @Test
    public void testAllCommitsPOI() throws IOException {
        helper.close();
        helper = new JGitHelper("/opt/poi/.git");

        runAllCommits();
    }

    private void runAllCommits() throws IOException {
        long start;

        System.out.println("warmup");
        for (int i = 0; i < 3; i++) {
            start = System.currentTimeMillis();
            int size = helper.allCommits(null).size();
            assertTrue(size > 3, "Had size: " + size);
            System.out.print("." + size + ": " + (System.currentTimeMillis() - start));
        }

        System.out.println("\nrun");
        start = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            int size = helper.allCommits(null).size();
            assertTrue(size > 3, "Had size: " + size);
            System.out.print("." + size);
        }
        System.out.println("avg.time: " + (System.currentTimeMillis() - start) / 10);
    }

    @Disabled("local test")
    @Test
    public void testSubversionEmptyFile() throws Exception {
        JGitHelper jgitHelper = new JGitHelper("/opt/Subversion/git");
        List<String> items = jgitHelper.getBranches();
        assertNotNull(items);
        assertTrue(items.contains("ppa_1.7.11"));

        String commit = jgitHelper.getBranchHeadCommit("ppa_1.7.11");
        assertNotNull(commit);

        items = jgitHelper.readElementsAt(commit, "");
        assertNotNull(items);
        assertTrue(items.size() > 0);

        //subversion/branch/ppa_1.7.11/build/generator/__init__.py

        items = jgitHelper.readElementsAt(commit, "build");
        assertNotNull(items);
        assertTrue(items.size() > 0);

        items = jgitHelper.readElementsAt(commit, "build/generator");
        assertNotNull(items);
        assertTrue(items.size() > 0);
        assertTrue(items.contains("__init__.py"), "Had: " + items);

        try (InputStream openFile = jgitHelper.openFile(commit, "build/generator/__init__.py")) {
            String string = IOUtils.toString(openFile, CHARSET);
            System.out.println("Having " + string.length() + " bytes: \n" + string);
        }

        try (InputStream openFile = jgitHelper.openFile(commit, "build/generator/__init__.py")) {
            // skip until we are at the offset
            assertEquals(0, openFile.skip(0));

            byte[] arr = new byte[4096];
            int read = openFile.read(arr, 0, 4096);
            System.out.println("Had: " + read);
        }

        jgitHelper.close();
    }

    @Test
    public void testWithTestdata() throws IOException {
        String commit = helper.getBranchHeadCommit("master");
        checkCommitContents(commit);
    }


    @Test
    public void testStashWithTestData() throws IOException {
        Assumptions.assumeTrue(hasStashes, "Cannot test stashes without having local stashes");

        String commit = helper.getStashHeadCommit("stash@{0}");
        checkCommitContents(commit);
    }

    @Test
    public void testStashOrigWithTestData() throws IOException {
        Assumptions.assumeTrue(hasStashes, "Cannot test stashes without having local stashes");

        String commit = helper.getStashOrigCommit("stash@{0}");
        checkCommitContents(commit);
    }

    private void checkCommitContents(String commit) throws IOException {
        assertNotNull(commit);

        // check that the test-data is there
        List<String> elements = helper.readElementsAt(commit, "src/test/data");
        assertEquals(4, elements.size(), "Had: " + elements);
        assertTrue(elements.contains("emptytestfile"));
        assertTrue(elements.contains("one"));
        assertTrue(elements.contains("symlink"));
        assertTrue(elements.contains("rellink"));

        // check type of files
        final StatWrapper wrapper = getStatsWrapper();
        assertNotNull(wrapper);

        helper.readType(commit, "src/test/data", wrapper);
        assertEquals(NodeType.DIRECTORY, wrapper.type());
        helper.readType(commit, "src/test/data/emptytestfile", wrapper);
        assertEquals(NodeType.FILE, wrapper.type());
        helper.readType(commit, "src/test/data/one", wrapper);
        assertEquals(NodeType.FILE, wrapper.type());
        helper.readType(commit, "src/test/data/symlink", wrapper);
        assertEquals(NodeType.SYMBOLIC_LINK, wrapper.type());
        helper.readType(commit, "src/test/data/rellink", wrapper);
        assertEquals(NodeType.SYMBOLIC_LINK, wrapper.type());

        // check that the empty file is actually empty
        try (InputStream stream = helper.openFile(commit, "src/test/data/emptytestfile")) {
            assertEquals("", IOUtils.toString(stream, CHARSET));
        }

        // check that the file has the correct content
        try (InputStream stream = helper.openFile(commit, "src/test/data/one")) {
            assertEquals("1", IOUtils.toString(stream, CHARSET).trim());
        }

        // check that we can read the symlink
        try (InputStream stream = helper.openFile(commit, "src/test/data/symlink")) {
            assertEquals("one", IOUtils.toString(stream, CHARSET).trim(), "Should be 'one' as it contains the filename of the file pointed to!");
        }
        try (InputStream stream = helper.openFile(commit, "src/test/data/rellink")) {
            assertEquals("../../../build.gradle", IOUtils.toString(stream, CHARSET).trim(), "Should be '../../../build.gradle' as it contains the filename of the file pointed to!");
        }

        // read the symlinks
        assertEquals("one", helper.readSymlink(commit, "src/test/data/symlink"));
        assertEquals("../../../build.gradle", helper.readSymlink(commit, "src/test/data/rellink"));
        try {
            helper.readSymlink(commit, "src/test/data/one");
            fail("Should not be able to read symlink for normal file");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("src/test/data/one"));
        }
        try {
            helper.readSymlink(commit, "src/test/data");
            fail("Should not be able to read symlink for directory");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("src/test/data"));
        }
    }

    @Test
    public void testToString() {
        // toString should not return null
        assertNotNull(helper.toString(), "A derived toString() should not return null!");

        // toString should not return an empty string
        assertNotEquals("", helper.toString(), "A derived toString() should not return an empty string!");

        // check that calling it multiple times leads to the same value
        String value = helper.toString();
        for (int i = 0; i < 10; i++) {
            assertEquals(value,
                    helper.toString(),
                    "toString() is expected to result in the same result across repeated calls!");
        }

    }

    @Test
    public void testReadSymlink() throws Exception {
        String link = helper.readSymlink(SYMLINK_COMMIT, "src/test/data/symlink");
        assertEquals("one", link);

        link = helper.readSymlink(SYMLINK_COMMIT, "src/test/data/rellink");
        assertEquals("../../../build.gradle", link);

        // TODO: add full support for Submodules
        try {
            link = helper.readSymlink(GITLINK_COMMIT, "fuse-jna");
            assertNotNull(link);
            assertEquals("one", link);
        } catch (@SuppressWarnings("unused") UnsupportedOperationException e) {
            // expected for now...
        }

        try {
            helper.readSymlink(DEFAULT_COMMIT, "build.gradle");
            fail("Should catch exception here");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("build.gradle"));
            assertTrue(e.getMessage().contains(DEFAULT_COMMIT));
        }
    }

    @Test
    public void testIsGitLink() throws IOException {
        assertFalse(helper.isGitLink(DEFAULT_COMMIT, "build.gradle"));
        assertTrue(helper.isGitLink(GITLINK_COMMIT, "fuse-jna"));
    }

    @Test
    public void testGetSubmodulesBareRepository() throws Exception {
        File localPath = File.createTempFile("JGitHelperTest", ".test");
        assertTrue(localPath.delete());

        try {
            System.out.println("Cloning to " + localPath);

            Git result = Git.cloneRepository()
                    .setURI(new File(".").toURI().toURL().toString())
                    .setBare(true)
                    .setDirectory(new File(localPath, ".git"))
                    .call();

            try {
                System.out.println("Cloned to " + localPath + ", result: " + result.getRepository().getDirectory());

                assertTrue(result.getRepository().isBare());

                try (JGitHelper jGitHelper = new JGitHelper(localPath.getAbsolutePath())) {
                    assertTrue(jGitHelper.allSubmodules().isEmpty());
                }
            } finally {
                // workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=474093
                result.getRepository().close();

                result.close();
            }
        } finally {
            FileUtils.deleteDirectory(localPath);
        }
    }

    @Test
    public void testJGitCloneCloseResources() throws Exception {
        File localPath = File.createTempFile("JGitHelperTest", ".test");
        assertTrue(localPath.delete());

        try {
            System.out.println("Cloning to " + localPath);
            Git result = Git.cloneRepository()
                    .setURI("https://github.com/centic9/jgit-cookbook.git")
                    .setDirectory(new File(localPath, ".git"))
                    .call();

            // workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=474093
            result.getRepository().close();

            result.close();

            System.out.println("Cloned to " + localPath + ", now opening repository");
        } finally {
            FileUtils.deleteDirectory(localPath);
        }
    }

    @Test
    public void testConstructForInvalidSubmodule() throws Exception {
        try {
            JGitHelper subHelper = new JGitHelper(helper, "notexisting");
            subHelper.close();
            fail("Should catch exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("notexisting"));
        }
    }

    @Disabled("Just used for testing")
    @Test
    public void testGitLinkRepository() throws Exception {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();

        try (Repository repository = builder.setGitDir(new File("./.git"))
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build()) {
//        Repository repository = helper.getRepository();

//        assertFalse("Repo should not be bare", repository.isBare());

            System.out.println("Found submodules" + helper.allSubmodules());

            try (Repository subRepo = SubmoduleWalk.getSubmoduleRepository(repository, "fuse-jna")) {

//        Repository subRepo = builder.setGitDir(new File("/opt/git-fs/JGitFS/fuse-jna/.git"))
//                .readEnvironment() // scan environment GIT_* variables
//                .findGitDir() // scan up the file system tree
//                .build();

                assertFalse(subRepo.isBare());
                List<Ref> allRefs = subRepo.getRefDatabase().getRefs();
                assertFalse(allRefs.isEmpty(), "We should find some refs via submodule-repository");

//        {
//            SubmoduleWalk walk = SubmoduleWalk.forIndex( repository );
//            boolean found = false;
//            while( walk.next() ) {
//              Repository submoduleRepository = walk.getRepository();
//              //Git.wrap( submoduleRepository ).checkout().call();
//              //listSubs(submoduleRepository);
//
//              Map<String, Ref> allRefs = submoduleRepository.getAllRefs();
//              assertFalse("We should find some refs via submodule-repository", allRefs.isEmpty());
//              for(String ref : allRefs.keySet()) {
//                  System.out.println(ref);
//              }
//
//              submoduleRepository.close();
//            }
//            assertTrue("Should find some submodules", found);
//            walk.release();
//        }

                //listSubs(SubmoduleWalk.getSubmoduleRepository(repository, "fuse-jna"));
                {
                    allRefs.forEach(System.out::println);
                }

                // find the commit
		        /*ObjectId lastCommitId = subRepo.resolve(SUBMODULE_COMMIT);

		        // .add(lastCommitId)
//        Iterable<RevCommit> commits = new Git(subRepo).log().call();
//        for(RevCommit commit : commits) {
//            System.out.println("Commit: " + commit.getId());
//        }

		        // a RevWalk allows to walk over commits based on some filtering that is defined
		        try (RevWalk revWalk = new RevWalk(subRepo)) {
			        RevCommit commit = revWalk.parseCommit(lastCommitId);
			        // and using commit's tree find the path
			        RevTree tree = commit.getTree();
			        System.out.println("Having tree: " + tree);
			        // now try to find a specific file
			        try (TreeWalk treeWalk = new TreeWalk(subRepo)) {
				        treeWalk.addTree(tree);
				        treeWalk.setRecursive(true);
				        treeWalk.setFilter(PathFilter.create("build.gradle"));
				        if (!treeWalk.next()) {
				            throw new IllegalStateException("Did not find expected file 'build.gradle'");
				        }
				        ObjectId objectId = treeWalk.getObjectId(0);
				        ObjectLoader loader = subRepo.open(objectId);
				        // and then one can the loader to read the file
				        loader.copyTo(System.out);
			        }
			        revWalk.dispose();
		        }*/
            }
        }
//        try (JGitHelper linkHelper = new JGitHelper("fuse-jna")) {
//            Set<String> allCommitSubs = linkHelper.allCommitSubs();
//            assertNotNull(allCommitSubs);
//            assertFalse(allCommitSubs.isEmpty());
//        }
    }

    @Disabled("Used for local testing")
    @Test
    public void testPOIInvalidSubmodule() throws IOException {
        try (JGitHelper jgit = new JGitHelper("/opt/poi")) {

            String path = "/commit/78/632d39b2e48e650d82e5ad1480d96a3de4063f/src/documentation";

            String lcommit = jgit.readCommit(path);
            String dir = jgit.readPath(path);

            // for symlinks that are actually git-links for a submodule, we need to redirect back to the
            // separate submodule-folder with the correct submodule name filled in
            assertTrue(jgit.isGitLink(lcommit, dir));

			/*assertEquals("", jgit.readSymlink(lcommit, dir));

			try (InputStream openFile = jgit.openFile(lcommit, path)) {
				assertEquals("", IOUtils.toString(openFile, Charset.forName("UTF-8")));
			}*/

            System.out.println("Submodules: " + jgit.allSubmodules());

            String subName = jgit.getSubmoduleAt(dir);
            String subHead = jgit.getSubmoduleHead(subName);
            assertNotNull(subHead);

            assertTrue(jgit.readSymlink(lcommit, dir).getBytes().length > 0);
        }
    }

    @Disabled("Just a local test")
    @Test
    public void testStrangeBareRepo() throws IOException {
        String gitDir = "/opt/openambit/openambit.git";
        //noinspection ConstantConditions
        if (!gitDir.endsWith("/.git") && !gitDir.endsWith("/.git/")) {
            gitDir = gitDir + "/.git";
        }
        if (!new File(gitDir).exists()) {
            throw new IllegalStateException("Could not find git repository at " + gitDir);
        }

		/*WindowCacheConfig cfg = new WindowCacheConfig();
		// set a lower stream file threshold as we want to run the code with
		// very limited memory, e.g. -Xmx60m and having the default of 50BM
		// would cause OOM if access is done in parallel
		cfg.setStreamFileThreshold(1024 * 1024);
		cfg.install();*/

        System.out.println("Using git repo at " + gitDir);
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder.setGitDir(new File(gitDir))
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build()) {

            assertFalse(repository.isBare());
        }
    }
}
