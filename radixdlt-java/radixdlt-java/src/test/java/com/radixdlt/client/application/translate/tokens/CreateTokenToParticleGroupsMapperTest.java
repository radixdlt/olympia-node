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

import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.atom.SpunParticle;

import java.util.List;
import java.util.function.Predicate;

import com.radixdlt.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atom.ParticleGroup;
import org.junit.Test;

import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.EUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CreateTokenToParticleGroupsMapperTest {
	@Test
	public void testNormalMutableSupplyConstruction() {
		CreateTokenAction tokenCreation = mock(CreateTokenAction.class);
		RadixAddress address = mock(RadixAddress.class);
		when(address.euid()).thenReturn(mock(EUID.class));
		ECPublicKey key = mock(ECPublicKey.class);
		when(address.getPublicKey()).thenReturn(key);
		when(tokenCreation.getRRI()).thenReturn(RRI.of(address, "ISO"));
		when(tokenCreation.getInitialSupply()).thenReturn(TokenUnitConversions.getMinimumGranularity());
		when(tokenCreation.getGranularity()).thenReturn(TokenUnitConversions.getMinimumGranularity());
		when(tokenCreation.getTokenSupplyType()).thenReturn(TokenSupplyType.MUTABLE);

		CreateTokenToParticleGroupsMapper createTokenToParticlesMapper = new CreateTokenToParticleGroupsMapper();
		List<ParticleGroup> particleGroups = createTokenToParticlesMapper.mapToParticleGroups(tokenCreation);
		assertThat(particleGroups).anyMatch(hasInstanceOf(MutableSupplyTokenDefinitionParticle.class));
		assertThat(particleGroups).anyMatch(hasInstanceOf(TransferrableTokensParticle.class));
		assertThat(particleGroups).anyMatch(hasInstanceOf(UnallocatedTokensParticle.class));
		assertThat(particleGroups).anyMatch(hasInstanceOf(RRIParticle.class));
		assertThat(particleGroups.stream().flatMap(ParticleGroup::spunParticles).count()).isEqualTo(6);
	}

	@Test
	public void testNormalFixedSupplyConstruction() {
		CreateTokenAction tokenCreation = mock(CreateTokenAction.class);
		RadixAddress address = mock(RadixAddress.class);
		when(address.euid()).thenReturn(mock(EUID.class));
		ECPublicKey key = mock(ECPublicKey.class);
		when(address.getPublicKey()).thenReturn(key);
		when(tokenCreation.getRRI()).thenReturn(RRI.of(address, "ISO"));
		when(tokenCreation.getInitialSupply()).thenReturn(TokenUnitConversions.getMinimumGranularity());
		when(tokenCreation.getGranularity()).thenReturn(TokenUnitConversions.getMinimumGranularity());
		when(tokenCreation.getTokenSupplyType()).thenReturn(TokenSupplyType.FIXED);

		CreateTokenToParticleGroupsMapper createTokenToParticlesMapper = new CreateTokenToParticleGroupsMapper();
		List<ParticleGroup> particleGroups = createTokenToParticlesMapper.mapToParticleGroups(tokenCreation);
		assertThat(particleGroups).anyMatch(hasInstanceOf(FixedSupplyTokenDefinitionParticle.class));
		assertThat(particleGroups).anyMatch(hasInstanceOf(TransferrableTokensParticle.class));
		assertThat(particleGroups).anyMatch(hasInstanceOf(RRIParticle.class));
		assertThat(particleGroups.stream().flatMap(ParticleGroup::spunParticles).count()).isEqualTo(3);
	}

	private Predicate<ParticleGroup> hasInstanceOf(Class<? extends Particle> cls) {
		return pg -> pg.spunParticles()
			.map(SpunParticle::getParticle)
			.anyMatch(cls::isInstance);
	}
}