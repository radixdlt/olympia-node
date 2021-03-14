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

package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.AtomToExecutedActionsMapper;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.atom.SpunParticle;
import com.radixdlt.utils.Bytes;
import io.reactivex.Observable;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Maps an atom to some number of token transfer actions.
 */
public class AtomToTokenTransfersMapper implements AtomToExecutedActionsMapper<TokenTransfer> {

	public AtomToTokenTransfersMapper() {
		// Nothing to do here
	}

	@Override
	public Class<TokenTransfer> actionClass() {
		return TokenTransfer.class;
	}


	private BigDecimal consumableToAmount(SpunParticle sp) {
		BigDecimal amount = TokenUnitConversions.subunitsToUnits(sp.getParticle(TransferrableTokensParticle.class).getAmount());
		return sp.getSpin() == Spin.DOWN ? amount.negate() : amount;
	}

	@Override
	public Observable<TokenTransfer> map(Atom atom, RadixIdentity identity) {
		List<TokenTransfer> tokenTransfers = atom.particleGroups()
			.flatMap(pg -> {
				Map<RRI, Map<RadixAddress, BigDecimal>> tokenSummary = pg.spunParticles()
					.filter(sp -> sp.getParticle() instanceof TransferrableTokensParticle)
					.collect(
						Collectors.groupingBy(
							sp -> sp.getParticle(TransferrableTokensParticle.class).getTokenDefinitionReference(),
							Collectors.groupingBy(
								sp -> sp.getParticle(TransferrableTokensParticle.class).getAddress(),
								Collectors.reducing(BigDecimal.ZERO, this::consumableToAmount, BigDecimal::add)
							)
						)
					);

				return tokenSummary.entrySet().stream()
					.map(e -> {

						List<Entry<RadixAddress, BigDecimal>> summary = new ArrayList<>(e.getValue().entrySet());
						if (summary.size() > 2) {
							throw new IllegalStateException(
								"More than two participants in token transfer. " + "Unable to handle: " + summary
							);
						}

						final RadixAddress from;
						final RadixAddress to;
						if (summary.size() == 1) {
							from = summary.get(0).getValue().signum() <= 0 ? summary.get(0).getKey() : null;
							to = summary.get(0).getValue().signum() < 0 ? null : summary.get(0).getKey();
						} else {
							if (summary.get(0).getValue().signum() > 0) {
								from = summary.get(1).getKey();
								to = summary.get(0).getKey();
							} else {
								from = summary.get(0).getKey();
								to = summary.get(1).getKey();
							}
						}

						String attachment = pg.getMetaData().get("attachment");
						return new TokenTransfer(
							from,
							to,
							e.getKey(),
							summary.get(0).getValue().abs(),
							attachment == null ? null : Bytes.fromBase64String(attachment)
						);
					});
			})
			.collect(Collectors.toList());

		return Observable.fromIterable(tokenTransfers);
	}
}
