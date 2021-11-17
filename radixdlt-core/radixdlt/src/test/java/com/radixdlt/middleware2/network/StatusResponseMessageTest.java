package com.radixdlt.middleware2.network;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class StatusResponseMessageTest {
    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(StatusResponseMessage.class)
                .withIgnoredFields("instance")
                .suppress(Warning.NONFINAL_FIELDS)
                .withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void deserializationWithNullThrowsException() {
        new StatusResponseMessage(null);
    }
}