[![Build Status]((https://travis-ci.org/centic9/JGitFS.png)](https://travis-ci.org/centic9/JGitFS)

JGitFS provides access to Git branches/tags/commits like if they would be separate directories via a [FUSE][Linux-Fuse] 
userland filesystem. 

## The straight dope
#### Grab it

    git clone git://github.com/centic9/JGitFS

#### Build it and create the distribution files

	gradle install

#### Run it

    build/install/JGitFS/bin/JGitFS <path-to-git> <location-for-filesystem>

E.g. if you have a Git repository at /opt/project, then after calling 
 
    build/install/JGitFS/bin/JGitFS /opt/project /mnt/git

you will have a directory structure under `/mnt/git` which shows sub-directories `branches`, `tags` and `commit` which provide access to previous versions of your project. This is useful if you want to look at more than one version at the same time, e.g. to use a more powerful compare-tool like Beyond Compare to visualize the differences between two branches.

This allows to use a Git repository a bit like IBM/Rational ClearCase dynamic views, because you can directly access past points in the history of your project without having to switch to them.  

Note: Access is strictly read-only, no writing of new commits on branches is possible. Branches and Tags are actually implemented as symbolic links to the respective commit. The commit-directory uses the same two-byte directory-substructure like Git uses in .git/objects.

## The longer stuff
#### The idea

I was looking for a way to visualize branches of my Git repositories as separate directories so I could easier compare different versions. I did find a number of projects, developed in native, python or caml and all of them were 
either dormant or not fully working for some reason. 

As part of some further research I stumbled upon the great [fuse-jna] library which makes building a [FUSE][Linux-Fuse] userland filesystem in 100% pure Java very easy and straightforward by making use of [JNA]. No native code, no compile troubles, no nothing but full access to the [FUSE][Linux-Fuse] functionality! Amazing! 

On the Git side I had heard about [JGit] and wanted to give it a try anyway, using it was a bit of a rough ride and in order to get to grips with it I ended up with a collection of code-snippets at [jgit-cookbook], but in the end pure-Java access to Git repositories worked fine as well.

#### Todos

* Only commits reachable via refs or tags are listed currently as I could not yet get JGit to return me a list of all commits
* Some operations are not performance-optimized yet, listing files in a directory sometimes results in high IO and CPU load, surely JGit can be used better in a few places
* Especially reading information about a specific path as-of a specific commit is done in a bad way and causes lots of overhead, see comments in JGitHelper.buildTreeWalk() for details
* Remote branches are not included yet, but should be fairly easy to add

#### How to hack on it

Create matching Eclipse project files

	gradle eclipse

Implementation should be straightforward, the class `JGitFS` implements the filesystem interface, `JGitHelper` encapsulates access to Git, `GitUtils` are local utils for computing commits, ...

#### Compatibility

Because it is based on [fuse-jna], JGitFS should work with:

* OS X with [MacFUSE]/[fuse4x]/[OSXFUSE] on Intel architectures
* Linux with [FUSE][Linux-Fuse] on Intel and PowerPC architectures
* FreeBSD with [FUSE][FreeBSD-Fuse] on Intel architectures

#### Licensing
JGitFS is licensed under the [BSD 2-Clause License].
[fuse-jna] is licensed under the [BSD 2-Clause License].
[JNA] is licensed under the [LGPL v2.1].
[JGit] is licensed under the [EDL]. 
The Apache Commons `io` and `lang` libraries are licensed under the [Apache 2.0 License] 

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
