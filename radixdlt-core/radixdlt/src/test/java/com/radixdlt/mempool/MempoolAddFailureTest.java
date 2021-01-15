package com.radixdlt.mempool;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class MempoolAddFailureTest {
    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(MempoolAddFailure.class)
                .withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
                .verify();
    }
}