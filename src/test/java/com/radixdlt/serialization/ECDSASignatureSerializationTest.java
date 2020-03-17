/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

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
