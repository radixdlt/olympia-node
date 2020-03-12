package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.atommodel.rri.RRIParticle;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

import java.util.List;
import java.util.function.Predicate;

import com.radixdlt.client.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.client.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
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
		when(address.getUID()).thenReturn(mock(EUID.class));
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
		when(address.getUID()).thenReturn(mock(EUID.class));
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