package com.radixdlt.serialization;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.TestSetupUtils;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;

import org.junit.BeforeClass;

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
    	try {
    		Hash hash = Hash.random();

    		ECKeyPair k1 = new ECKeyPair();
    		ECDSASignature s1 = k1.sign(hash);

    		ECKeyPair k2 = new ECKeyPair();
    		ECDSASignature s2 = k2.sign(hash);

    		return new ECDSASignatures(
   				ImmutableMap.of(
					k1.getPublicKey(), s1,
					k2.getPublicKey(), s2
    			)
    		);
    	} catch (CryptoException e) {
    		throw new IllegalStateException("While generating keypair", e);
    	}
    }
}
