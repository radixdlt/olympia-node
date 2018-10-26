package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.actions.CreateFixedSupplyTokenAction;
import com.radixdlt.client.core.atoms.TokenClassReference;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.atoms.particles.TokenParticle;
import com.radixdlt.client.core.atoms.particles.TokenParticle.MintPermissions;
import com.radixdlt.client.core.atoms.particles.TransferParticle;
import com.radixdlt.client.core.atoms.particles.quarks.FungibleQuark;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Maps the CreateFixedSupplyToken action into it's corresponding particles
 */
public class TokenMapper {
	public List<SpunParticle> map(CreateFixedSupplyTokenAction tokenCreation) {
		if (tokenCreation == null) {
			return Collections.emptyList();
		}

		TokenParticle token = new TokenParticle(
				tokenCreation.getAddress(),
				tokenCreation.getName(),
				tokenCreation.getIso(),
				tokenCreation.getDescription(),
				MintPermissions.SAME_ATOM_ONLY,
				null
		);
		TransferParticle minted = new TransferParticle(
				tokenCreation.getFixedSupply() * TokenClassReference.SUB_UNITS,
				FungibleQuark.FungibleType.MINTED,
				tokenCreation.getAddress(),
				System.currentTimeMillis(),
				token.getTokenClassReference(),
				System.currentTimeMillis() / 60000L + 60000
		);

		return Arrays.asList(SpunParticle.up(token), SpunParticle.up(minted));
	}
}
