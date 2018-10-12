package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.actions.CreateFixedSupplyToken;
import com.radixdlt.client.core.atoms.TokenRef;
import com.radixdlt.client.core.atoms.particles.Minted;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.TokenParticle;
import com.radixdlt.client.core.atoms.particles.TokenParticle.MintPermissions;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Maps the CreateFixedSupplyToken action into it's corresponding particles
 */
public class TokenMapper {
	public List<Particle> map(CreateFixedSupplyToken tokenCreation) {
		if (tokenCreation == null) {
			return Collections.emptyList();
		}

		TokenParticle token = new TokenParticle(
			tokenCreation.getAccountReference(),
			tokenCreation.getName(),
			tokenCreation.getIso(),
			tokenCreation.getDescription(),
			MintPermissions.SAME_ATOM_ONLY,
			null
		);
		Minted minted = new Minted(
			tokenCreation.getFixedSupply() * TokenRef.SUB_UNITS,
			tokenCreation.getAccountReference(),
			System.currentTimeMillis(),
			token.getTokenRef(),
			System.currentTimeMillis() / 60000L + 60000
		);

		return Arrays.asList(token, minted);
	}
}
