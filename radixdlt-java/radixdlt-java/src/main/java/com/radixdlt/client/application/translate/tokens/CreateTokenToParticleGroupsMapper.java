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
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.client.application.translate.StatelessActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.atom.ParticleGroup.ParticleGroupBuilder;

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
			tokenCreation.getRRI(),
			tokenCreation.getName(),
			tokenCreation.getDescription(),
			TokenUnitConversions.unitsToSubunits(tokenCreation.getGranularity()),
			tokenCreation.getIconUrl(),
			tokenCreation.getUrl(),
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY,
				TokenTransition.BURN, TokenPermission.TOKEN_OWNER_ONLY
			)
		);

		UnallocatedTokensParticle unallocated = new UnallocatedTokensParticle(
			UInt256.MAX_VALUE,
			TokenUnitConversions.unitsToSubunits(tokenCreation.getGranularity()),
			token.getRRI(),
			token.getTokenPermissions(),
			System.currentTimeMillis()
		);

		RRIParticle rriParticle = new RRIParticle(token.getRRI());
		ParticleGroup tokenCreationGroup = ParticleGroup.builder()
			.virtualSpinDown(rriParticle)
			.spinUp(token)
			.spinUp(unallocated)
			.build();

		if (tokenCreation.getInitialSupply().compareTo(BigDecimal.ZERO) == 0) {
			// No initial supply -> just the token particle
			return Collections.singletonList(
				tokenCreationGroup
			);
		}

		TransferrableTokensParticle minted = new TransferrableTokensParticle(
			tokenCreation.getRRI().getAddress(),
			TokenUnitConversions.unitsToSubunits(tokenCreation.getInitialSupply()),
			TokenUnitConversions.unitsToSubunits(tokenCreation.getGranularity()),
			token.getRRI(),
			token.getTokenPermissions(),
			System.nanoTime()
		);

		ParticleGroupBuilder mintGroupBuilder = ParticleGroup.builder()
			.spinDown(SubstateId.ofSubstate(unallocated))
			.spinUp(minted);

		final UInt256 leftOver = UInt256.MAX_VALUE.subtract(TokenUnitConversions.unitsToSubunits(tokenCreation.getInitialSupply()));

		if (!leftOver.isZero()) {
			UnallocatedTokensParticle unallocatedLeftOver = new UnallocatedTokensParticle(
				leftOver,
				TokenUnitConversions.unitsToSubunits(tokenCreation.getGranularity()),
				token.getRRI(),
				token.getTokenPermissions(),
				System.currentTimeMillis()
			);

			mintGroupBuilder.spinUp(unallocatedLeftOver);
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
			tokenCreation.getRRI(),
			tokenCreation.getName(),
			tokenCreation.getDescription(),
			amount,
			granularity,
			tokenCreation.getIconUrl(),
			tokenCreation.getUrl()
		);

		TransferrableTokensParticle tokens = new TransferrableTokensParticle(
			token.getRRI().getAddress(),
			amount,
			granularity,
			token.getRRI(),
			ImmutableMap.of(),
			System.currentTimeMillis()
		);

		RRIParticle rriParticle = new RRIParticle(token.getRRI());
		ParticleGroup tokenCreationGroup = ParticleGroup.builder()
			.virtualSpinDown(rriParticle)
			.spinUp(token)
			.spinUp(tokens)
			.build();

		return Collections.singletonList(tokenCreationGroup);
	}
}
