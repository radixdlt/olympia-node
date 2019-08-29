package com.radixdlt.atommodel.tokens;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.RRI;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.utils.UInt256;
import java.util.HashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.BeforeClass;
import org.junit.Test;
import org.powermock.api.mockito.PowerMockito;

public class TokensConstraintScryptTest {
	private static Function<Particle, Result> staticCheck;

	@BeforeClass
	public static void initializeConstraintScrypt() {
		TokensConstraintScrypt tokensConstraintScrypt = new TokensConstraintScrypt();
		CMAtomOS cmAtomOS = new CMAtomOS();
		cmAtomOS.load(tokensConstraintScrypt);
		staticCheck = cmAtomOS.buildParticleStaticCheck();
	}

	@Test
	public void when_validating_mutable_token_def_particle_with_no_rri__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = mock(MutableSupplyTokenDefinitionParticle.class);
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("rri cannot be null");
	}

	@Test
	public void when_validating_fixed_token_def_particle_with_no_rri__result_has_error() {
		FixedSupplyTokenDefinitionParticle token = mock(FixedSupplyTokenDefinitionParticle.class);
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("rri cannot be null");
	}

	@Test
	public void when_validating_token_class_particle_with_too_long_symbol__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TEEEEEEEEEEEEEEEEEEEEEEEEEEST"));
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("Symbol: invalid length");
	}

	@Test
	public void when_validating_token_class_particle_with_too_short_symbol__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), ""));
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("Symbol: invalid length");
	}

	@Test
	public void when_validating_token_class_particle_without_description__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("Description: no or empty provided");
	}

	@Test
	public void when_validating_token_class_particle_with_too_long_description__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TEST"));
		when(token.getDescription()).thenReturn(
			IntStream.range(0, TokenDefinitionUtils.MAX_DESCRIPTION_LENGTH + 1).mapToObj(i -> "c").collect(Collectors.joining()));
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("Description: invalid length");
	}

	@Test
	public void when_validating_token_class_particle_without_permissions_set__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(token.getTokenPermissions()).thenReturn(new HashMap<>());
		when(token.getDescription()).thenReturn("Hello");
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("Permissions: must be set");
	}

	@Test
	public void when_validating_token_class_particle_with_invalid_icon_url__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(token.getDescription()).thenReturn("Hello");
		when(token.getTokenPermissions()).thenReturn(ImmutableMap.of(
			TokenTransition.MINT, TokenPermission.ALL,
			TokenTransition.BURN, TokenPermission.ALL
		));
		when(token.getIconUrl()).thenReturn("this is not a url");
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("Icon: not a valid URL");
	}

	@Test
	public void when_validating_token_instance_with_null_amount__result_has_error() {
		TransferrableTokensParticle transferrableTokensParticle = mock(TransferrableTokensParticle.class);
		when(transferrableTokensParticle.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(transferrableTokensParticle.getAmount()).thenReturn(null);
		assertThat(staticCheck.apply(transferrableTokensParticle).getErrorMessage())
			.contains("null");
	}

	@Test
	public void when_validating_unallocated_token_with_null_amount__result_has_error() {
		UnallocatedTokensParticle unallocated = mock(UnallocatedTokensParticle.class);
		when(unallocated.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(unallocated.getAmount()).thenReturn(null);
		assertThat(staticCheck.apply(unallocated).getErrorMessage())
			.contains("null");
	}

	@Test
	public void when_validating_token_instance_with_zero_amount__result_has_error() {
		TransferrableTokensParticle transferrableTokensParticle = mock(TransferrableTokensParticle.class);
		when(transferrableTokensParticle.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(transferrableTokensParticle.getAmount()).thenReturn(UInt256.ZERO);
		assertThat(staticCheck.apply(transferrableTokensParticle).getErrorMessage())
			.contains("zero");
	}

	@Test
	public void when_validating_unallocated_token_with_zero_amount__result_has_error() {
		UnallocatedTokensParticle burnedTokensParticle = mock(UnallocatedTokensParticle.class);
		when(burnedTokensParticle.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(burnedTokensParticle.getAmount()).thenReturn(UInt256.ZERO);
		assertThat(staticCheck.apply(burnedTokensParticle).getErrorMessage())
			.contains("zero");
	}

	@Test
	public void when_validating_token_instance_with_null_granularity__result_has_error() {
		TransferrableTokensParticle transferrableTokensParticle = mock(TransferrableTokensParticle.class);
		when(transferrableTokensParticle.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(transferrableTokensParticle.getAmount()).thenReturn(UInt256.ONE);
		when(transferrableTokensParticle.getGranularity()).thenReturn(null);
		assertThat(staticCheck.apply(transferrableTokensParticle).getErrorMessage())
			.contains("granularity");
	}

	@Test
	public void when_validating_unallocated_token_with_null_granularity__result_has_error() {
		UnallocatedTokensParticle unallocated = mock(UnallocatedTokensParticle.class);
		when(unallocated.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(unallocated.getAmount()).thenReturn(UInt256.ONE);
		when(unallocated.getGranularity()).thenReturn(null);
		assertThat(staticCheck.apply(unallocated).getErrorMessage())
			.contains("granularity");
	}

	@Test
	public void when_validating_unallocated_token_with_zero_granularity__result_has_error() {
		UnallocatedTokensParticle unallocated = mock(UnallocatedTokensParticle.class);
		when(unallocated.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(unallocated.getAmount()).thenReturn(UInt256.ONE);
		when(unallocated.getGranularity()).thenReturn(UInt256.ZERO);
		assertThat(staticCheck.apply(unallocated).getErrorMessage())
			.contains("granularity");
	}

	@Test
	public void when_validating_token_instance_with_amount_not_divisible_by_granularity__result_has_error() {
		TransferrableTokensParticle transferrableTokensParticle = mock(TransferrableTokensParticle.class);
		when(transferrableTokensParticle.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(transferrableTokensParticle.getAmount()).thenReturn(UInt256.THREE);
		when(transferrableTokensParticle.getGranularity()).thenReturn(UInt256.TWO);
		assertThat(staticCheck.apply(transferrableTokensParticle).getErrorMessage())
			.contains("granularity");
	}
}