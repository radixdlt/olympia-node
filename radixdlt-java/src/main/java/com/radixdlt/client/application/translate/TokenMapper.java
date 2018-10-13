package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.actions.CreateFixedSupplyToken;
import com.radixdlt.client.core.atoms.TokenRef;
import com.radixdlt.client.core.atoms.particles.Consumable;
import com.radixdlt.client.core.atoms.particles.Consumable.ConsumableType;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
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
		Consumable minted = new Consumable(
			tokenCreation.getFixedSupply() * TokenRef.SUB_UNITS,
			ConsumableType.MINTED,
			tokenCreation.getAccountReference(),
			System.currentTimeMillis(),
			token.getTokenRef(),
			System.currentTimeMillis() / 60000L + 60000, Spin.UP
		);

		return Arrays.asList(token, minted);
	}
}
