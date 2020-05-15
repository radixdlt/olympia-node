package com.radixdlt.atommodel.validators;

import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.Particle;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class ValidatorConstraintScryptTest {
	private static Function<Particle, Result> staticCheck;

	@BeforeClass
	public static void initializeConstraintScrypt() {
		ValidatorConstraintScrypt tokensConstraintScrypt = new ValidatorConstraintScrypt();
		CMAtomOS cmAtomOS = new CMAtomOS();
		cmAtomOS.load(tokensConstraintScrypt);
		staticCheck = cmAtomOS.buildParticleStaticCheck();
	}

	@Test
	public void when_validating_unregistered_validator_particle_with_no_address__result_has_error() {
		UnregisteredValidatorParticle validator = mock(UnregisteredValidatorParticle.class);
		assertThat(staticCheck.apply(validator).getErrorMessage()).contains("address");
	}

	@Test
	public void when_validating_registered_validator_particle_with_no_address__result_has_error() {
		RegisteredValidatorParticle validator = mock(RegisteredValidatorParticle.class);
		assertThat(staticCheck.apply(validator).getErrorMessage()).contains("address");
	}


}
