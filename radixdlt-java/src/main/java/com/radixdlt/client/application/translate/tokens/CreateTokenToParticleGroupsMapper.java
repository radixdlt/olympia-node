package com.radixdlt.client.application.translate.tokens;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.StatelessActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.atommodel.tokens.BurnedTokensParticle;
import com.radixdlt.client.atommodel.tokens.MintedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import io.reactivex.Observable;

import java.math.BigDecimal;
import org.radix.utils.UInt256;

/**
 * Maps the CreateToken action into it's corresponding particles
 */
public class CreateTokenToParticleGroupsMapper implements StatelessActionToParticleGroupsMapper {
	@Override
	public Observable<Action> sideEffects(Action action) {
		return Observable.empty();
	}

	@Override
	public Observable<ParticleGroup> mapToParticleGroups(Action action) {
		if (!(action instanceof CreateTokenAction)) {
			return Observable.empty();
		}

		CreateTokenAction tokenCreation = (CreateTokenAction) action;

		final TokenPermission mintPermissions;
		final TokenPermission burnPermissions;

		if (tokenCreation.getTokenSupplyType().equals(TokenSupplyType.FIXED)) {
			mintPermissions = TokenPermission.TOKEN_CREATION_ONLY;
			burnPermissions = TokenPermission.NONE;
		} else if (tokenCreation.getTokenSupplyType().equals(TokenSupplyType.MUTABLE)) {
			mintPermissions = TokenPermission.TOKEN_OWNER_ONLY;
			burnPermissions = TokenPermission.TOKEN_OWNER_ONLY;
		} else {
			throw new IllegalStateException("Unknown supply type: " + tokenCreation.getTokenSupplyType());
		}

		TokenDefinitionParticle token = new TokenDefinitionParticle(
			tokenCreation.getAddress(),
			tokenCreation.getName(),
			tokenCreation.getIso(),
			tokenCreation.getDescription(),
			TokenUnitConversions.unitsToSubunits(tokenCreation.getGranularity()),
			ImmutableMap.of(
				MintedTokensParticle.class, mintPermissions,
				BurnedTokensParticle.class, burnPermissions
			),
			null
		);

		UnallocatedTokensParticle unallocated = new UnallocatedTokensParticle(
			TokenUnitConversions.unitsToSubunits(tokenCreation.getInitialSupply()),
			TokenUnitConversions.unitsToSubunits(tokenCreation.getGranularity()),
			tokenCreation.getAddress(),
			System.currentTimeMillis(),
			token.getTokenDefinitionReference(),
			System.currentTimeMillis() / 60000L + 60000
		);

		if (tokenCreation.getInitialSupply().compareTo(BigDecimal.ZERO) == 0) {
			// No initial supply -> just the token particle
			return Observable.just(
				ParticleGroup.of(SpunParticle.up(token)),
				ParticleGroup.of(SpunParticle.up(unallocated))
			);
		}

		/*
		UnallocatedTokensParticle unallocatedLeftOver = new UnallocatedTokensParticle(
			TokenUnitConversions.unitsToSubunits(tokenCreation.getInitialSupply()),
			UInt256.MAX_VALUE.subtract(TokenUnitConversions.unitsToSubunits(tokenCreation.getInitialSupply())),
			TokenUnitConversions.unitsToSubunits(tokenCreation.getGranularity()),
			tokenCreation.getAddress(),
			System.currentTimeMillis(),
			token.getTokenDefinitionReference(),
			System.currentTimeMillis() / 60000L + 60000
		);
		*/

		MintedTokensParticle minted = new MintedTokensParticle(
			TokenUnitConversions.unitsToSubunits(tokenCreation.getInitialSupply()),
			TokenUnitConversions.unitsToSubunits(tokenCreation.getGranularity()),
			tokenCreation.getAddress(),
			System.nanoTime(),
			token.getTokenDefinitionReference(),
			System.currentTimeMillis() / 60000L + 60000
		);

		return Observable.just(
			ParticleGroup.of(SpunParticle.up(token)),
			ParticleGroup.of(SpunParticle.up(unallocated)),
			ParticleGroup.of(
				SpunParticle.down(unallocated),
				SpunParticle.up(minted)
			)
		);
	}
}
