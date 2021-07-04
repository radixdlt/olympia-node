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

package com.radixdlt.application.validators.scrypt;

import com.radixdlt.application.validators.state.PreparedOwnerUpdate;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DeserializeException;

public class ValidatorUpdateOwnerConstraintScrypt implements ConstraintScrypt {

	private static class UpdatingValidatorOwner implements ReducerState {
		private final ECPublicKey validatorKey;

		UpdatingValidatorOwner(ECPublicKey validatorKey) {
			this.validatorKey = validatorKey;
		}

		void update(PreparedOwnerUpdate update) throws ProcedureException {
			if (!update.getValidatorKey().equals(validatorKey)) {
				throw new ProcedureException("Invalid key update");
			}
		}
	}

	@Override
	public void main(Loader os) {
		os.substate(new SubstateDefinition<>(
			ValidatorOwnerCopy.class,
			SubstateTypeId.VALIDATOR_OWNER_COPY.id(),
			buf -> {
				REFieldSerialization.deserializeReservedByte(buf);
				var key = REFieldSerialization.deserializeKey(buf);
				var owner = REFieldSerialization.deserializeAccountREAddr(buf);
				if (!owner.isAccount()) {
					throw new DeserializeException("Address is not an account: " + owner);
				}
				return new ValidatorOwnerCopy(key, owner);
			},
			(s, buf) -> {
				REFieldSerialization.serializeReservedByte(buf);
				REFieldSerialization.serializeKey(buf, s.getValidatorKey());
				REFieldSerialization.serializeREAddr(buf, s.getOwner());
			},
			s -> REAddr.ofPubKeyAccount(s.getValidatorKey()).equals(s.getOwner())
		));

		os.substate(new SubstateDefinition<>(
			PreparedOwnerUpdate.class,
			SubstateTypeId.PREPARED_VALIDATOR_OWNER_UPDATE.id(),
			buf -> {
				REFieldSerialization.deserializeReservedByte(buf);
				var key = REFieldSerialization.deserializeKey(buf);
				var ownerAddr = REFieldSerialization.deserializeAccountREAddr(buf);
				if (!ownerAddr.isAccount()) {
					throw new DeserializeException("Owner address must be an account");
				}
				return new PreparedOwnerUpdate(key, ownerAddr);
			},
			(s, buf) -> {
				REFieldSerialization.serializeReservedByte(buf);
				REFieldSerialization.serializeKey(buf, s.getValidatorKey());
				REFieldSerialization.serializeREAddr(buf, s.getOwnerAddress());
			}
		));
		os.procedure(new DownProcedure<>(
			VoidReducerState.class, PreparedOwnerUpdate.class,
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
				return ReducerResult.incomplete(new UpdatingValidatorOwner(d.getSubstate().getValidatorKey()));
			}
		));
		os.procedure(new DownProcedure<>(
			VoidReducerState.class, ValidatorOwnerCopy.class,
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
				return ReducerResult.incomplete(new UpdatingValidatorOwner(d.getSubstate().getValidatorKey()));
			}
		));

		os.procedure(new UpProcedure<>(
			UpdatingValidatorOwner.class, PreparedOwnerUpdate.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				s.update(u);
				return ReducerResult.complete();
			}
		));
	}
}
