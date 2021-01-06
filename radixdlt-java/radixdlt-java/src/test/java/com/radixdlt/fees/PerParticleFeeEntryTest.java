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

package com.radixdlt.fees;

import static org.junit.Assert.assertEquals;
import nl.jqno.equalsverifier.EqualsVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;

import java.util.Set;

import org.assertj.core.util.Sets;
import org.junit.Test;

public class PerParticleFeeEntryTest {
	private static final Class<? extends Particle> TYPE = MessageParticle.class;
	private static final int THRESHOLD = 1;
    private static final UInt256 FEE = UInt256.ONE;

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(PerParticleFeeEntry.class)
			.verify();
	}

    @Test
    public void testGetters() {
    	PerParticleFeeEntry f = get();
    	assertEquals(TYPE, f.particleType());
    	assertEquals(THRESHOLD, f.threshold());
    	assertEquals(FEE, f.fee());
    }

    @Test
    public void testFeeForAtom() {
    	PerParticleFeeEntry f = get();
    	Set<Particle> outputs = Sets.newHashSet();
    	RadixAddress addr = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());

    	assertEquals(UInt256.ZERO, f.feeFor(null, 0, outputs));
    	outputs.add(MessageParticle.builder().from(addr).to(addr).payload("Particle 1".getBytes()).build());
    	assertEquals(UInt256.ZERO, f.feeFor(null, 0, outputs));
    	outputs.add(MessageParticle.builder().from(addr).to(addr).payload("Particle 2".getBytes()).build());
    	assertEquals(FEE, f.feeFor(null, 0, outputs));
    	outputs.add(MessageParticle.builder().from(addr).to(addr).payload("Particle 3".getBytes()).build());
    	assertEquals(FEE.multiply(UInt256.TWO), f.feeFor(null, 0, outputs));
    }

    @Test
    public void boundaryConditions() {
    	// Negative threshold
    	assertThatThrownBy(() -> PerParticleFeeEntry.of(TYPE, -1, UInt256.ONE))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageStartingWith("Threshold must be non-negative");

    	// Fee overflow
    	PerParticleFeeEntry f = PerParticleFeeEntry.of(TYPE, 0, UInt256.MAX_VALUE);
    	RadixAddress addr = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
    	ImmutableSet<Particle> outputs = ImmutableSet.of(
			MessageParticle.builder().from(addr).to(addr).payload("Particle 1".getBytes()).build(),
			MessageParticle.builder().from(addr).to(addr).payload("Particle 2".getBytes()).build()
    	);
    	assertThatThrownBy(() -> f.feeFor(null, 2, outputs))
    		.isInstanceOf(ArithmeticException.class)
    		.hasMessageStartingWith("Fee overflow");
    }

    @Test
    public void testToString() {
    	String s = get().toString();

    	assertThat(s).contains(PerParticleFeeEntry.class.getSimpleName());
    }

    private static PerParticleFeeEntry get() {
    	return PerParticleFeeEntry.of(TYPE, THRESHOLD, FEE);
    }
}
