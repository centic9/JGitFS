package org.dstadler.jgitfs.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.junit.Test;


public class FuseUtilsTest {

    @Test
    public void testPrepareMountpoint() throws Exception {
        assertNotNull(new FuseUtils());
        
        FuseUtils.prepareMountpoint(new File("."));
    }

    @Test
    public void testPrepareMountpointFails() throws Exception {
        assertNotNull(new FuseUtils());
        
        // how can we fail when trying to use a mountpoint?
        //FuseUtils.prepareMountpoint(new File("/tmp/!\"§$%&\t\r\n/()/=)%($§§\"$\\ÖÄ'*'*#+_:;:--.,;"));
        
        try {
            FuseUtils.prepareMountpoint(new File("/proc/123234"));
            fail("Should not be able to create the invalid mountpoint");
        } catch (IOException e) {
            // expected
        }
    }
}
