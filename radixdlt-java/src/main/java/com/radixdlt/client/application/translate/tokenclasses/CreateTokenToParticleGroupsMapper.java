package com.radixdlt.client.application.translate.tokenclasses;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.StatelessActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.tokenclasses.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.atommodel.quarks.FungibleQuark;
import com.radixdlt.client.atommodel.quarks.FungibleQuark.FungibleType;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TokenParticle;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

import io.reactivex.Observable;

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
			mintPermissions = TokenPermission.SAME_ATOM_ONLY;
			burnPermissions = TokenPermission.NONE;
		} else if (tokenCreation.getTokenSupplyType().equals(TokenSupplyType.MUTABLE)) {
			mintPermissions = TokenPermission.TOKEN_OWNER_ONLY;
			burnPermissions = TokenPermission.TOKEN_OWNER_ONLY;
		} else {
			throw new IllegalStateException("Unknown supply type: " + tokenCreation.getTokenSupplyType());
		}

		TokenParticle token = new TokenParticle(
				tokenCreation.getAddress(),
			tokenCreation.getName(),
				tokenCreation.getIso(),
				tokenCreation.getDescription(),
				tokenCreation.getGranularity(),
				ImmutableMap.of(
					FungibleType.MINTED, mintPermissions,
					FungibleType.BURNED, burnPermissions,
					FungibleType.TRANSFERRED, TokenPermission.ALL
				),
				null
		);

		if (tokenCreation.getInitialSupply().isZero()) {
			// No initial supply -> just the token particle
			return Observable.just(ParticleGroup.of(SpunParticle.up(token)));
		}

		OwnedTokensParticle minted = new OwnedTokensParticle(
				tokenCreation.getInitialSupply(),
				tokenCreation.getGranularity(),
				FungibleQuark.FungibleType.MINTED,
				tokenCreation.getAddress(),
				System.currentTimeMillis(),
				token.getTokenClassReference(),
				System.currentTimeMillis() / 60000L + 60000
		);
		return Observable.just(ParticleGroup.of(SpunParticle.up(token), SpunParticle.up(minted)));
	}
}
