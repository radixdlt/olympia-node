package com.radixdlt.client.application.translate.tokens;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.client.core.atoms.particles.RRI;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.Test;

public class MintTokensActionMapperTest {
	@Test
	public void when_a_mint_action_is_created_with_zero_unallocated_tokens__then_a_overmint_exception_should_be_thrown() {
		MintTokensActionMapper mapper = new MintTokensActionMapper();
		RRI token = mock(RRI.class);
		MintTokensAction mintTokensAction = MintTokensAction.create(token, mock(RadixAddress.class), BigDecimal.ONE);
		TokenDefinitionParticle tokenDefinitionParticle = mock(TokenDefinitionParticle.class);
		when(tokenDefinitionParticle.getRRI()).thenReturn(token);

		assertThatThrownBy(() -> mapper.mapToParticleGroups(mintTokensAction, Stream.of(tokenDefinitionParticle)))
			.isInstanceOf(TokenOverMintException.class);
	}

	@Test
	public void when_a_mint_action_is_created_with_zero_particles__then_an_unknown_token_exception_should_be_thrown() {
		MintTokensActionMapper mapper = new MintTokensActionMapper();
		MintTokensAction mintTokensAction = MintTokensAction.create(mock(RRI.class), mock(RadixAddress.class), BigDecimal.ONE);
		assertThatThrownBy(() -> mapper.mapToParticleGroups(mintTokensAction, Stream.empty()))
			.isInstanceOf(UnknownTokenException.class);
	}

	@Test
	public void when_a_mint_action_is_created_with_no_corresponding_token_definition__then_an_unknown_token_exception_should_be_thrown() {
		MintTokensActionMapper mapper = new MintTokensActionMapper();
		MintTokensAction mintTokensAction = MintTokensAction.create(mock(RRI.class), mock(RadixAddress.class), BigDecimal.ONE);
		TokenDefinitionParticle tokenDefinitionParticle = mock(TokenDefinitionParticle.class);
		when(tokenDefinitionParticle.getRRI()).thenReturn(mock(RRI.class));

		assertThatThrownBy(() -> mapper.mapToParticleGroups(mintTokensAction, Stream.of(tokenDefinitionParticle)))
			.isInstanceOf(UnknownTokenException.class);
	}
}