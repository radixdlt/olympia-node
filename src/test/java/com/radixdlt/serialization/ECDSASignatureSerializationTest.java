package com.radixdlt.serialization;

import com.radixdlt.TestSetupUtils;
import com.radixdlt.crypto.ECDSASignature;
import org.junit.BeforeClass;

import java.math.BigInteger;
import java.util.Random;
import java.util.function.Supplier;

/**
 * JSON Serialization round trip of {@link ECDSASignature}
 */
public class ECDSASignatureSerializationTest extends SerializeObjectEngine<ECDSASignature> {


	public ECDSASignatureSerializationTest() {
		super(ECDSASignature.class, ECDSASignatureSerializationTest::getECDSASignature);
	}

	@BeforeClass
	public static void startRadixTest() {
		TestSetupUtils.installBouncyCastleProvider();
	}

	private static ECDSASignature getECDSASignature() {
		Supplier<BigInteger> randomBigInt = () -> BigInteger.valueOf(new Random().nextLong());
		return new ECDSASignature(randomBigInt.get(), randomBigInt.get());
	}
}
