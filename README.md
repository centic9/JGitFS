[![Build Status](https://buildhive.cloudbees.com/job/centic9/job/JGitFS/badge/icon)](https://buildhive.cloudbees.com/job/centic9/job/JGitFS/)

JGitFS provides access to Git branches/tags/commits like if they would be separate directories via a [FUSE][Linux-Fuse] 
userland filesystem. 

## Getting started

#### Grab it

    git clone --recursive git://github.com/centic9/JGitFS

Note: The "--recursive" is necessary to clone necessary submodules as well!
If you did a normal clone, you can do ``git submodule update --init --recursive`` to fetch the submodule.

#### Build it and create the distribution files

	cd JGitFS
	./gradlew installDist

#### Run it

    build/install/JGitFS/bin/JGitFS <path-to-git> <location-for-filesystem>

I.e. to get a view of the Git repository located at /opt/project mounted at /mnt/git, do
 
    build/install/JGitFS/bin/JGitFS /opt/project /mnt/git

## The longer stuff

#### Details

If you have a Git repository at /opt/project, then after calling 
 
    build/install/JGitFS/bin/JGitFS /opt/project /mnt/git

you will have a directory structure under `/mnt/git` which shows sub-directories `branches`, `tags` and `commit` which provide access to previous versions of your project. This is useful if you want to look at more than one version at the same time, e.g. to use a more powerful compare-tool like Beyond Compare to visualize the differences between two branches.

This allows to use a Git repository a bit like IBM/Rational ClearCase dynamic views, because you can directly access past points in the history of your project without having to switch to them.  

Note: Access is strictly read-only, no writing of new commits on branches is possible. Branches and Tags are actually implemented as symbolic links to the respective commit. The commit-directory uses the same two-byte directory-substructure like Git uses in .git/objects.


#### Change it

Implementation should be straightforward, the class `JGitFS` implements the commandline handling, `JGitFilesystem` implements the filesystem interfaces that are neede for simple read-only access as well as some caching, `JGitHelper` encapsulates access to Git, `GitUtils` are local utils for computing commits, ...

Create matching Eclipse project files

	./gradlew eclipse

Run unit tests

	./gradlew test jacocoTestReport

#### The idea

I was looking for a way to visualize branches of my Git repositories as separate directories so I could easier compare different versions. There are ways to do a 2nd checkout from an existing repository to have two working copies, but this is cumbersome. 

I did find a number of projects, developed in native, python or caml, but they were either dormant or not fully working for some reason. 

As part of some further research I stumbled upon the great [fuse-jna] library which makes building a [FUSE][Linux-Fuse] userland filesystem in 100% pure Java very easy and straightforward by making use of [JNA] instead of using JNI compiled code. This means no native code, no compile troubles, no nothing but full access to the [FUSE][Linux-Fuse] functionality! Amazing! 

On the Git side I had heard about [JGit] and wanted to give it a try anyway, using it was a bit of a rough ride and in order to get to grips with it I ended up putting together a collection of code-snippets at [jgit-cookbook], but in the end pure-Java access to Git repositories worked fine as well.

Finally after some performance tuning sessions and adding some caching at the right spots I was also satisfied with the performance, naturally there is a bit of overhead but overall I am able to work with fairly large repositories without large delays, only some more CPU is used because of all the compression/format reading in JGit and this doesn't seem to be a big concern with fairly up-to-date hardware.

#### Todos

* Only commits reachable via refs or tags are listed currently as I could not yet get JGit to return me a list of all commits, so commits which still exist, but are unreferenced currently are not visible
* Stashes are not listed yet
* We could show the state of the index as separate folder-hierarchy via DirCache
* Is there a way to include the current workspace via WorkingTreeIterator?
* JGit also supports Notes, how could we show these?	
* Sometimes the history of a single file is needed, what about having dirs perfile/branch, perfile/commit, perfile/tag, ...
* Would be nice to get this on Windows via cygwin as well, seems there is https://github.com/openunix/fuse-cygwin/, but could not get it to compile yet 

#### Compatibility

Because it is based on [fuse-jna], JGitFS should work with:

* OS X with [MacFUSE]/[fuse4x]/[OSXFUSE] on Intel architectures
* Linux with [FUSE][Linux-Fuse] on Intel and PowerPC architectures
* FreeBSD with [FUSE][FreeBSD-Fuse] on Intel architectures

#### Related projects

* https://github.com/g2p/git-fs
* https://bitbucket.org/billcroberts/gitfs
* https://github.com/akiellor/githubfs

#### Licensing
* JGitFS is licensed under the [BSD 2-Clause License].
* [fuse-jna] is licensed under the [BSD 2-Clause License].
* [JNA] is licensed under the [LGPL v2.1].
* [JGit] is licensed under the [EDL]. 
* The Apache Commons `io` and `lang` libraries are licensed under the [Apache 2.0 License] 

[fuse-jna]: https://github.com/EtiennePerot/fuse-jna
[JNA]: https://github.com/twall/jna
[JGit]: http://eclipse.org/jgit/
[jgit-cookbook]: https://github.com/centic9/jgit-cookbook
[MacFUSE]: http://code.google.com/p/macfuse/
[fuse4x]: http://fuse4x.org/
[OSXFUSE]: http://osxfuse.github.com/
[Linux-FUSE]: http://fuse.sourceforge.net/
[FreeBSD-FUSE]: http://wiki.freebsd.org/FuseFilesystem
[BSD 2-Clause License]: http://www.opensource.org/licenses/bsd-license.php
[LGPL v2.1]: http://www.opensource.org/licenses/lgpl-2.1.php
[EDL]: http://www.eclipse.org/org/documents/edl-v10.php
[Apache 2.0 License]: http://www.apache.org/licenses/LICENSE-2.0
