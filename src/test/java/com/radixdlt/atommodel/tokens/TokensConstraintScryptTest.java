package com.radixdlt.atommodel.tokens;

import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.atomos.RRI;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atomos.test.TestAtomOS;
import com.radixdlt.constraintmachine.AtomMetadata;
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
	private static TestAtomOS testAtomOS = new TestAtomOS();

	@BeforeClass
	public static void initializeConstraintScrypt() {
		TokensConstraintScrypt tokensConstraintScrypt = new TokensConstraintScrypt();
		tokensConstraintScrypt.main(testAtomOS);
	}

	@Test
	public void when_validating_token_class_particle_without_symbol__result_has_error() {
		TokenDefinitionParticle token = PowerMockito.mock(TokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "token"));

		testAtomOS
			.testInitialParticle(token, PowerMockito.mock(AtomMetadata.class))
			.assertErrorWithMessageContaining("Symbol: no symbol provided");
	}

	@Test
	public void when_validating_token_class_particle_with_too_long_symbol__result_has_error() {
		TokenDefinitionParticle token = PowerMockito.mock(TokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "token"));
		Mockito.when(token.getSymbol()).thenReturn("TEEEEEEEEEEEEEEEEEEEEEEEEEEST");
		testAtomOS
			.testInitialParticle(token, PowerMockito.mock(AtomMetadata.class))
			.assertErrorWithMessageContaining("Symbol: invalid length");
	}

	@Test
	public void when_validating_token_class_particle_with_too_short_symbol__result_has_error() {
		TokenDefinitionParticle token = PowerMockito.mock(TokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "token"));
		Mockito.when(token.getSymbol()).thenReturn("");
		testAtomOS
			.testInitialParticle(token, PowerMockito.mock(AtomMetadata.class))
			.assertErrorWithMessageContaining("Symbol: invalid length");
	}

	@Test
	public void when_validating_token_class_particle_with_valid_symbol__result_has_no_symbol_errors() {
		TokenDefinitionParticle token = PowerMockito.mock(TokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "token"));
		Mockito.when(token.getSymbol()).thenReturn("TEST");
		testAtomOS
			.testInitialParticle(token, PowerMockito.mock(AtomMetadata.class))
			.assertNoErrorWithMessageContaining("Symbol:");
	}

	@Test
	public void when_validating_token_class_particle_without_description__result_has_error() {
		TokenDefinitionParticle token = PowerMockito.mock(TokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "token"));
		testAtomOS
			.testInitialParticle(token, PowerMockito.mock(AtomMetadata.class))
			.assertErrorWithMessageContaining("Description: no or empty provided");
	}

	@Test
	public void when_validating_token_class_particle_with_too_long_description__result_has_error() {
		TokenDefinitionParticle token = PowerMockito.mock(TokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "token"));
		Mockito.when(token.getDescription()).thenReturn(
			IntStream.range(0, TokenDefinitionParticle.MAX_DESCRIPTION_LENGTH + 1).mapToObj(i -> "c").collect(Collectors.joining()));
		testAtomOS
			.testInitialParticle(token, PowerMockito.mock(AtomMetadata.class))
			.assertErrorWithMessageContaining("Description: invalid length");
	}

	@Test
	public void when_validating_token_class_particle_with_valid_description__result_has_no_description_errors() {
		TokenDefinitionParticle token = PowerMockito.mock(TokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "token"));
		Mockito.when(token.getDescription()).thenReturn("Valid test description.");
		testAtomOS
			.testInitialParticle(token, PowerMockito.mock(AtomMetadata.class))
			.assertNoErrorWithMessageContaining("Description:");
	}

	@Test
	public void when_validating_token_class_particle_with_all_permissions_set__result_has_no_permission_errors() {
		TokenDefinitionParticle token = PowerMockito.mock(TokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "token"));
		Map<TokenTransition, TokenPermission> permissions = ImmutableMap.of(
			TokenTransition.MINT, TokenPermission.TOKEN_CREATION_ONLY,
			TokenTransition.BURN, TokenPermission.TOKEN_CREATION_ONLY
		);

		Mockito.when(token.getTokenPermissions()).thenReturn(permissions);
		testAtomOS
			.testInitialParticle(token, PowerMockito.mock(AtomMetadata.class))
			.assertNoErrorWithMessageContaining("Permissions:");
	}

	@Test
	public void when_validating_token_class_particle_without_permissions_set__result_has_error() {
		TokenDefinitionParticle token = PowerMockito.mock(TokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "token"));
		Mockito.when(token.getTokenPermissions()).thenReturn(new HashMap<>());
		testAtomOS
			.testInitialParticle(token, PowerMockito.mock(AtomMetadata.class))
			.assertErrorWithMessageContaining("Permissions: must be set");
	}

	@Test
	public void when_validating_token_class_particle_with_invalid_icon_url__result_has_error() {
		TokenDefinitionParticle token = PowerMockito.mock(TokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "token"));
		Mockito.when(token.getIconUrl()).thenReturn("this is not a url");
		testAtomOS
			.testInitialParticle(token, PowerMockito.mock(AtomMetadata.class))
			.assertErrorWithMessageContaining("Icon: not a valid URL");
	}

	@Test
	public void when_validating_token_class_particle_with_valid_icon__result_has_no_error() {
		TokenDefinitionParticle token = PowerMockito.mock(TokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "token"));
		Mockito.when(token.getIconUrl()).thenReturn("https://assets.radixdlt.com/test.png");
		testAtomOS
			.testInitialParticle(token, PowerMockito.mock(AtomMetadata.class))
			.assertNoErrorWithMessageContaining("Icon:");
	}

	@Test
	public void when_validating_token_class_particle_without_owner_signature__result_has_no_error() {
		TokenDefinitionParticle token = PowerMockito.mock(TokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "token"));
		RadixAddress owner = PowerMockito.mock(RadixAddress.class);
		Mockito.when(token.getAddress()).thenReturn(owner);
		AtomMetadata metadata = PowerMockito.mock(AtomMetadata.class);
		Mockito.when(metadata.isSignedBy(owner)).thenReturn(false);
		testAtomOS.testInitialParticle(token, metadata);
	}

	@Test
	public void when_validating_token_class_particle_with_owner_signature__result_has_no_error() {
		TokenDefinitionParticle token = PowerMockito.mock(TokenDefinitionParticle.class);
		RadixAddress owner = PowerMockito.mock(RadixAddress.class);
		when(token.getRRI()).thenReturn(RRI.of(owner, "token"));
		Mockito.when(token.getAddress()).thenReturn(owner);
		AtomMetadata metadata = PowerMockito.mock(AtomMetadata.class);
		Mockito.when(metadata.isSignedBy(owner)).thenReturn(true);
		testAtomOS
			.testInitialParticle(token, metadata)
			.assertNoErrorWithMessageContaining("sign");
	}

	@Test
	public void when_validating_token_instance_with_zero_amount__result_has_error() {
		TransferrableTokensParticle transferrableTokensParticle = mock(TransferrableTokensParticle.class);
		when(transferrableTokensParticle.getAmount()).thenReturn(UInt256.ZERO);
		testAtomOS.testInitialParticle(transferrableTokensParticle, mock(AtomMetadata.class))
			.assertErrorWithMessageContaining("zero");

		UnallocatedTokensParticle burnedTokensParticle = mock(UnallocatedTokensParticle.class);
		when(burnedTokensParticle.getAmount()).thenReturn(UInt256.ZERO);
		testAtomOS.testInitialParticle(burnedTokensParticle, mock(AtomMetadata.class))
			.assertErrorWithMessageContaining("zero");
	}
}