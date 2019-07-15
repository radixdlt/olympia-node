package com.radixdlt.atommodel.tokens;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.BeforeClass;
import org.junit.Test;

import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.atomos.test.TestAtomOS;

import com.google.common.collect.ImmutableMap;

import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

public class TokenDefinitionConstraintScryptTest {
	private static TestAtomOS testAtomModelOS = new TestAtomOS();

	@BeforeClass
	public static void initializeConstraintScrypt() {
		TokenDefinitionConstraintScrypt tokenDefinitionSmartConstraint = new TokenDefinitionConstraintScrypt();
		tokenDefinitionSmartConstraint.main(testAtomModelOS);
	}

	@Test
	public void when_validating_token_class_particle_without_symbol__result_has_error() {
		TokenDefinitionParticle token = mock(TokenDefinitionParticle.class);
		testAtomModelOS
			.testInitialParticle(token, mock(AtomMetadata.class))
			.assertErrorWithMessageContaining("Symbol: no symbol provided");
	}

	@Test
	public void when_validating_token_class_particle_with_too_long_symbol__result_has_error() {
		TokenDefinitionParticle token = mock(TokenDefinitionParticle.class);
		when(token.getSymbol()).thenReturn("TEEEEEEEEEEEEEEEEEEEEEEEEEEST");
		testAtomModelOS
			.testInitialParticle(token, mock(AtomMetadata.class))
			.assertErrorWithMessageContaining("Symbol: invalid length");
	}

	@Test
	public void when_validating_token_class_particle_with_too_short_symbol__result_has_error() {
		TokenDefinitionParticle token = mock(TokenDefinitionParticle.class);
		when(token.getSymbol()).thenReturn("");
		testAtomModelOS
			.testInitialParticle(token, mock(AtomMetadata.class))
			.assertErrorWithMessageContaining("Symbol: invalid length");
	}

	@Test
	public void when_validating_token_class_particle_with_valid_symbol__result_has_no_symbol_errors() {
		TokenDefinitionParticle token = mock(TokenDefinitionParticle.class);
		when(token.getSymbol()).thenReturn("TEST");
		testAtomModelOS
			.testInitialParticle(token, mock(AtomMetadata.class))
			.assertNoErrorWithMessageContaining("Symbol:");
	}

	@Test
	public void when_validating_token_class_particle_without_description__result_has_error() {
		TokenDefinitionParticle token = mock(TokenDefinitionParticle.class);
		testAtomModelOS
			.testInitialParticle(token, mock(AtomMetadata.class))
			.assertErrorWithMessageContaining("Description: no or empty provided");
	}

	@Test
	public void when_validating_token_class_particle_with_too_long_description__result_has_error() {
		TokenDefinitionParticle token = mock(TokenDefinitionParticle.class);
		when(token.getDescription()).thenReturn(
			IntStream.range(0, TokenDefinitionParticle.MAX_DESCRIPTION_LENGTH + 1).mapToObj(i -> "c").collect(Collectors.joining()));
		testAtomModelOS
			.testInitialParticle(token, mock(AtomMetadata.class))
			.assertErrorWithMessageContaining("Description: invalid length");
	}

	@Test
	public void when_validating_token_class_particle_with_valid_description__result_has_no_description_errors() {
		TokenDefinitionParticle token = mock(TokenDefinitionParticle.class);
		when(token.getDescription()).thenReturn("Valid test description.");
		testAtomModelOS
			.testInitialParticle(token, mock(AtomMetadata.class))
			.assertNoErrorWithMessageContaining("Description:");
	}

	@Test
	public void when_validating_token_class_particle_with_all_permissions_set__result_has_no_permission_errors() {
		TokenDefinitionParticle token = mock(TokenDefinitionParticle.class);
		Map<TokenTransition, TokenPermission> permissions = ImmutableMap.of(
			TokenTransition.MINT, TokenPermission.TOKEN_CREATION_ONLY,
			TokenTransition.BURN, TokenPermission.TOKEN_CREATION_ONLY
		);

		when(token.getTokenPermissions()).thenReturn(permissions);
		testAtomModelOS
			.testInitialParticle(token, mock(AtomMetadata.class))
			.assertNoErrorWithMessageContaining("Permissions:");
	}

	@Test
	public void when_validating_token_class_particle_without_permissions_set__result_has_error() {
		TokenDefinitionParticle token = mock(TokenDefinitionParticle.class);
		when(token.getTokenPermissions()).thenReturn(new HashMap<>());
		testAtomModelOS
			.testInitialParticle(token, mock(AtomMetadata.class))
			.assertErrorWithMessageContaining("Permissions: must be set");
	}

	@Test
	public void when_validating_token_class_particle_with_invalid_icon_url__result_has_error() {
		TokenDefinitionParticle token = mock(TokenDefinitionParticle.class);
		when(token.getIconUrl()).thenReturn("this is not a url");
		testAtomModelOS
			.testInitialParticle(token, mock(AtomMetadata.class))
			.assertErrorWithMessageContaining("Icon: not a valid URL");
	}

	@Test
	public void when_validating_token_class_particle_with_valid_icon__result_has_no_error() {
		TokenDefinitionParticle token = mock(TokenDefinitionParticle.class);
		when(token.getIconUrl()).thenReturn("https://assets.radixdlt.com/test.png");
		testAtomModelOS
			.testInitialParticle(token, mock(AtomMetadata.class))
			.assertNoErrorWithMessageContaining("Icon:");
	}

	@Test
	public void when_validating_token_class_particle_without_owner_signature__result_has_no_error() {
		TokenDefinitionParticle token = mock(TokenDefinitionParticle.class);
		RadixAddress owner = mock(RadixAddress.class);
		when(token.getOwner()).thenReturn(owner);
		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(owner)).thenReturn(false);
		testAtomModelOS
			.testInitialParticle(token, metadata)
			.assertErrorWithMessageContaining("sign");
	}

	@Test
	public void when_validating_token_class_particle_with_owner_signature__result_has_no_error() {
		TokenDefinitionParticle token = mock(TokenDefinitionParticle.class);
		RadixAddress owner = mock(RadixAddress.class);
		when(token.getOwner()).thenReturn(owner);
		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(owner)).thenReturn(true);
		testAtomModelOS
			.testInitialParticle(token, metadata)
			.assertNoErrorWithMessageContaining("sign");
	}
}