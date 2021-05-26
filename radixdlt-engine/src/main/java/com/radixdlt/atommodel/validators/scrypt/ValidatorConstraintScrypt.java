/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.atommodel.validators.scrypt;

import com.google.common.reflect.TypeToken;
import com.radixdlt.atom.actions.Unknown;
import com.radixdlt.atommodel.validators.state.ValidatorParticle;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.constraintmachine.AuthorizationException;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;

import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Constraint Scrypt defining the Validator FSM.
 */
public class ValidatorConstraintScrypt implements ConstraintScrypt {
	private static class ValidatorUpdate implements ReducerState {
		private final ValidatorParticle prevState;

		private ValidatorUpdate(ValidatorParticle prevState) {
			this.prevState = prevState;
		}
	}

	@Override
	public void main(SysCalls os) {
		os.registerParticle(ValidatorParticle.class, ParticleDefinition.<ValidatorParticle>builder()
			.staticValidation(checkAddressAndUrl(ValidatorParticle::getUrl))
			.virtualizeUp(p -> !p.isRegisteredForNextEpoch() && p.getUrl().isEmpty() && p.getName().isEmpty())
			.build()
		);

		os.createDownProcedure(new DownProcedure<>(
			ValidatorParticle.class, VoidReducerState.class,
			(d, r) -> PermissionLevel.USER,
			(d, r, k) -> {
				if (!k.map(d.getSubstate().getKey()::equals).orElse(false)) {
					throw new AuthorizationException("Key does not match.");
				}
			},
			(d, s, r) -> {
				if (d.getArg().isPresent()) {
					throw new ProcedureException("Args not allowed");
				}
				return ReducerResult.incomplete(new ValidatorUpdate(d.getSubstate()));
			}
		));

		os.createUpProcedure(new UpProcedure<>(
			ValidatorUpdate.class, ValidatorParticle.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> { },
			(s, u, r) -> {
				if (!Objects.equals(s.prevState.getKey(), u.getKey())) {
					throw new ProcedureException(String.format(
						"validator addresses do not match: %s != %s",
						s.prevState.getKey(), u.getKey()
					));
				}
				return ReducerResult.complete(Unknown.create());
			}
		));
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
}
