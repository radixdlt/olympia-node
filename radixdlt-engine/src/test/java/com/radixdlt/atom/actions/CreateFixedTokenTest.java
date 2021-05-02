package com.radixdlt.atom.actions;

import com.radixdlt.identifiers.REAddr;
import com.radixdlt.test.utils.TypedMocks;
import com.radixdlt.utils.UInt256;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class CreateFixedTokenTest {

    @Test
    public void testNonNullableConstructorParams() {
        assertThrows(NullPointerException.class, () -> {
            CreateFixedToken cft1 = new CreateFixedToken(null, null, null, null, null, null, null, null);
        });
    }

    @Test
    public void testDefaultParameters() {
        CreateFixedToken cft1 = new CreateFixedToken(
                TypedMocks.rmock(REAddr.class),
                TypedMocks.rmock(REAddr.class),
                "TEST",
                "Test Token",
                null,
                null,
                null,
                UInt256.from(10000));

        assertEquals("", cft1.getDescription());
        assertEquals("", cft1.getIconUrl());
        assertEquals("", cft1.getTokenUrl());
    }
}
