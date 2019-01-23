package com.radixdlt.client.util;

import com.radixdlt.client.core.address.RadixUniverseConfigs;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class MagicByteTest {

    @Test
    public void testMagicByte() {
        int magicIntAlphanet = 281411585;
        byte magicByte = (byte) (magicIntAlphanet & 0xff);
        /// The origin of this byte value is this library it self, commit: acbc5307cf5c9f7e1c30300f7438ef5dbc3bb629
        /// This value can be used as a reference for other Radix libraries, e.g. Swift.
        byte expected = (byte) 1;
        assertEquals(expected, magicByte);
    }
}
