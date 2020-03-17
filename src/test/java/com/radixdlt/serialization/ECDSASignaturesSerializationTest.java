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

import com.google.common.collect.ImmutableMap;
import com.radixdlt.TestSetupUtils;
import com.radixdlt.crypto.CryptoException;
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

        ECKeyPair k1 = null;
        try {
            k1 = new ECKeyPair();
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        ECDSASignature s1 = new ECDSASignature(BigInteger.ONE, BigInteger.ONE);

        return new ECDSASignatures(
                ImmutableMap.of(
                    Objects.requireNonNull(k1).getPublicKey(), s1
                )
        );
    }
}
