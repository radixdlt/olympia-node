package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.actions.CreateTokenAction;
import com.radixdlt.client.application.actions.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.atommodel.tokens.TokenParticle;
import com.radixdlt.client.atommodel.tokens.TokenParticle.MintPermissions;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.atommodel.quarks.FungibleQuark;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Maps the CreateToken action into it's corresponding particles
 */
public class TokenMapper {
	public List<SpunParticle> map(CreateTokenAction tokenCreation) {
		if (tokenCreation == null) {
			return Collections.emptyList();
		}

		final MintPermissions mintPermissions;
		if (tokenCreation.getTokenSupplyType().equals(TokenSupplyType.FIXED)) {
			mintPermissions = MintPermissions.SAME_ATOM_ONLY;
		} else if (tokenCreation.getTokenSupplyType().equals(TokenSupplyType.MUTABLE)) {
			mintPermissions = MintPermissions.OWNER_ONLY;
		} else {
			throw new IllegalStateException("Unknown supply type: " + tokenCreation.getTokenSupplyType());
		}

		TokenParticle token = new TokenParticle(
				tokenCreation.getAddress(),
				tokenCreation.getName(),
				tokenCreation.getIso(),
				tokenCreation.getDescription(),
				mintPermissions,
				null
		);
		OwnedTokensParticle minted = new OwnedTokensParticle(
				tokenCreation.getInitialSupply() * TokenClassReference.SUB_UNITS,
				FungibleQuark.FungibleType.MINTED,
				tokenCreation.getAddress(),
				System.currentTimeMillis(),
				token.getTokenClassReference(),
				System.currentTimeMillis() / 60000L + 60000
		);

		return Arrays.asList(SpunParticle.up(token), SpunParticle.up(minted));
	}
}
