package org.dstadler.jgit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Some tests to try to narrow down/reproduce some problems in JGit itself.
 */
public class JGitReproducer {
    @Test
    public void testSymlink() throws Exception {
        try(Repository repository = createNewRepository()) {
            commitSymbolicLink(repository);

            // get Commit
            Ref head = repository.exactRef("refs/heads/master");
            System.out.println("Found head: " + head);

            // a RevWalk allows to walk over commits based on some filtering that is defined
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit commit = walk.parseCommit(head.getObjectId());

                System.out.println("Found commit " + commit);

                // and using commit's tree find the path
                RevTree tree = commit.getTree();

                // now read the file/directory attributes
                try (TreeWalk treeWalk = buildTreeWalk(repository, tree, "link")) {
                    FileMode fileMode = treeWalk.getFileMode();

                    System.out.println("Had fileMode: " + fileMode);
                }
            }
        }
    }

    private TreeWalk buildTreeWalk(Repository repository, RevTree tree, final String path) throws IOException {
        TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree);

        if(treeWalk == null) {
            throw new FileNotFoundException("Did not find expected file '" + path + "' in tree '" + tree.getName() + "'");
        }

        return treeWalk;
    }

    private void commitSymbolicLink(Repository repository) throws IOException, GitAPIException {
        try (Git git = new Git(repository)) {
            // create a symbolic link
            Path newLink = Paths.get(repository.getDirectory().getParentFile().getAbsolutePath(), "link");
            Path target = Paths.get("/tmp");
            Files.createSymbolicLink(newLink, target);

            // run the add-call
            git.add()
                    .addFilepattern("link")
                    .call();


            // and then commit the changes
            git.commit()
                    .setMessage("Added symbolic link")
                    .call();

            System.out.println("Added symbolic link 'link' to repository at " + repository.getDirectory());
        }
    }

    public static Repository createNewRepository() throws IOException {
        // prepare a new folder
        File localPath = File.createTempFile("TestGitRepository", "");
        if(!localPath.delete()) {
            throw new IOException("Could not delete temporary file " + localPath);
        }

        // create the directory
        Repository repository = FileRepositoryBuilder.create(new File(localPath, ".git"));
        repository.create();

        return repository;
    }
}
