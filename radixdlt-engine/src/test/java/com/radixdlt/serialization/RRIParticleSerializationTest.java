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
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.crypto.ECKeyPair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * JSON Serialization round trip of {@link RRIParticle} object.
 */
public class RRIParticleSerializationTest extends SerializeObjectEngine<RRIParticle> {
	private static final String NAME = "TEST";
	private static final ECKeyPair keyPair;
	private static final RadixAddress address;
	private static final RRI rri;

	static {
		keyPair = ECKeyPair.generateNew();
		address = new RadixAddress((byte) 123, keyPair.getPublicKey());
		rri = RRI.of(address, NAME);
	}

    public RRIParticleSerializationTest() {
        super(RRIParticle.class, RRIParticleSerializationTest::get);
    }

    @BeforeClass
    public static void startRRIParticleSerializationTest() {
        TestSetupUtils.installBouncyCastleProvider();
    }

    @Test
    public void testGetters() {
    	RRIParticle p = get();
    	assertEquals(rri, p.getRri());
    	assertEquals(0L, p.getNonce());
    }

    @Test
    public void testToString() {
    	String s = get().toString();
    	assertThat(s)
    		.contains(RRIParticle.class.getSimpleName())
    		.contains(rri.toString());
    }

    private static RRIParticle get() {
    	return new RRIParticle(rri);
    }
}
