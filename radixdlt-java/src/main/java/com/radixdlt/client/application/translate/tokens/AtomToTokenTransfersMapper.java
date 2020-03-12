package com.radixdlt.client.application.translate.tokens;

import com.google.gson.JsonParser;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.AtomToExecutedActionsMapper;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
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
	private static final JsonParser JSON_PARSER = new JsonParser();

	public AtomToTokenTransfersMapper() {
	}

	@Override
	public Class<TokenTransfer> actionClass() {
		return TokenTransfer.class;
	}


	private BigDecimal consumableToAmount(SpunParticle<TransferrableTokensParticle> sp) {
		BigDecimal amount = TokenUnitConversions.subunitsToUnits(sp.getParticle().getAmount());
		return sp.getSpin() == Spin.DOWN ? amount.negate() : amount;
	}

	@Override
	public Observable<TokenTransfer> map(Atom atom, RadixIdentity identity) {
		List<TokenTransfer> tokenTransfers = atom.particleGroups()
			.flatMap(pg -> {
				Map<RRI, Map<RadixAddress, BigDecimal>> tokenSummary = pg.spunParticles()
					.filter(sp -> sp.getParticle() instanceof TransferrableTokensParticle)
					.map(sp -> (SpunParticle<TransferrableTokensParticle>) sp)
					.collect(
						Collectors.groupingBy(
							sp -> sp.getParticle().getTokenDefinitionReference(),
							Collectors.groupingBy(
								sp -> sp.getParticle().getAddress(),
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
							attachment == null ? null : Bytes.fromBase64String(attachment),
							atom.getTimestamp()
						);
					});
			})
			.collect(Collectors.toList());

		return Observable.fromIterable(tokenTransfers);
	}
}
