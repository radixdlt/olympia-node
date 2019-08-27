package integration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.RRI;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.utils.UInt256;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.BeforeClass;
import org.junit.Test;
import org.powermock.api.mockito.PowerMockito;

public class TokensConstraintScryptTest {
	private static ConstraintMachine constraintMachine;

	@BeforeClass
	public static void initializeConstraintScrypt() {
		TokensConstraintScrypt tokensConstraintScrypt = new TokensConstraintScrypt();
		CMAtomOS cmAtomOS = new CMAtomOS();
		cmAtomOS.load(tokensConstraintScrypt);
		constraintMachine = cmAtomOS.buildMachine();
	}

	private void validateUnknownParticleError(Particle particle, String errorMessage) {
		CMInstruction instruction = new CMInstruction(ImmutableList.of(CMMicroInstruction.checkSpin(particle, Spin.UP)));
		Optional<CMError> error = constraintMachine.validate(instruction);
		assertThat(error)
			.get()
			.satisfies(e -> {
				assertThat(e.getErrMsg()).contains(errorMessage);
				assertThat(e.getErrorCode()).isEqualTo(CMErrorCode.INVALID_PARTICLE);
				assertThat(e.getDataPointer()).isEqualTo(DataPointer.ofParticle(0, 0));
			});
	}

	@Test
	public void when_validating_mutable_token_def_particle_with_no_rri__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = mock(MutableSupplyTokenDefinitionParticle.class);
		validateUnknownParticleError(token, "rri cannot be null");
	}

	@Test
	public void when_validating_fixed_token_def_particle_with_no_rri__result_has_error() {
		FixedSupplyTokenDefinitionParticle token = mock(FixedSupplyTokenDefinitionParticle.class);
		validateUnknownParticleError(token, "rri cannot be null");
	}

	@Test
	public void when_validating_token_class_particle_with_too_long_symbol__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TEEEEEEEEEEEEEEEEEEEEEEEEEEST"));
		validateUnknownParticleError(token, "Symbol: invalid length");
	}

	@Test
	public void when_validating_token_class_particle_with_too_short_symbol__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), ""));
		validateUnknownParticleError(token, "Symbol: invalid length");
	}

	@Test
	public void when_validating_token_class_particle_without_description__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		validateUnknownParticleError(token, "Description: no or empty provided");
	}

	@Test
	public void when_validating_token_class_particle_with_too_long_description__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TEST"));
		when(token.getDescription()).thenReturn(
			IntStream.range(0, TokenDefinitionUtils.MAX_DESCRIPTION_LENGTH + 1).mapToObj(i -> "c").collect(Collectors.joining()));

		validateUnknownParticleError(token, "Description: invalid length");
	}

	@Test
	public void when_validating_token_class_particle_without_permissions_set__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(token.getTokenPermissions()).thenReturn(new HashMap<>());
		when(token.getDescription()).thenReturn("Hello");

		validateUnknownParticleError(token, "Permissions: must be set");
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
		validateUnknownParticleError(token, "Icon: not a valid URL");
	}

	@Test
	public void when_validating_token_instance_with_null_amount__result_has_error() {
		TransferrableTokensParticle transferrableTokensParticle = mock(TransferrableTokensParticle.class);
		when(transferrableTokensParticle.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(transferrableTokensParticle.getAmount()).thenReturn(null);
		validateUnknownParticleError(transferrableTokensParticle, "null");
	}

	@Test
	public void when_validating_unallocated_token_with_null_amount__result_has_error() {
		UnallocatedTokensParticle unallocated = mock(UnallocatedTokensParticle.class);
		when(unallocated.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(unallocated.getAmount()).thenReturn(null);
		validateUnknownParticleError(unallocated, "null");
	}

	@Test
	public void when_validating_token_instance_with_zero_amount__result_has_error() {
		TransferrableTokensParticle transferrableTokensParticle = mock(TransferrableTokensParticle.class);
		when(transferrableTokensParticle.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(transferrableTokensParticle.getAmount()).thenReturn(UInt256.ZERO);
		validateUnknownParticleError(transferrableTokensParticle, "zero");
	}

	@Test
	public void when_validating_unallocated_token_with_zero_amount__result_has_error() {
		UnallocatedTokensParticle burnedTokensParticle = mock(UnallocatedTokensParticle.class);
		when(burnedTokensParticle.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(burnedTokensParticle.getAmount()).thenReturn(UInt256.ZERO);
		validateUnknownParticleError(burnedTokensParticle, "zero");
	}

	@Test
	public void when_validating_token_instance_with_null_granularity__result_has_error() {
		TransferrableTokensParticle transferrableTokensParticle = mock(TransferrableTokensParticle.class);
		when(transferrableTokensParticle.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(transferrableTokensParticle.getAmount()).thenReturn(UInt256.ONE);
		when(transferrableTokensParticle.getGranularity()).thenReturn(null);
		validateUnknownParticleError(transferrableTokensParticle, "granularity");
	}

	@Test
	public void when_validating_unallocated_token_with_null_granularity__result_has_error() {
		UnallocatedTokensParticle unallocated = mock(UnallocatedTokensParticle.class);
		when(unallocated.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(unallocated.getAmount()).thenReturn(UInt256.ONE);
		when(unallocated.getGranularity()).thenReturn(null);
		validateUnknownParticleError(unallocated, "granularity");
	}

	@Test
	public void when_validating_unallocated_token_with_zero_granularity__result_has_error() {
		UnallocatedTokensParticle unallocated = mock(UnallocatedTokensParticle.class);
		when(unallocated.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(unallocated.getAmount()).thenReturn(UInt256.ONE);
		when(unallocated.getGranularity()).thenReturn(UInt256.ZERO);
		validateUnknownParticleError(unallocated, "granularity");
	}

	@Test
	public void when_validating_token_instance_with_amount_not_divisible_by_granularity__result_has_error() {
		TransferrableTokensParticle transferrableTokensParticle = mock(TransferrableTokensParticle.class);
		when(transferrableTokensParticle.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(transferrableTokensParticle.getAmount()).thenReturn(UInt256.THREE);
		when(transferrableTokensParticle.getGranularity()).thenReturn(UInt256.TWO);
		validateUnknownParticleError(transferrableTokensParticle, "granularity");
	}
}