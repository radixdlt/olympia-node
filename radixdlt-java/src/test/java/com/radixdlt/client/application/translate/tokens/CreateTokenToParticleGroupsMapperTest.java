package com.radixdlt.client.application.translate.tokens;

import java.util.List;

import com.radixdlt.client.atommodel.tokens.MintedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import org.junit.Test;

import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.crypto.ECPublicKey;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.observers.TestObserver;

public class CreateTokenToParticleGroupsMapperTest {
	@Test
	public void testNormalConstruction() {
		CreateTokenAction tokenCreation = mock(CreateTokenAction.class);
		RadixAddress address = mock(RadixAddress.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(address.getPublicKey()).thenReturn(key);
		when(tokenCreation.getAddress()).thenReturn(address);
		when(tokenCreation.getIso()).thenReturn("ISO");
		when(tokenCreation.getInitialSupply()).thenReturn(TokenUnitConversions.getMinimumGranularity());
		when(tokenCreation.getGranularity()).thenReturn(TokenUnitConversions.getMinimumGranularity());
		when(tokenCreation.getTokenSupplyType()).thenReturn(TokenSupplyType.MUTABLE);

		CreateTokenToParticleGroupsMapper createTokenToParticlesMapper = new CreateTokenToParticleGroupsMapper();
		TestObserver<List<ParticleGroup>> testObserver = TestObserver.create();
		createTokenToParticlesMapper.mapToParticleGroups(tokenCreation).toList().subscribe(testObserver);
		testObserver.assertValue(particleGroups ->
			particleGroups.stream()
				.flatMap(ParticleGroup::spunParticles)
				.anyMatch(s -> s.getParticle() instanceof TokenDefinitionParticle)
				&& particleGroups.stream().flatMap(ParticleGroup::spunParticles)
					.anyMatch(s -> s.getParticle() instanceof MintedTokensParticle)
				&& particleGroups.stream().flatMap(ParticleGroup::spunParticles).count() == 2
		);
	}
}