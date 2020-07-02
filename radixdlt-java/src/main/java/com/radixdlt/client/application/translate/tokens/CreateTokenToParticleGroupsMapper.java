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

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.translate.StatelessActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.atommodel.rri.RRIParticle;
import com.radixdlt.client.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.client.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.client.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.ParticleGroup.ParticleGroupBuilder;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.radixdlt.utils.UInt256;

/**
 * Maps the CreateToken action into it's corresponding particles
 */
public class CreateTokenToParticleGroupsMapper implements StatelessActionToParticleGroupsMapper<CreateTokenAction> {
	@Override
	public List<ParticleGroup> mapToParticleGroups(CreateTokenAction tokenCreation) {
		if (tokenCreation.getTokenSupplyType().equals(TokenSupplyType.FIXED)) {
			return createFixedSupplyToken(tokenCreation);
		} else if (tokenCreation.getTokenSupplyType().equals(TokenSupplyType.MUTABLE)) {
			return createVariableSupplyToken(tokenCreation);
		} else {
			throw new IllegalStateException("Unknown supply type: " + tokenCreation.getTokenSupplyType());
		}
	}

	public List<ParticleGroup> createVariableSupplyToken(CreateTokenAction tokenCreation) {
		MutableSupplyTokenDefinitionParticle token = new MutableSupplyTokenDefinitionParticle(
			tokenCreation.getRRI().getAddress(),
			tokenCreation.getName(),
			tokenCreation.getRRI().getName(),
			tokenCreation.getDescription(),
			TokenUnitConversions.unitsToSubunits(tokenCreation.getGranularity()),
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY,
				TokenTransition.BURN, TokenPermission.TOKEN_OWNER_ONLY
			),
			tokenCreation.getIconUrl(),
			tokenCreation.getUrl()
		);

		UnallocatedTokensParticle unallocated = new UnallocatedTokensParticle(
			UInt256.MAX_VALUE,
			TokenUnitConversions.unitsToSubunits(tokenCreation.getGranularity()),
			System.currentTimeMillis(),
			token.getRRI(),
			token.getTokenPermissions()
		);

		RRIParticle rriParticle = new RRIParticle(token.getRRI());
		ParticleGroup tokenCreationGroup = ParticleGroup.of(
			SpunParticle.down(rriParticle),
			SpunParticle.up(token),
			SpunParticle.up(unallocated)
		);

		if (tokenCreation.getInitialSupply().compareTo(BigDecimal.ZERO) == 0) {
			// No initial supply -> just the token particle
			return Collections.singletonList(
				tokenCreationGroup
			);
		}

		TransferrableTokensParticle minted = new TransferrableTokensParticle(
			TokenUnitConversions.unitsToSubunits(tokenCreation.getInitialSupply()),
			TokenUnitConversions.unitsToSubunits(tokenCreation.getGranularity()),
			tokenCreation.getRRI().getAddress(),
			System.nanoTime(),
			token.getRRI(),
			System.currentTimeMillis() / 60000L + 60000,
			token.getTokenPermissions()
		);

		ParticleGroupBuilder mintGroupBuilder = ParticleGroup.builder()
			.addParticle(unallocated, Spin.DOWN)
			.addParticle(minted, Spin.UP);

		final UInt256 leftOver = UInt256.MAX_VALUE.subtract(TokenUnitConversions.unitsToSubunits(tokenCreation.getInitialSupply()));

		if (!leftOver.isZero()) {
			UnallocatedTokensParticle unallocatedLeftOver = new UnallocatedTokensParticle(
				leftOver,
				TokenUnitConversions.unitsToSubunits(tokenCreation.getGranularity()),
				System.currentTimeMillis(),
				token.getRRI(),
				token.getTokenPermissions()
			);

			mintGroupBuilder.addParticle(unallocatedLeftOver, Spin.UP);
		}

		return Arrays.asList(
			tokenCreationGroup,
			mintGroupBuilder.build()
		);
	}

	public List<ParticleGroup> createFixedSupplyToken(CreateTokenAction tokenCreation) {
		UInt256 amount = TokenUnitConversions.unitsToSubunits(tokenCreation.getInitialSupply());
		UInt256 granularity = TokenUnitConversions.unitsToSubunits(tokenCreation.getGranularity());
		FixedSupplyTokenDefinitionParticle token = new FixedSupplyTokenDefinitionParticle(
			tokenCreation.getRRI().getAddress(),
			tokenCreation.getName(),
			tokenCreation.getRRI().getName(),
			tokenCreation.getDescription(),
			amount,
			granularity,
			tokenCreation.getIconUrl(),
			tokenCreation.getUrl()
		);

		TransferrableTokensParticle tokens = new TransferrableTokensParticle(
			amount,
			granularity,
			token.getRRI().getAddress(),
			System.currentTimeMillis(),
			token.getRRI(),
			System.currentTimeMillis() / 60000L + 60000L,
			ImmutableMap.of()
		);

		RRIParticle rriParticle = new RRIParticle(token.getRRI());
		ParticleGroup tokenCreationGroup = ParticleGroup.of(
			SpunParticle.down(rriParticle),
			SpunParticle.up(token),
			SpunParticle.up(tokens)
		);

		return Arrays.asList(
			tokenCreationGroup
		);
	}
}
