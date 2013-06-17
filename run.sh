#!/bin/sh

java -cp bin:../fuse-jna/lib/jna/jna-3.5.2.jar:lib/org.eclipse.jgit-2.3.1.201302201838-r.jar:../fuse-jna/bin  org.dstadler.jgitfs.JGitFS $*
