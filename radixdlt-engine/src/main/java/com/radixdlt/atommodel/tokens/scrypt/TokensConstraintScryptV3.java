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

package com.radixdlt.atommodel.tokens.scrypt;

import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atommodel.tokens.state.TokenResource;
import com.radixdlt.atommodel.tokens.state.TokensInAccount;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.AuthorizationException;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.EndProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

import java.util.Set;

public final class TokensConstraintScryptV3 implements ConstraintScrypt {
	@Override
	public void main(Loader os) {
		registerParticles(os);
		defineTokenCreation(os);
		defineMintTransferBurn(os);
	}

	private void registerParticles(Loader os) {
		os.substate(
			new SubstateDefinition<>(
				TokenResource.class,
				Set.of(SubstateTypeId.TOKEN_DEF.id()),
				(b, buf) -> {
					var rri = REFieldSerialization.deserializeREAddr(buf);
					var type = buf.get();
					final UInt256 supply;
					final ECPublicKey minter;
					if (type == 0) {
						supply = null;
						minter = null;
					} else if (type == 1) {
						supply = null;
						minter = REFieldSerialization.deserializeKey(buf);
					} else if (type == 2) {
						supply = REFieldSerialization.deserializeNonZeroUInt256(buf);
						minter = null;
					} else {
						throw new DeserializeException("Unknown token def type " + type);
					}
					var name = REFieldSerialization.deserializeString(buf);
					var description = REFieldSerialization.deserializeString(buf);
					var url = REFieldSerialization.deserializeUrl(buf);
					var iconUrl = REFieldSerialization.deserializeUrl(buf);
					return new TokenResource(rri, name, description, iconUrl, url, supply, minter);
				},
				(s, buf) -> {
					buf.put(SubstateTypeId.TOKEN_DEF.id());
					REFieldSerialization.serializeREAddr(buf, s.getAddr());
					s.getSupply().ifPresentOrElse(
						i -> {
							buf.put((byte) 2);
							buf.put(i.toByteArray());
						},
						() -> {
							s.getOwner().ifPresentOrElse(
								m -> {
									buf.put((byte) 1);
									REFieldSerialization.serializeKey(buf, m);
								},
								() -> buf.put((byte) 0)
							);
						}
					);
					REFieldSerialization.serializeString(buf, s.getName());
					REFieldSerialization.serializeString(buf, s.getDescription());
					REFieldSerialization.serializeString(buf, s.getUrl());
					REFieldSerialization.serializeString(buf, s.getIconUrl());
				}
			)
		);

		os.substate(
			new SubstateDefinition<>(
				TokensInAccount.class,
				Set.of(SubstateTypeId.TOKENS.id(), SubstateTypeId.TOKENS_LOCKED.id()),
				(b, buf) -> {
					var rri = REFieldSerialization.deserializeREAddr(buf);
					var holdingAddr = REFieldSerialization.deserializeREAddr(buf);
					if (!holdingAddr.isAccount()) {
						throw new DeserializeException("Tokens must be held by holding address: " + holdingAddr);
					}
					var amount = REFieldSerialization.deserializeNonZeroUInt256(buf);

					if (b == SubstateTypeId.TOKENS.id()) {
						return new TokensInAccount(holdingAddr, amount, rri);
					} else {
						var epochUnlocked = buf.getLong();
						return new TokensInAccount(holdingAddr, amount, rri, epochUnlocked);
					}
				},
				(s, buf) -> {
					s.getEpochUnlocked().ifPresentOrElse(
						e -> buf.put(SubstateTypeId.TOKENS_LOCKED.id()),
						() -> buf.put(SubstateTypeId.TOKENS.id())
					);
					REFieldSerialization.serializeREAddr(buf, s.getResourceAddr());
					REFieldSerialization.serializeREAddr(buf, s.getHoldingAddr());
					buf.put(s.getAmount().toByteArray());
					s.getEpochUnlocked().ifPresent(buf::putLong);
				}
			)
		);
	}

	private static class NeedFixedTokenSupply implements ReducerState {
		private final byte[] arg;
		private final TokenResource tokenResource;
		private NeedFixedTokenSupply(byte[] arg, TokenResource tokenResource) {
			this.arg = arg;
			this.tokenResource = tokenResource;
		}
	}

	private void defineTokenCreation(Loader os) {
		os.procedure(new UpProcedure<>(
			CMAtomOS.REAddrClaim.class, TokenResource.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				if (!u.getAddr().equals(s.getAddr())) {
					throw new ProcedureException("Addresses don't match");
				}

				if (u.isMutable()) {
					return ReducerResult.complete();
				}

				return ReducerResult.incomplete(new NeedFixedTokenSupply(s.getArg(), u));
			}
		));

		os.procedure(new UpProcedure<>(
			NeedFixedTokenSupply.class, TokensInAccount.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				if (!u.getResourceAddr().equals(s.tokenResource.getAddr())) {
					throw new ProcedureException("Addresses don't match.");
				}

				if (!u.getAmount().equals(s.tokenResource.getSupply().orElseThrow())) {
					throw new ProcedureException("Initial supply doesn't match.");
				}

				return ReducerResult.complete();
			}
		));
	}

	private void defineMintTransferBurn(Loader os) {
		// Mint
		os.procedure(new UpProcedure<>(
			VoidReducerState.class, TokensInAccount.class,
			u -> {
				if (u.getResourceAddr().isNativeToken()) {
					return new Authorization(PermissionLevel.SYSTEM, (r, c) -> { });
				}

				return new Authorization(PermissionLevel.USER, (r, c) -> {
					var tokenDef = (TokenResource) r.loadAddr(u.getResourceAddr())
						.orElseThrow(() -> new AuthorizationException("Invalid token address: " + u.getResourceAddr()));
					tokenDef.verifyMintAuthorization(c.key());
				});
			},
			(s, u, c, r) -> {
				c.verifyCanAllocAndDestroyResources();
				return ReducerResult.complete();
			}
		));

		// Burn
		os.procedure(new EndProcedure<>(
			TokenHoldingBucket.class,
			s -> new Authorization(PermissionLevel.USER, (r, c) -> {
				if (s.isEmpty()) {
					return;
				}
				var tokenDef = (TokenResource) r.loadAddr(s.getResourceAddr())
					.orElseThrow(() -> new AuthorizationException("Invalid token address: " + s.getResourceAddr()));
				tokenDef.verifyBurnAuthorization(c.key());
			}),
			TokenHoldingBucket::destroy
		));

		// Initial Withdraw
		os.procedure(new DownProcedure<>(
			VoidReducerState.class, TokensInAccount.class,
			d -> d.getSubstate().bucket().withdrawAuthorization(),
			(d, s, r) -> {
				var tokens = d.getSubstate();
				var state = new TokenHoldingBucket(
					tokens.getResourceAddr(),
					UInt384.from(tokens.getAmount())
				);
				return ReducerResult.incomplete(state);
			}
		));

		// More Withdraws
		os.procedure(new DownProcedure<>(
			TokenHoldingBucket.class, TokensInAccount.class,
			d -> d.getSubstate().bucket().withdrawAuthorization(),
			(d, s, r) -> {
				var tokens = d.getSubstate();
				var nextState = s.deposit(
					tokens.getResourceAddr(),
					tokens.getAmount()
				);
				return ReducerResult.incomplete(nextState);
			}
		));

		// Deposit
		os.procedure(new UpProcedure<>(
			TokenHoldingBucket.class, TokensInAccount.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				var nextState = s.withdraw(u.getResourceAddr(), u.getAmount());
				return ReducerResult.incomplete(nextState);
			}
		));
	}
}