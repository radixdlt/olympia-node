package com.radixdlt.atommodel.validators;

import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.SysCalls;

public class ValidatorConstraintScrypt implements ConstraintScrypt {
	@Override
	public void main(SysCalls os) {
		os.registerParticle(
			UnregisteredValidatorParticle.class,
			UnregisteredValidatorParticle::getAddress,
			ValidatorConstraintScrypt::staticCheck
		);
	}

	private static Result staticCheck(RegisteredValidatorParticle registeredValidatorParticle) {
		return Result.success();
	}

	private static Result staticCheck(UnregisteredValidatorParticle unregisteredValidatorParticle) {
		return Result.success();
	}
}
