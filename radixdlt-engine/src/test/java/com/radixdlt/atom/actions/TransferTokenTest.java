package com.radixdlt.atom.actions;

import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class TransferTokenTest {

    @Test
    public void testNonNullableConstructorParams() {
        assertThrows(NullPointerException.class, () -> {
            new TransferToken(null, null, null, null);
        });
    }
}
