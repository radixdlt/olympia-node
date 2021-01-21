package com.radixdlt.atommodel.tokens;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class MutableSupplyTokenDefinitionParticleTest {
    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(MutableSupplyTokenDefinitionParticle.class)
                .suppress(Warning.NONFINAL_FIELDS)
                .withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
                .verify();
    }
}
