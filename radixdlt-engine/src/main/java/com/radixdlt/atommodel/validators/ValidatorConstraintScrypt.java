/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.atommodel.validators;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.reflect.TypeToken;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.InputOutputReducer;
import com.radixdlt.constraintmachine.VoidUsedData;
import com.radixdlt.constraintmachine.SignatureValidator;
import com.radixdlt.identifiers.RadixAddress;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Constraint Scrypt defining the Validator FSM, specifically the registration/unregistration flow.
 * <p>
 * The Validator FSM is implemented with two particles, UnregisteredValidatorParticle and RegisteredValidatorParticle,
 * both carrying the address of the validator in question and a nonce. The first unregistered Validator particle
 * for an address (with nonce 0) is virtualised as having an UP spin to initialise the FSM. Whenever a validator
 * (identified by their address) transitions between the two states, the nonce must increase (to ensure uniqueness).
 * The atom carrying a transition must be signed by the validator.
 */
public class ValidatorConstraintScrypt implements ConstraintScrypt {
	@Override
	public void main(SysCalls os) {
		os.registerParticle(ValidatorParticle.class, ParticleDefinition.<ValidatorParticle>builder()
			.staticValidation(checkAddressAndUrl(ValidatorParticle::getAddress,
				ValidatorParticle::getUrl))
			.virtualizeUp(p -> !p.isRegisteredForNextEpoch() && p.getUrl() == null)
			.allowTransitionsFromOutsideScrypts() // to enable staking in TokensConstraintScrypt
			.build()
		);

		createTransition(os,
			ValidatorParticle.class,
			ValidatorParticle::getAddress,
			ValidatorParticle.class,
			ValidatorParticle::getAddress
		);
	}

	private <I extends Particle, O extends Particle> void createTransition(
		SysCalls os,
		Class<I> inputParticle,
		Function<I, RadixAddress> inputAddressMapper,
		Class<O> outputParticle,
		Function<O, RadixAddress> outputAddressMapper
	) {
		os.createTransition(
			new TransitionToken<>(inputParticle, TypeToken.of(VoidUsedData.class), outputParticle, TypeToken.of(VoidUsedData.class)),
			new ValidatorTransitionProcedure<>(inputAddressMapper, outputAddressMapper)
		);
	}

	// From the OWASP validation repository: https://www.owasp.org/index.php/OWASP_Validation_Regex_Repository
	private static final Pattern OWASP_URL_REGEX = Pattern.compile(
		"^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))"
		+ "(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,A-Za-z0-9])+)([).!';/?:,][[:blank:]])?$"
	);

	private static <I> Function<I, Result> checkAddressAndUrl(
		Function<I, RadixAddress> addressMapper,
		Function<I, String> urlMapper
	) {
		return particle -> {
			if (addressMapper.apply(particle) == null) {
				return Result.error("address is null");
			}
			String url = urlMapper.apply(particle);
			if (url != null && !OWASP_URL_REGEX.matcher(url).matches()) {
				return Result.error("url is not a valid URL: " + url);
			}

			return Result.success();
		};
	}

	// create a check verifying that the given address doesn't map to null
	private static <I> Function<I, Result> checkAddress(Function<I, RadixAddress> addressMapper) {
		return particle -> {
			if (addressMapper.apply(particle) == null) {
				return Result.error("address is null");
			}

			return Result.success();
		};
	}

	@VisibleForTesting
	static class ValidatorTransitionProcedure<I extends Particle, O extends Particle>
		implements TransitionProcedure<I, VoidUsedData, O, VoidUsedData> {
		private final Function<I, RadixAddress> inputAddressMapper;
		private final Function<O, RadixAddress> outputAddressMapper;

		ValidatorTransitionProcedure(
			Function<I, RadixAddress> inputAddressMapper,
			Function<O, RadixAddress> outputAddressMapper
		) {
			this.inputAddressMapper = inputAddressMapper;
			this.outputAddressMapper = outputAddressMapper;
		}

		@Override
		public Result precondition(I inputParticle, VoidUsedData inputUsed, O outputParticle, VoidUsedData outputUsed) {
			RadixAddress inputAddress = inputAddressMapper.apply(inputParticle);
			RadixAddress outputAddress = outputAddressMapper.apply(outputParticle);
			// ensure transition is between validator particles concerning the same validator address
			if (!Objects.equals(inputAddress, outputAddress)) {
				return Result.error(String.format(
					"validator addresses do not match: %s != %s",
					inputAddress, outputAddress
				));
			}

			return Result.success();
		}

		@Override
		public InputOutputReducer<I, VoidUsedData, O, VoidUsedData> inputOutputReducer() {
			return (input, inputUsed, output, outputUsed) -> Optional.empty();
		}

		@Override
		public SignatureValidator<I> inputSignatureRequired() {
			// verify that the transition was authenticated by the validator address in question
			return i -> Optional.of(inputAddressMapper.apply(i).getPublicKey());
		}
	}
}
