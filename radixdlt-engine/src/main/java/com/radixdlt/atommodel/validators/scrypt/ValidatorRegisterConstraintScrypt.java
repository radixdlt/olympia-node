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

import com.google.common.hash.HashCode;
import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atommodel.validators.state.PreparedRegisteredUpdate;
import com.radixdlt.atommodel.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.AuthorizationException;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.crypto.ECPublicKey;

import java.util.Optional;

public class ValidatorRegisterConstraintScrypt implements ConstraintScrypt {
	private static class UpdatingRegistered implements ReducerState {
		private final ECPublicKey validatorKey;

		UpdatingRegistered(ECPublicKey validatorKey) {
			this.validatorKey = validatorKey;
		}

		void update(PreparedRegisteredUpdate update) throws ProcedureException {
			if (!update.getValidatorKey().equals(validatorKey)) {
				throw new ProcedureException("Cannot update validator");
			}
		}
	}

	@Override
	public void main(Loader os) {
		os.substate(new SubstateDefinition<>(
			ValidatorRegisteredCopy.class,
			SubstateTypeId.VALIDATOR_REGISTERED_FLAG_COPY.id(),
			buf -> {
				var key = REFieldSerialization.deserializeKey(buf);
				var flag = REFieldSerialization.deserializeBoolean(buf);
				return new ValidatorRegisteredCopy(key, flag);
			},
			(s, buf) -> {
				REFieldSerialization.serializeKey(buf, s.getValidatorKey());
				buf.put((byte) (s.isRegistered() ? 1 : 0));
			},
			s -> !s.isRegistered()
		));

		os.substate(new SubstateDefinition<>(
			PreparedRegisteredUpdate.class,
			SubstateTypeId.PREPARED_REGISTERED_FLAG_UPDATE.id(),
			buf -> {
				var key = REFieldSerialization.deserializeKey(buf);
				var flag = REFieldSerialization.deserializeBoolean(buf);
				final var forkVoteHashBytes = REFieldSerialization.deserializeBytes(buf);
				final var forkVoteHash = forkVoteHashBytes.length > 0
					? Optional.of(HashCode.fromBytes(forkVoteHashBytes))
					: Optional.<HashCode>empty();
				return new PreparedRegisteredUpdate(key, flag, forkVoteHash);
			},
			(s, buf) -> {
				REFieldSerialization.serializeKey(buf, s.getValidatorKey());
				buf.put((byte) (s.isRegistered() ? 1 : 0));
				s.getForkVoteHash().ifPresentOrElse(
					forkVoteHash -> REFieldSerialization.serializeBytes(buf, forkVoteHash.asBytes()),
					() -> REFieldSerialization.serializeBytes(buf, new byte[] {})
				);
			},
			s -> !s.isRegistered()
		));


		os.procedure(new DownProcedure<>(
			VoidReducerState.class, ValidatorRegisteredCopy.class,
			d -> new Authorization(
				PermissionLevel.USER,
				(r, c) -> {
					if (!c.key().map(d.getSubstate().getValidatorKey()::equals).orElse(false)) {
						throw new AuthorizationException("Key does not match.");
					}
				}
			),
			(d, s, r) -> {
				if (d.getArg().isPresent()) {
					throw new ProcedureException("Args not allowed");
				}
				return ReducerResult.incomplete(new UpdatingRegistered(d.getSubstate().getValidatorKey()));
			}
		));

		os.procedure(new DownProcedure<>(
			VoidReducerState.class, PreparedRegisteredUpdate.class,
			d -> new Authorization(
				PermissionLevel.USER,
				(r, c) -> {
					if (!c.key().map(d.getSubstate().getValidatorKey()::equals).orElse(false)) {
						throw new AuthorizationException("Key does not match.");
					}
				}
			),
			(d, s, r) -> {
				if (d.getArg().isPresent()) {
					throw new ProcedureException("Args not allowed");
				}
				return ReducerResult.incomplete(new UpdatingRegistered(d.getSubstate().getValidatorKey()));
			}
		));

		os.procedure(new UpProcedure<>(
			UpdatingRegistered.class, PreparedRegisteredUpdate.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				s.update(u);
				return ReducerResult.complete();
			}
		));
	}
}
