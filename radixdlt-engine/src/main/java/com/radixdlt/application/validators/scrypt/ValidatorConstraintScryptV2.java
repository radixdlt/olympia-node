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

import com.radixdlt.application.validators.state.ValidatorSystemMetadata;
import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.application.validators.state.ValidatorMetaData;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;

import java.util.Objects;


public class ValidatorConstraintScryptV2 implements ConstraintScrypt {

	private static class UpdatingValidatorHashMetadata implements ReducerState {
		private final ValidatorSystemMetadata prevState;

		private UpdatingValidatorHashMetadata(ValidatorSystemMetadata prevState) {
			this.prevState = prevState;
		}

		void update(ValidatorSystemMetadata next) throws ProcedureException {
			if (!prevState.getValidatorKey().equals(next.getValidatorKey())) {
				throw new ProcedureException("Invalid key");
			}
		}
	}

	private static class UpdatingValidatorInfo implements ReducerState {
		private final ValidatorMetaData prevState;

		private UpdatingValidatorInfo(ValidatorMetaData prevState) {
			this.prevState = prevState;
		}
	}

	private static class UpdatingDelegationFlag implements ReducerState {
		private final AllowDelegationFlag current;

		private UpdatingDelegationFlag(AllowDelegationFlag current) {
			this.current = current;
		}

		void update(AllowDelegationFlag next) throws ProcedureException {
			if (!current.getValidatorKey().equals(next.getValidatorKey())) {
				throw new ProcedureException("Invalid key update");
			}
		}
	}

	@Override
	public void main(Loader os) {
		os.substate(
			new SubstateDefinition<>(
				ValidatorSystemMetadata.class,
				SubstateTypeId.VALIDATOR_SYSTEM_META_DATA.id(),
				buf -> {
					REFieldSerialization.deserializeReservedByte(buf);
					var key = REFieldSerialization.deserializeKey(buf);
					var bytes = REFieldSerialization.deserializeFixedLengthBytes(buf, 32);
					return new ValidatorSystemMetadata(key, bytes);
				},
				(s, buf) -> {
					REFieldSerialization.serializeReservedByte(buf);
					REFieldSerialization.serializeKey(buf, s.getValidatorKey());
					REFieldSerialization.serializeFixedLengthBytes(buf, s.getData());
				},
				REFieldSerialization::deserializeKey,
				(k, buf) -> REFieldSerialization.serializeKey(buf, (ECPublicKey) k),
				k -> new ValidatorSystemMetadata((ECPublicKey) k, HashUtils.zero256().asBytes())
			)
		);

		os.procedure(new DownProcedure<>(
			VoidReducerState.class, ValidatorSystemMetadata.class,
			d -> new Authorization(
				PermissionLevel.USER,
				(r, c) -> {
					if (!c.key().map(d.getValidatorKey()::equals).orElse(false)) {
						throw new AuthorizationException("Key does not match.");
					}
				}
			),
			(d, s, r, c) -> ReducerResult.incomplete(new UpdatingValidatorHashMetadata(d))
		));
		os.procedure(new UpProcedure<>(
			UpdatingValidatorHashMetadata.class, ValidatorSystemMetadata.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				s.update(u);
				return ReducerResult.complete();
			}
		));

		os.substate(
			new SubstateDefinition<>(
				ValidatorMetaData.class,
				SubstateTypeId.VALIDATOR_META_DATA.id(),
				buf -> {
					REFieldSerialization.deserializeReservedByte(buf);
					var key = REFieldSerialization.deserializeKey(buf);
					var name = REFieldSerialization.deserializeString(buf);
					var url = REFieldSerialization.deserializeUrl(buf);
					return new ValidatorMetaData(key, name, url);
				},
				(s, buf) -> {
					REFieldSerialization.serializeReservedByte(buf);
					REFieldSerialization.serializeKey(buf, s.getValidatorKey());
					REFieldSerialization.serializeString(buf, s.getName());
					REFieldSerialization.serializeString(buf, s.getUrl());
				},
				buf -> REFieldSerialization.deserializeKey(buf),
				(k, buf) -> REFieldSerialization.serializeKey(buf, (ECPublicKey) k),
				k -> new ValidatorMetaData((ECPublicKey) k, "", "")
			)
		);

		os.procedure(new DownProcedure<>(
			VoidReducerState.class, ValidatorMetaData.class,
			d -> new Authorization(
				PermissionLevel.USER,
				(r, c) -> {
					if (!c.key().map(d.getValidatorKey()::equals).orElse(false)) {
						throw new AuthorizationException("Key does not match.");
					}
				}
			),
			(d, s, r, c) -> ReducerResult.incomplete(new UpdatingValidatorInfo(d))
		));

		os.procedure(new UpProcedure<>(
			UpdatingValidatorInfo.class, ValidatorMetaData.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				if (!Objects.equals(s.prevState.getValidatorKey(), u.getValidatorKey())) {
					throw new ProcedureException(String.format(
						"validator addresses do not match: %s != %s",
						s.prevState.getValidatorKey(), u.getValidatorKey()
					));
				}
				return ReducerResult.complete();
			}
		));

		registerRakeUpdates(os);
	}

	public void registerRakeUpdates(Loader os) {
		os.substate(new SubstateDefinition<>(
			AllowDelegationFlag.class,
			SubstateTypeId.VALIDATOR_ALLOW_DELEGATION_FLAG.id(),
			buf -> {
				REFieldSerialization.deserializeReservedByte(buf);
				var key = REFieldSerialization.deserializeKey(buf);
				var flag = REFieldSerialization.deserializeBoolean(buf);
				return new AllowDelegationFlag(key, flag);
			},
			(s, buf) -> {
				REFieldSerialization.serializeReservedByte(buf);
				REFieldSerialization.serializeKey(buf, s.getValidatorKey());
				buf.put((byte) (s.allowsDelegation() ? 1 : 0));
			},
			buf -> REFieldSerialization.deserializeKey(buf),
			(k, buf) -> REFieldSerialization.serializeKey(buf, (ECPublicKey) k),
			k -> new AllowDelegationFlag((ECPublicKey) k, false)
		));

		os.procedure(new DownProcedure<>(
			VoidReducerState.class, AllowDelegationFlag.class,
			d -> new Authorization(
				PermissionLevel.USER,
				(r, c) -> {
					if (!c.key().map(d.getValidatorKey()::equals).orElse(false)) {
						throw new AuthorizationException("Key does not match.");
					}
				}
			),
			(d, s, r, c) -> ReducerResult.incomplete(new UpdatingDelegationFlag(d))
		));

		os.procedure(new UpProcedure<>(
			UpdatingDelegationFlag.class, AllowDelegationFlag.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				s.update(u);
				return ReducerResult.complete();
			}
		));
	}
}
