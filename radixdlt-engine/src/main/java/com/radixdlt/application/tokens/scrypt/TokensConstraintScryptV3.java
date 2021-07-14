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

import com.radixdlt.application.system.scrypt.SystemConstraintScrypt;
import com.radixdlt.application.tokens.ResourceCreatedEvent;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.ExecutionContext;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.EndProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.UInt256;

import java.nio.charset.StandardCharsets;

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
					REFieldSerialization.deserializeReservedByte(buf);
					var addr = REFieldSerialization.deserializeResourceAddr(buf);
					var granularity = REFieldSerialization.deserializeNonZeroUInt256(buf);
					if (!granularity.equals(UInt256.ONE)) {
						throw new DeserializeException("Granularity must be one.");
					}
					var isMutable = REFieldSerialization.deserializeBoolean(buf);
					var minter = REFieldSerialization.deserializeOptionalKey(buf);
					return new TokenResource(addr, granularity, isMutable, minter.orElse(null));
				},
				(s, buf) -> {
					REFieldSerialization.serializeReservedByte(buf);
					REFieldSerialization.serializeREAddr(buf, s.getAddr());
					REFieldSerialization.serializeUInt256(buf, UInt256.ONE);
					REFieldSerialization.serializeBoolean(buf, s.isMutable());
					REFieldSerialization.serializeOptionalKey(buf, s.getOwner());
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
					return new TokenResourceMetadata(addr, name, description, iconUrl, url);
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
		private final byte[] arg;
		private NeedMetadata(byte[] arg, TokenResource tokenResource) {
			this.arg = arg;
			this.tokenResource = tokenResource;
		}

		void metadata(TokenResourceMetadata metadata, ExecutionContext context) throws ProcedureException {
			if (!metadata.getAddr().equals(tokenResource.getAddr())) {
				throw new ProcedureException("Addresses don't match");
			}

			var symbol = new String(arg, StandardCharsets.UTF_8);
			context.emitEvent(new ResourceCreatedEvent(symbol, tokenResource, metadata));
		}
	}

	private void defineTokenCreation(Loader os) {
		os.procedure(new UpProcedure<>(
			SystemConstraintScrypt.REAddrClaim.class, TokenResource.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				if (!u.getAddr().equals(s.getAddr())) {
					throw new ProcedureException("Addresses don't match");
				}

				if (u.isMutable()) {
					return ReducerResult.incomplete(new NeedMetadata(s.getArg(), u));
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
				return ReducerResult.incomplete(new NeedMetadata(s.arg, s.tokenResource));
			}
		));

		os.procedure(new UpProcedure<>(
			NeedMetadata.class, TokenResourceMetadata.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				s.metadata(u, c);
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
			d -> d.bucket().withdrawAuthorization(),
			(d, s, r, c) -> {
				var state = new TokenHoldingBucket(d.toTokens());
				return ReducerResult.incomplete(state);
			}
		));

		// More Withdraws
		os.procedure(new DownProcedure<>(
			TokenHoldingBucket.class, TokensInAccount.class,
			d -> d.bucket().withdrawAuthorization(),
			(d, s, r, c) -> {
				s.deposit(d.toTokens());
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