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

import com.radixdlt.constraintmachine.Spin;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.atommodel.unique.UniqueParticle;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;

import nl.jqno.equalsverifier.EqualsVerifier;

public class FeeTableTest {
    private static final UInt256 MINIMUM_FEE = UInt256.FIVE;
	private static final ImmutableList<FeeEntry> FEE_ENTRIES = ImmutableList.of(
		PerParticleFeeEntry.of(UniqueParticle.class, 0, UInt256.THREE)
	);

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(FeeTable.class)
			.verify();
	}

    @Test
    public void testGetters() {
    	FeeTable ft = get();
    	assertEquals(MINIMUM_FEE, ft.minimumFee());
    	assertEquals(FEE_ENTRIES, ft.feeEntries());
    }

    @Test
    public void testFeeForAtomNotMinimum() {
    	FeeTable ft = get();
    	final var p1 = makeParticle("test message 1");
    	final var p2 = makeParticle("test message 2");
    	ImmutableList<ParticleGroup> particleGroups = ImmutableList.of(
    		ParticleGroup.of(SpunParticle.up(p1)),
    		ParticleGroup.of(SpunParticle.up(p2))
    	);
    	Atom a = Atom.create(particleGroups);
    	UInt256 fee = ft.feeFor(a, a.particles(Spin.UP).collect(ImmutableSet.toImmutableSet()), 0);
    	assertEquals(UInt256.SIX, fee);
    }

    @Test
    public void testFeeForAtomMinimum() {
    	FeeTable ft = get();
    	Atom a = Atom.create(ImmutableList.of());
    	UInt256 fee = ft.feeFor(a, ImmutableSet.of(), 0);
    	assertEquals(UInt256.FIVE, fee);
    }

    @Test
    public void testFeeOverflow() {
    	ImmutableList<FeeEntry> feeEntries = ImmutableList.of(
			PerParticleFeeEntry.of(UniqueParticle.class, 0, UInt256.MAX_VALUE),
			PerBytesFeeEntry.of(1, 0, UInt256.MAX_VALUE)
		);
    	FeeTable ft = FeeTable.from(MINIMUM_FEE, feeEntries);
    	final var p3 = makeParticle("test message 3");
    	Atom a = Atom.create(ParticleGroup.of(SpunParticle.up(p3)));
    	ImmutableSet<Particle> outputs = a.particles(Spin.UP).collect(ImmutableSet.toImmutableSet());
    	assertThatThrownBy(() -> ft.feeFor(a, outputs, 1))
    		.isInstanceOf(ArithmeticException.class)
    		.hasMessageStartingWith("Fee overflow");
    }


    @Test
    public void testToString() {
    	String s = get().toString();
    	assertThat(s).contains(FeeTable.class.getSimpleName());
    }

    private static FeeTable get() {
    	return FeeTable.from(MINIMUM_FEE, FEE_ENTRIES);
    }

    private static UniqueParticle makeParticle(String message) {
    	final var kp = ECKeyPair.generateNew();
    	final var address = new RadixAddress((byte) 0, kp.getPublicKey());
    	return new UniqueParticle(address, message);
    }
}
