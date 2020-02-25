package com.radixdlt.serialization;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.TestSetupUtils;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECKeyPair;
import org.junit.BeforeClass;

import java.math.BigInteger;


/**
 * Serialization for Signatures to JSON.
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

        ECKeyPair k1 = null;
        try {
            k1 = new ECKeyPair();
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        ECDSASignature s1 = new ECDSASignature(BigInteger.ONE, BigInteger.ONE);

        return new ECDSASignatures(
                ImmutableMap.of(
                    k1.getPublicKey(), s1
                )
        );
    }
}
