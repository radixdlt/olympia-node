package com.radixdlt.serialization;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.TestSetupUtils;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECKeyPair;
import org.junit.BeforeClass;

import java.math.BigInteger;
import java.util.Objects;

/**
 * JSON Serialization round trip of {@link ECDSASignatures}
 */
public class ECDSASignaturesSerializationTest extends SerializeObjectEngine<ECDSASignatures> {


    public ECDSASignaturesSerializationTest() {
        super(ECDSASignatures.class, ECDSASignaturesSerializationTest::getECDSASignatures);
    }

    @BeforeClass
    public static void startRadixTest() {
        TestSetupUtils.installBouncyCastleProvider();
    }

    private static ECDSASignatures getECDSASignatures() {
        var k1 = ECKeyPair.generateNew();
        var s1 = ECDSASignature.create(BigInteger.ONE, BigInteger.ONE, 1);

        return new ECDSASignatures(ImmutableMap.of(Objects.requireNonNull(k1).getPublicKey(), s1));
    }
}
