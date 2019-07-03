package com.radixdlt.atommodel.tokens;

import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.atomos.test.TestAtomOS;
import com.radixdlt.atomos.test.TestResult;
import com.radixdlt.atomos.RRI;
import com.radixdlt.utils.UInt256;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

public class TokenInstancesConstraintScryptTest {
	private static TestAtomOS testAtomOS = new TestAtomOS();

	@BeforeClass
	public static void initializeConstraintScrypt() {
		TokenInstancesConstraintScrypt tokenInstancesConstraintScrypt = new TokenInstancesConstraintScrypt();
		tokenInstancesConstraintScrypt.main(testAtomOS);
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

	private static class MockTransition {
		private final UnallocatedTokensParticle unallocated;
		private final TransferrableTokensParticle transferred;

		MockTransition(UnallocatedTokensParticle unallocated, TransferrableTokensParticle transferred) {
			this.unallocated = unallocated;
			this.transferred = transferred;
		}
	}

	private MockTransition mockTransition() {
		Map<TokenTransition, TokenPermission> permissions = mock(Map.class);
		RRI rri = mock(RRI.class);
		RadixAddress address = mock(RadixAddress.class);
		when(rri.getAddress()).thenReturn(address);

		UnallocatedTokensParticle unallocated = mock(UnallocatedTokensParticle.class);
		when(unallocated.getTokenPermission(TokenTransition.MINT)).thenReturn(TokenPermission.ALL);
		when(unallocated.getTokenPermission(TokenTransition.BURN)).thenReturn(TokenPermission.ALL);
		when(unallocated.getTokenPermissions()).thenReturn(permissions);
		when(unallocated.getAmount()).thenReturn(UInt256.ONE);
		when(unallocated.getTokDefRef()).thenReturn(rri);
		when(unallocated.getGranularity()).thenReturn(UInt256.ONE);

		TransferrableTokensParticle transferred = mock(TransferrableTokensParticle.class);
		when(transferred.getTokenPermissions()).thenReturn(permissions);
		when(transferred.getAmount()).thenReturn(UInt256.ONE);
		when(transferred.getTokDefRef()).thenReturn(rri);
		when(transferred.getGranularity()).thenReturn(UInt256.ONE);
		when(transferred.getAddress()).thenReturn(address);

		return new MockTransition(unallocated, transferred);
	}

	@Test
	public void when_validating_minted_with_satisfied_all_permission__result_has_no_error() {
		MockTransition mock = mockTransition();
		AtomMetadata metadata = mock(AtomMetadata.class);

		testAtomOS.testFungible(mock.unallocated, mock.transferred, metadata)
			.assertSuccess();
	}

	@Test
	public void when_validating_minted_with_satisfied_none_permission__result_has_error() {
		MockTransition mock = mockTransition();
		when(mock.unallocated.getTokenPermission(TokenTransition.MINT)).thenReturn(TokenPermission.NONE);
		AtomMetadata metadata = mock(AtomMetadata.class);

		testAtomOS.testFungible(mock.unallocated, mock.transferred, metadata)
			.assertErrorWithMessageContaining("no-one");
	}

	@Test
	public void when_validating_minted_with_satisfied_token_creation_permission__result_has_no_error() {
		MockTransition mock = mockTransition();
		when(mock.unallocated.getTokenPermission(TokenTransition.MINT)).thenReturn(TokenPermission.TOKEN_CREATION_ONLY);
		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.contains(any())).thenReturn(true);

		testAtomOS.testFungible(mock.unallocated, mock.transferred, metadata)
			.assertSuccess();
	}

	@Test
	public void when_validating_minted_with_unsatisfied_token_creation_permission__result_has_error() {
		MockTransition mock = mockTransition();
		when(mock.unallocated.getTokenPermission(TokenTransition.MINT)).thenReturn(TokenPermission.TOKEN_CREATION_ONLY);
		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.contains(any())).thenReturn(false);

		testAtomOS.testFungible(mock.unallocated, mock.transferred, metadata)
			.assertErrorWithMessageContaining("token creation");
	}

	@Test
	public void when_validating_minted_with_satisfied_token_owner_permission__result_has_no_error() {
		MockTransition mock = mockTransition();
		when(mock.unallocated.getTokenPermission(TokenTransition.MINT)).thenReturn(TokenPermission.TOKEN_OWNER_ONLY);
		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(mock.transferred.getAddress())).thenReturn(true);

		testAtomOS.testFungible(mock.unallocated, mock.transferred, metadata)
			.assertSuccess();
	}

	@Test
	public void when_validating_minted_with_unsatisfied_token_owner_permission__result_has_error() {
		MockTransition mock = mockTransition();
		when(mock.unallocated.getTokenPermission(TokenTransition.MINT)).thenReturn(TokenPermission.TOKEN_OWNER_ONLY);
		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(mock.transferred.getAddress())).thenReturn(false);

		testAtomOS.testFungible(mock.unallocated, mock.transferred, metadata)
			.assertErrorWithMessageContaining("token owner");
	}

	@Test
	public void when_validating_burned_with_satisfied_all_permission__result_has_no_error() {
		MockTransition mock = mockTransition();
		when(mock.unallocated.getTokenPermission(TokenTransition.BURN)).thenReturn(TokenPermission.ALL);
		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(any())).thenReturn(true);

		testAtomOS.testFungible(mock.transferred, mock.unallocated, metadata)
			.assertSuccess();
	}

	@Test
	public void when_validating_burned_with_satisfied_none_permission__result_has_error() {
		MockTransition mock = mockTransition();
		when(mock.unallocated.getTokenPermission(TokenTransition.BURN)).thenReturn(TokenPermission.NONE);
		AtomMetadata metadata = mock(AtomMetadata.class);

		testAtomOS.testFungible(mock.transferred, mock.unallocated, metadata)
			.assertErrorWithMessageContaining("no-one");
	}

	@Test
	public void when_validating_burned_with_satisfied_token_creation_permission__result_has_no_error() {
		MockTransition mock = mockTransition();
		when(mock.unallocated.getTokenPermission(TokenTransition.BURN)).thenReturn(TokenPermission.TOKEN_CREATION_ONLY);
		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.contains(any())).thenReturn(true);
		when(metadata.isSignedBy(any())).thenReturn(true);

		testAtomOS.testFungible(mock.transferred, mock.unallocated, metadata)
			.assertSuccess();
	}

	@Test
	public void when_validating_burned_with_unsatisfied_token_creation_permission__result_has_error() {
		MockTransition mock = mockTransition();
		when(mock.unallocated.getTokenPermission(TokenTransition.BURN)).thenReturn(TokenPermission.TOKEN_CREATION_ONLY);
		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.contains(any())).thenReturn(false);

		testAtomOS.testFungible(mock.transferred, mock.unallocated, metadata)
			.assertErrorWithMessageContaining("token creation");
	}

	@Test
	public void when_validating_burned_with_satisfied_token_owner_permission__result_has_no_error() {
		MockTransition mock = mockTransition();
		when(mock.unallocated.getTokenPermission(TokenTransition.BURN)).thenReturn(TokenPermission.TOKEN_OWNER_ONLY);
		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(mock.transferred.getAddress())).thenReturn(true);

		testAtomOS.testFungible(mock.transferred, mock.unallocated, metadata)
			.assertSuccess();
	}

	@Test
	public void when_validating_burned_with_unsatisfied_token_owner_permission__result_has_error() {
		MockTransition mock = mockTransition();
		when(mock.unallocated.getTokenPermission(TokenTransition.BURN)).thenReturn(TokenPermission.TOKEN_OWNER_ONLY);
		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(mock.transferred.getAddress())).thenReturn(false);

		testAtomOS.testFungible(mock.transferred, mock.unallocated, metadata)
			.assertErrorWithMessageContaining("token owner");
	}

	@Test
	public void when_validating_transferred_where_amount_fits_granularity__result_has_no_error() {
		TransferrableTokensParticle transferrableTokensParticle = mock(TransferrableTokensParticle.class);
		when(transferrableTokensParticle.getAmount()).thenReturn(UInt256.TEN);
		when(transferrableTokensParticle.getGranularity()).thenReturn(UInt256.FIVE);

		testAtomOS.testInitialParticle(transferrableTokensParticle, mock(AtomMetadata.class))
			.assertNoErrorWithMessageContaining("amount");
	}

	@Test
	public void when_validating_transferred_where_amount_does_not_fit_granularity__result_has_error() {
		TransferrableTokensParticle transferrableTokensParticle = mock(TransferrableTokensParticle.class);
		when(transferrableTokensParticle.getAmount()).thenReturn(UInt256.TEN);
		when(transferrableTokensParticle.getGranularity()).thenReturn(UInt256.THREE);

		testAtomOS.testInitialParticle(transferrableTokensParticle, mock(AtomMetadata.class))
			.assertErrorWithMessageContaining("amount");
	}

	@Test
	public void when_validating_signed_transfer_to_transfer_with_equal_amounts_equal_granularities_equal_type_result_has_no_error() {
		RRI type = mock(RRI.class);
		UInt256 granularity = UInt256.ONE;
		TransferrableTokensParticle output = mock(TransferrableTokensParticle.class);
		when(output.getAmount()).thenReturn(UInt256.TWO);
		when(output.getGranularity()).thenReturn(granularity);
		when(output.getTokDefRef()).thenReturn(type);
		TransferrableTokensParticle input = mock(TransferrableTokensParticle.class);
		when(input.getAmount()).thenReturn(UInt256.TWO);
		when(input.getGranularity()).thenReturn(granularity);
		when(input.getTokDefRef()).thenReturn(type);

		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(any())).thenReturn(true);

		testAtomOS.testFungible(input, output, metadata)
			.assertSuccess();
	}

	@Test
	public void when_validating_unsigned_transfer_to_unallocated_with_equal_amounts_equal_granularities_equal_type_result_has_error() {
		RRI type = mock(RRI.class);
		RadixAddress address = mock(RadixAddress.class);
		when(type.getAddress()).thenReturn(address);
		Map<TokenTransition, TokenPermission> permissions = mock(Map.class);

		UInt256 granularity = UInt256.ONE;
		UnallocatedTokensParticle output = mock(UnallocatedTokensParticle.class);
		when(output.getAmount()).thenReturn(UInt256.TWO);
		when(output.getGranularity()).thenReturn(granularity);
		when(output.getTokDefRef()).thenReturn(type);
		when(output.getTokenPermissions()).thenReturn(permissions);
		when(output.getTokenPermission(any())).thenReturn(TokenPermission.TOKEN_OWNER_ONLY);

		TransferrableTokensParticle input = mock(TransferrableTokensParticle.class);
		when(input.getAmount()).thenReturn(UInt256.TWO);
		when(input.getGranularity()).thenReturn(granularity);
		when(input.getTokDefRef()).thenReturn(type);
		when(input.getAddress()).thenReturn(address);
		when(input.getTokenPermissions()).thenReturn(permissions);

		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(any())).thenReturn(false);

		testAtomOS.testFungible(input, output, metadata)
			.assertErrorWithMessageContaining("signed");
	}

	@Test
	public void when_validating_signed_mint_to_transfer_with_equal_amounts_equal_granularities_different_type_result_has_error() {
		MockTransition mock = mockTransition();
		RRI type = mock(RRI.class);
		when(mock.unallocated.getTokDefRef()).thenReturn(type);

		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(any())).thenReturn(true);

		testAtomOS.testFungible(mock.unallocated, mock.transferred, metadata)
			.assertErrorWithMessageContaining("token types");
	}

	@Test
	public void when_validating_signed_burn_with_equal_amounts_equal_granularities_different_type_result_has_error() {
		MockTransition mock = mockTransition();
		RRI type = mock(RRI.class);
		when(mock.unallocated.getTokDefRef()).thenReturn(type);

		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(any())).thenReturn(true);

		testAtomOS.testFungible(mock.transferred, mock.unallocated, metadata)
			.assertErrorWithMessageContaining("token types");
	}

	@Test
	public void when_validating_signed_minted_to_transfer_with_equal_amounts_different_granularities_equal_type_result_has_error() {
		MockTransition mock = mockTransition();
		when(mock.unallocated.getGranularity()).thenReturn(UInt256.TWO);

		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(any())).thenReturn(true);

		testAtomOS.testFungible(mock.unallocated, mock.transferred, metadata)
			.assertErrorWithMessageContaining("granularities");
	}

	@Test
	public void when_validating_signed_transfer_to_burn_with_equal_amounts_different_granularities_equal_type_result_has_error() {
		MockTransition mock = mockTransition();
		when(mock.unallocated.getGranularity()).thenReturn(UInt256.TWO);

		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(any())).thenReturn(true);

		testAtomOS.testFungible(mock.transferred, mock.unallocated, metadata)
			.assertErrorWithMessageContaining("granularities");
	}

	@Test
	public void when_validating_unsigned_transfer_to_burn_with_equal_amounts_different_granularities_different_type_result_has_error() {
		MockTransition mock = mockTransition();
		when(mock.unallocated.getGranularity()).thenReturn(UInt256.TWO);
		when(mock.unallocated.getTokDefRef()).thenReturn(mock(RRI.class));

		AtomMetadata metadata = mock(AtomMetadata.class);
		when(metadata.isSignedBy(any())).thenReturn(false);

		TestResult result = testAtomOS.testFungible(mock.transferred, mock.unallocated, metadata);
		result.assertErrorWithMessageContaining("granularities");
		result.assertErrorWithMessageContaining("signed");
		result.assertErrorWithMessageContaining("token types");
	}
}