package com.radixdlt.atom.actions;

import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class BurnTokenTest {

    @Test
    public void testNonNullableConstructorParams() {
        assertThrows(NullPointerException.class, () -> {
            BurnToken bt1 = new BurnToken(null, null, null);
        });
    }
}
