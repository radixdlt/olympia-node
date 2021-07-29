/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
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
				k -> ValidatorMetaData.createVirtual((ECPublicKey) k)
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
