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

package com.radixdlt.application.tokens.scrypt;

import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.EndProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.UInt256;

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
				SubstateTypeId.TOKEN_RESOURCE.id(),
				buf -> {
					var type = buf.get();
					var addr = REFieldSerialization.deserializeResourceAddr(buf);
					var granularity = REFieldSerialization.deserializeNonZeroUInt256(buf);
					if (!granularity.equals(UInt256.ONE)) {
						throw new DeserializeException("Granularity must be one.");
					}
					final ECPublicKey minter;
					if (type == (byte) 0x1) {
						return new TokenResource(addr, granularity, true, null);
					} else if (type == (byte) 0x3) {
						minter = REFieldSerialization.deserializeKey(buf);
						return new TokenResource(addr, granularity, true, minter);
					} else if (type == (byte) 0x0) {
						return new TokenResource(addr, granularity, false, null);
					} else {
						throw new DeserializeException("Unknown token def type " + type);
					}
				},
				(s, buf) -> {
					byte type = 0;
					type |= (s.isMutable() ? 0x1 : 0x0);
					type |= (s.getOwner().isPresent() ? 0x2 : 0x0);
					buf.put(type);
					REFieldSerialization.serializeREAddr(buf, s.getAddr());
					REFieldSerialization.serializeUInt256(buf, UInt256.ONE);
					s.getOwner().ifPresent(k -> REFieldSerialization.serializeKey(buf, k));
				}
			)
		);

		os.substate(
			new SubstateDefinition<>(
				TokenResourceMetadata.class,
				SubstateTypeId.TOKEN_RESOURCE_METADATA.id(),
				buf -> {
					REFieldSerialization.deserializeReservedByte(buf);
					var addr = REFieldSerialization.deserializeResourceAddr(buf);
					var name = REFieldSerialization.deserializeString(buf);
					var description = REFieldSerialization.deserializeString(buf);
					var url = REFieldSerialization.deserializeUrl(buf);
					var iconUrl = REFieldSerialization.deserializeUrl(buf);
					return new TokenResourceMetadata(addr, name, description, url, iconUrl);
				},
				(s, buf) -> {
					REFieldSerialization.serializeReservedByte(buf);
					REFieldSerialization.serializeREAddr(buf, s.getAddr());
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
				SubstateTypeId.TOKENS.id(),
				buf -> {
					REFieldSerialization.deserializeReservedByte(buf);
					var holdingAddr = REFieldSerialization.deserializeAccountREAddr(buf);
					var addr = REFieldSerialization.deserializeResourceAddr(buf);
					var amount = REFieldSerialization.deserializeNonZeroUInt256(buf);
					return new TokensInAccount(holdingAddr, addr, amount);
				},
				(s, buf) -> {
					REFieldSerialization.serializeReservedByte(buf);
					REFieldSerialization.serializeREAddr(buf, s.getHoldingAddr());
					REFieldSerialization.serializeREAddr(buf, s.getResourceAddr());
					buf.put(s.getAmount().toByteArray());
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

	private static class NeedMetadata implements ReducerState {
		private final TokenResource tokenResource;
		private NeedMetadata(TokenResource tokenResource) {
			this.tokenResource = tokenResource;
		}

		void metadata(TokenResourceMetadata metadata) throws ProcedureException {
			if (!metadata.getAddr().equals(tokenResource.getAddr())) {
				throw new ProcedureException("Addresses don't match");
			}
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
					return ReducerResult.incomplete(new NeedMetadata(u));
				}

				if (!u.getGranularity().equals(UInt256.ONE)) {
					throw new ProcedureException("Granularity must be one.");
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
				return ReducerResult.incomplete(new NeedMetadata(s.tokenResource));
			}
		));

		os.procedure(new UpProcedure<>(
			NeedMetadata.class, TokenResourceMetadata.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				s.metadata(u);
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
				var tokensInAccount = d.getSubstate();
				var state = new TokenHoldingBucket(tokensInAccount.toTokens());
				return ReducerResult.incomplete(state);
			}
		));

		// More Withdraws
		os.procedure(new DownProcedure<>(
			TokenHoldingBucket.class, TokensInAccount.class,
			d -> d.getSubstate().bucket().withdrawAuthorization(),
			(d, s, r) -> {
				var tokensInAccount = d.getSubstate();
				s.deposit(tokensInAccount.toTokens());
				return ReducerResult.incomplete(s);
			}
		));

		// Deposit
		os.procedure(new UpProcedure<>(
			TokenHoldingBucket.class, TokensInAccount.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				s.withdraw(u.getResourceAddr(), u.getAmount());
				return ReducerResult.incomplete(s);
			}
		));
	}
}