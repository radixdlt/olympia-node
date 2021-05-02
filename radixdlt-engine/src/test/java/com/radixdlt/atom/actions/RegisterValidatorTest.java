package com.radixdlt.atom.actions;

import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class RegisterValidatorTest {

    @Test
    public void testNonNullableConstructorParams() {
        assertThrows(NullPointerException.class, () -> {
            RegisterValidator rv1 = new RegisterValidator(null, null, null);
        });
    }
}
