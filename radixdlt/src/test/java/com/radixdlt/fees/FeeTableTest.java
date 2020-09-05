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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.message.MessageParticle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.ClientAtom.LedgerAtomConversionException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import nl.jqno.equalsverifier.EqualsVerifier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.radixdlt.utils.UInt256;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class FeeTableTest {
    private static final UInt256 MINIMUM_FEE = UInt256.FIVE;
	private static final ImmutableList<FeeEntry> FEE_ENTRIES = ImmutableList.of(
		PerParticleFeeEntry.of(0, MessageParticle.class, UInt256.THREE)
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
    public void testFeeForAtomNotMinimum() throws LedgerAtomConversionException {
    	FeeTable ft = get();
    	RadixAddress from = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
    	RadixAddress to = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
    	ImmutableList<ParticleGroup> particleGroups = ImmutableList.of(
    		ParticleGroup.of(SpunParticle.up(new MessageParticle(from, to, "test message 1".getBytes(StandardCharsets.UTF_8)))),
    		ParticleGroup.of(SpunParticle.up(new MessageParticle(from, to, "test message 2".getBytes(StandardCharsets.UTF_8))))
    	);
    	Atom a = new Atom(particleGroups, ImmutableMap.of(), ImmutableMap.of());
    	ClientAtom ca = ClientAtom.convertFromApiAtom(a);
    	UInt256 fee = ft.feeFor(ca, a.particles(Spin.UP).collect(ImmutableSet.toImmutableSet()));
    	assertEquals(UInt256.SIX, fee);
    }

    @Test
    public void testFeeForAtomMinimum() throws LedgerAtomConversionException {
    	FeeTable ft = get();
    	Atom a = new Atom(ImmutableList.of(), ImmutableMap.of(), ImmutableMap.of());
    	ClientAtom ca = ClientAtom.convertFromApiAtom(a);
    	UInt256 fee = ft.feeFor(ca, ImmutableSet.of());
    	assertEquals(UInt256.FIVE, fee);
    }

    @Test(expected = ArithmeticException.class)
    public void testFeeOverflow() throws LedgerAtomConversionException {
    	ImmutableList<FeeEntry> feeEntries = ImmutableList.of(
			PerParticleFeeEntry.of(0, MessageParticle.class, UInt256.MAX_VALUE)
		);
    	FeeTable ft = FeeTable.from(MINIMUM_FEE, feeEntries);
    	RadixAddress from = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
    	RadixAddress to = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
    	ImmutableList<ParticleGroup> particleGroups = ImmutableList.of(
    		ParticleGroup.of(SpunParticle.up(new MessageParticle(from, to, "test message 3".getBytes(StandardCharsets.UTF_8)))),
    		ParticleGroup.of(SpunParticle.up(new MessageParticle(from, to, "test message 4".getBytes(StandardCharsets.UTF_8))))
    	);
    	Atom a = new Atom(particleGroups, ImmutableMap.of(), ImmutableMap.of());
    	ClientAtom ca = ClientAtom.convertFromApiAtom(a);
    	ft.feeFor(ca, a.particles(Spin.UP).collect(ImmutableSet.toImmutableSet()));
    	fail();
    }


    @Test
    public void testToString() {
    	String s = get().toString();
    	assertThat(s, containsString(FeeTable.class.getSimpleName()));
    }

    private static FeeTable get() {
    	return FeeTable.from(MINIMUM_FEE, FEE_ENTRIES);
    }
}
