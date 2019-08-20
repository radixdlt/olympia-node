package com.radixdlt.atommodel.tokens;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.RRI;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.utils.UInt256;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;

public class TokensConstraintScryptTest {
	private static CMAtomOS cmAtomOS = new CMAtomOS();

	@BeforeClass
	public static void initializeConstraintScrypt() {
		TokensConstraintScrypt tokensConstraintScrypt = new TokensConstraintScrypt();
		cmAtomOS.load(tokensConstraintScrypt);
	}

	@Test
	public void when_validating_token_class_particle_with_too_long_symbol__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TEEEEEEEEEEEEEEEEEEEEEEEEEEST"));

		assertThat(cmAtomOS.testParticle(token).getErrorMessage())
			.contains("Symbol: invalid length");
	}

	@Test
	public void when_validating_token_class_particle_with_too_short_symbol__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), ""));
		assertThat(cmAtomOS.testParticle(token).getErrorMessage())
			.contains("Symbol: invalid length");
	}

	@Test
	public void when_validating_token_class_particle_without_description__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));

		assertThat(cmAtomOS.testParticle(token).isError())
			.isTrue();
	}

	@Test
	public void when_validating_token_class_particle_with_too_long_description__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TEST"));
		Mockito.when(token.getDescription()).thenReturn(
			IntStream.range(0, TokenDefinitionUtils.MAX_DESCRIPTION_LENGTH + 1).mapToObj(i -> "c").collect(Collectors.joining()));

		assertThat(cmAtomOS.testParticle(token).getErrorMessage())
			.contains("Description: invalid length");
	}

	@Test
	public void when_validating_valid_token_class_particle__result_has_no_errors() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TEST"));
		when(token.getDescription()).thenReturn("Valid test description.");
		Map<TokenTransition, TokenPermission> permissions = ImmutableMap.of(
			TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY,
			TokenTransition.BURN, TokenPermission.TOKEN_OWNER_ONLY
		);
		when(token.getTokenPermissions()).thenReturn(permissions);
		assertThat(cmAtomOS.testParticle(token).isSuccess())
			.isTrue();
	}

	@Test
	public void when_validating_token_class_particle_without_permissions_set__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		Mockito.when(token.getTokenPermissions()).thenReturn(new HashMap<>());
		when(token.getDescription()).thenReturn("Hello");
		assertThat(cmAtomOS.testParticle(token).getErrorMessage())
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
		Mockito.when(token.getIconUrl()).thenReturn("this is not a url");
		assertThat(cmAtomOS.testParticle(token).getErrorMessage())
			.contains("Icon: not a valid URL");
	}

	@Test
	public void when_validating_token_instance_with_zero_amount__result_has_error() {
		TransferrableTokensParticle transferrableTokensParticle = mock(TransferrableTokensParticle.class);
		when(transferrableTokensParticle.getAmount()).thenReturn(UInt256.ZERO);
		assertThat(cmAtomOS.testParticle(transferrableTokensParticle).getErrorMessage())
			.contains("zero");

		UnallocatedTokensParticle burnedTokensParticle = mock(UnallocatedTokensParticle.class);
		when(burnedTokensParticle.getAmount()).thenReturn(UInt256.ZERO);
		assertThat(cmAtomOS.testParticle(burnedTokensParticle).getErrorMessage())
			.contains("zero");
	}
}