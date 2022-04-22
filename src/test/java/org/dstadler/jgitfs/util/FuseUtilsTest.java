package org.dstadler.jgitfs.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.SystemUtils;
import org.junit.Test;


public class FuseUtilsTest {

    @Test
    public void testPrepareMountpoint() throws Exception {
        FuseUtils object = new FuseUtils();
        assertNotNull(object);

        FuseUtils.prepareMountpoint(new File("."));
    }

    @Test
    public void testPrepareMountpointFails() {
        FuseUtils object = new FuseUtils();
        assertNotNull(object);

        // how can we fail when trying to use a mountpoint?
        //FuseUtils.prepareMountpoint(new File("/tmp/!\"§$%&\t\r\n/()/=)%($§§\"$\\ÖÄ'*'*#+_:;:--.,;"));

        try {
            FuseUtils.prepareMountpoint(new File("/proc/cpu/mem/some"));
            if(!SystemUtils.IS_OS_WINDOWS) {
            	fail("Should not be able to create the invalid mountpoint");
            }
        } catch (@SuppressWarnings("unused") IOException e) {
            // expected
        }
    }
}
