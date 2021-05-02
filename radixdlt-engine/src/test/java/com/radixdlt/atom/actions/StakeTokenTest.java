package com.radixdlt.atom.actions;

import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class StakeTokenTest {

    @Test
    public void testNonNullableConstructorParams() {
        assertThrows(NullPointerException.class, () -> {
            StakeTokens st1 = new StakeTokens(null, null, null);
        });
    }
}
