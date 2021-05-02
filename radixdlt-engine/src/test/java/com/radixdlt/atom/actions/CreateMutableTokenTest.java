package com.radixdlt.atom.actions;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class CreateMutableTokenTest {

    @Test
    public void testNonNullableConstructorParams() {
        assertThrows(NullPointerException.class, () -> {
            CreateMutableToken cmt1 = new CreateMutableToken(null, null, null, null, null);
        });
    }

    @Test
    public void testDefaultParameters() {
        CreateMutableToken cmt1 = new CreateMutableToken(
                "TEST",
                "Test Token",
                null,
                null,
                null
                );

        assertEquals("", cmt1.getDescription());
        assertEquals("", cmt1.getIconUrl());
        assertEquals("", cmt1.getTokenUrl());
    }
}
