package com.radixdlt.checkpoint;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokDefParticleFactory;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import org.radix.TokenIssuance;

import java.util.Map;

public final class CheckpointUtils {
	private CheckpointUtils() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static ImmutableList<SpunParticle> createEpochUpdate() {
		ImmutableList.Builder<SpunParticle> particles = ImmutableList.builder();
		particles.add(SpunParticle.down(new SystemParticle(0, 0, 0)));
		particles.add(SpunParticle.up(new SystemParticle(1, 0, 0)));
		return particles.build();
	}

	public static ImmutableList<SpunParticle> createTokenDefinition(
		byte magic,
		ECPublicKey key,
		String symbol,
		String name,
		String description,
		String iconUrl,
		String tokenUrl,
		Map<MutableSupplyTokenDefinitionParticle.TokenTransition, TokenPermission> tokenPermissions,
		UInt256 selfIssuance,
		ImmutableList<TokenIssuance> issuances
	) {
		final var universeAddress = new RadixAddress(magic, key);

		final var tokenRRI = RRI.of(universeAddress, symbol);

		final var particles = ImmutableList.<SpunParticle>builder();

		particles.add(SpunParticle.down(new RRIParticle(tokenRRI)));
		particles.add(SpunParticle.up(new MutableSupplyTokenDefinitionParticle(
			tokenRRI,
			name,
			description,
			UInt256.ONE,
			iconUrl,
			tokenUrl,
			tokenPermissions
		)));
		TokDefParticleFactory tokDefParticleFactory = TokDefParticleFactory.create(tokenRRI, tokenPermissions, UInt256.ONE);

		var issuedTokens = UInt384.from(selfIssuance);
		if (!selfIssuance.isZero()) {
			particles.add(SpunParticle.up(tokDefParticleFactory.createTransferrable(universeAddress, selfIssuance)));
		}
		// Merge issuances so we only have one TTP per address
		final var issuedAmounts = issuances.stream()
				.collect(ImmutableMap.toImmutableMap(TokenIssuance::receiver, TokenIssuance::amount, UInt256::add));
		for (final var issuance : issuedAmounts.entrySet()) {
			final var amount = issuance.getValue();
			if (!amount.isZero()) {
				particles.add(SpunParticle.up(
					tokDefParticleFactory.createTransferrable(new RadixAddress(magic, issuance.getKey()), amount)
				));
				issuedTokens = issuedTokens.add(amount);
			}
		}
		if (!issuedTokens.getHigh().isZero()) {
			// TokenOverflowException
			throw new IllegalStateException("Too many issued tokens: " + issuedTokens);
		}
		if (!issuedTokens.getLow().equals(UInt256.MAX_VALUE)) {
			particles.add(SpunParticle.up(tokDefParticleFactory.createUnallocated(UInt256.MAX_VALUE.subtract(issuedTokens.getLow()))));
		}
		return particles.build();
	}
}
