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

        ECKeyPair k1 = ECKeyPair.generateNew();
        ECDSASignature s1 = new ECDSASignature(BigInteger.ONE, BigInteger.ONE);

        return new ECDSASignatures(
                ImmutableMap.of(
                    Objects.requireNonNull(k1).getPublicKey(), s1
                )
        );
    }
}
