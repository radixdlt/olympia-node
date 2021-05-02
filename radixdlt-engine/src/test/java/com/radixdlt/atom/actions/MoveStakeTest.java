package com.radixdlt.atom.actions;

import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class MoveStakeTest {

    @Test
    public void testNonNullableConstructorParams() {
        assertThrows(NullPointerException.class, () -> {
            MoveStake ms1 = new MoveStake(null, null, null, null);
        });
    }
}
