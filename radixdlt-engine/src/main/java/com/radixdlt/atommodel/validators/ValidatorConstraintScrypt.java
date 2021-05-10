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
import com.radixdlt.atom.actions.Unknown;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.InputOutputReducer;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.constraintmachine.SignatureValidator;
import com.radixdlt.store.ReadableAddrs;

import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Constraint Scrypt defining the Validator FSM.
 */
public class ValidatorConstraintScrypt implements ConstraintScrypt {
	@Override
	public void main(SysCalls os) {
		os.registerParticle(ValidatorParticle.class, ParticleDefinition.<ValidatorParticle>builder()
			.staticValidation(checkAddressAndUrl(ValidatorParticle::getUrl))
			.virtualizeUp(p -> !p.isRegisteredForNextEpoch() && p.getUrl().isEmpty() && p.getName().isEmpty())
			.allowTransitionsFromOutsideScrypts() // to enable staking in TokensConstraintScrypt
			.build()
		);

		os.createTransition(
			new TransitionToken<>(ValidatorParticle.class, ValidatorParticle.class, TypeToken.of(VoidReducerState.class)),
			new ValidatorTransitionProcedure()
		);
	}

	// From the OWASP validation repository: https://www.owasp.org/index.php/OWASP_Validation_Regex_Repository
	private static final Pattern OWASP_URL_REGEX = Pattern.compile(
		"^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))"
		+ "(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,A-Za-z0-9])+)([).!';/?:,][[:blank:]])?$"
	);

	private static <I> Function<I, Result> checkAddressAndUrl(Function<I, String> urlMapper) {
		return particle -> {
			String url = urlMapper.apply(particle);
			if (!url.isEmpty() && !OWASP_URL_REGEX.matcher(url).matches()) {
				return Result.error("url is not a valid URL: " + url);
			}

			return Result.success();
		};
	}

	@VisibleForTesting
	static class ValidatorTransitionProcedure
		implements TransitionProcedure<ValidatorParticle, ValidatorParticle, VoidReducerState> {

		@Override
		public Result precondition(
			SubstateWithArg<ValidatorParticle> in,
			ValidatorParticle out,
			VoidReducerState outputUsed,
			ReadableAddrs index
		) {
			// ensure transition is between validator particles concerning the same validator address
			if (!Objects.equals(in.getSubstate().getKey(), out.getKey())) {
				return Result.error(String.format(
					"validator addresses do not match: %s != %s",
					in.getSubstate().getKey(), out.getKey()
				));
			}

			return Result.success();
		}

		@Override
		public InputOutputReducer<ValidatorParticle, ValidatorParticle, VoidReducerState> inputOutputReducer() {
			return (input, output, index, outputUsed) -> ReducerResult.complete(Unknown.create());
		}

		@Override
		public SignatureValidator<ValidatorParticle, ValidatorParticle> signatureValidator() {
			// verify that the transition was authenticated by the validator address in question
			return (i, o, index, pubKey) -> pubKey.map(i.getSubstate().getKey()::equals).orElse(false);
		}
	}
}
