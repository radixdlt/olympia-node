package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.actions.CreateTokenAction;
import com.radixdlt.client.application.actions.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.atommodel.quarks.FungibleQuark.FungibleType;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.atommodel.tokens.TokenParticle;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.atommodel.quarks.FungibleQuark;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

/**
 * Maps the CreateToken action into it's corresponding particles
 */
public class TokenMapper {
	public List<SpunParticle> map(CreateTokenAction tokenCreation) {
		if (tokenCreation == null) {
			return Collections.emptyList();
		}

		final TokenPermission mintPermissions;
		if (tokenCreation.getTokenSupplyType().equals(TokenSupplyType.FIXED)) {
			mintPermissions = TokenPermission.SAME_ATOM_ONLY;
		} else if (tokenCreation.getTokenSupplyType().equals(TokenSupplyType.MUTABLE)) {
			mintPermissions = TokenPermission.TOKEN_OWNER_ONLY;
		} else {
			throw new IllegalStateException("Unknown supply type: " + tokenCreation.getTokenSupplyType());
		}

		TokenParticle token = new TokenParticle(
				tokenCreation.getAddress(),
				tokenCreation.getName(),
				tokenCreation.getIso(),
				tokenCreation.getDescription(),
				new EnumMap<FungibleType, TokenPermission>(FungibleType.class) {{
					this.put(FungibleType.MINTED, mintPermissions);
					this.put(FungibleType.BURNED, TokenPermission.NONE);
					this.put(FungibleType.TRANSFERRED, TokenPermission.ALL);
				}},
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
