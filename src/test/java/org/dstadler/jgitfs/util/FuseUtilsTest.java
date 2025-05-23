package org.dstadler.jgitfs.util;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;


public class FuseUtilsTest {

    @Test
    public void testPrepareMountpoint() throws Exception {
        FuseUtils.prepareMountpoint(new File("."));
    }

    @Test
    public void testPrepareMountpointFails() {
        // how can we fail when trying to use a mountpoint?
        //FuseUtils.prepareMountpoint(new File("/tmp/!\"§$%&\t\r\n/()/=)%($§§\"$\\ÖÄ'*'*#+_:;:--.,;"));

        try {
            FuseUtils.prepareMountpoint(new File("/proc/cpu/mem/some"));
            if (!SystemUtils.IS_OS_WINDOWS) {
                fail("Should not be able to create the invalid mountpoint");
            }
        } catch (@SuppressWarnings("unused") IOException e) {
            // expected
        }
    }

    // helper method to get coverage of the unused constructor
    @Test
    public void testPrivateConstructor() throws Exception {
        org.dstadler.commons.testing.PrivateConstructorCoverage.executePrivateConstructor(FuseUtils.class);
    }
}
