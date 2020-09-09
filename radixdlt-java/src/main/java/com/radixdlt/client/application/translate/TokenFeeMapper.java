/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.application.translate;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.application.translate.tokens.BurnTokensAction;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.serialization.Serialize;
import com.radixdlt.fees.FeeTable;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maps a complete list of particles ready to be submitted to a token fee particle group.
 */
public final class TokenFeeMapper implements FeeMapper {

	private final RRI tokenRri;
	private final FeeTable feeTable;

	public TokenFeeMapper(RRI tokenRri, FeeTable feeTable) {
		this.tokenRri = Objects.requireNonNull(tokenRri);
		this.feeTable = Objects.requireNonNull(feeTable);
	}

	@Override
	public Pair<Map<String, String>, List<ParticleGroup>> map(ActionProcessor actionProcessor, RadixAddress address, Atom atom) {
		UInt256 feeToPay = feeFor(atom);
		final List<ParticleGroup> feeParticleGroups = feeToPay.isZero()
			? ImmutableList.of()
			: feeParticles(actionProcessor, address, feeToPay);
		return Pair.of(ImmutableMap.of(), feeParticleGroups);
	}

	private List<ParticleGroup> feeParticles(ActionProcessor actionProcessor, RadixAddress address, UInt256 feeToPay) {
		BurnTokensAction fee = BurnTokensAction.create(
			this.tokenRri,
			address,
			TokenUnitConversions.subunitsToUnits(feeToPay)
		);
		return actionProcessor.process(fee);
	}

	private UInt256 feeFor(Atom atom) {
		int feeSize = Serialize.getInstance().toDson(atom, Output.HASH).length;
		ImmutableSet<Particle> outputs = atom.spunParticles()
			.filter(sp -> Spin.UP.equals(sp.getSpin()))
			.map(SpunParticle::getParticle)
			.collect(ImmutableSet.toImmutableSet());
		return this.feeTable.feeFor(atom, outputs, feeSize);
	}
}
