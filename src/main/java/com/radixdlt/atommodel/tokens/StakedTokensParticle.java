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

package com.radixdlt.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt256;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 *  A particle which represents an amount of staked fungible tokens
 *  owned by some key owner, stored in an account and staked to a delegate address.
 */
@SerializerId2("radix.particles.staked_tokens")
public final class StakedTokensParticle extends Particle {
	@JsonProperty("delegateAddress")
	@DsonOutput(Output.ALL)
	private RadixAddress delegateAddress;

	@JsonProperty("address")
	@DsonOutput(Output.ALL)
	private RadixAddress address;

	@JsonProperty("tokenDefinitionReference")
	@DsonOutput(Output.ALL)
	private RRI tokenDefinitionReference;

	@JsonProperty("granularity")
	@DsonOutput(Output.ALL)
	private UInt256 granularity;

	@JsonProperty("planck")
	@DsonOutput(Output.ALL)
	private long planck;

	@JsonProperty("nonce")
	@DsonOutput(Output.ALL)
	private long nonce;

	@JsonProperty("amount")
	@DsonOutput(Output.ALL)
	private UInt256 amount;

	private Map<TokenTransition, TokenPermission> tokenPermissions;

	public StakedTokensParticle() {
		super();
		this.tokenPermissions = ImmutableMap.of();
	}

	public StakedTokensParticle(
		RadixAddress delegateAddress,
		RadixAddress address,
		UInt256 amount,
		UInt256 granularity,
		RRI tokenDefinitionReference,
		long planck,
		Map<TokenTransition, TokenPermission> tokenPermissions
	) {
		super(address.euid());

		this.delegateAddress = Objects.requireNonNull(delegateAddress);
		this.address = Objects.requireNonNull(address);
		this.granularity = Objects.requireNonNull(granularity);
		this.tokenDefinitionReference = Objects.requireNonNull(tokenDefinitionReference);
		this.planck = planck;
		this.nonce = System.nanoTime();
		this.amount = Objects.requireNonNull(amount);
		this.tokenPermissions = ImmutableMap.copyOf(tokenPermissions);
	}

	public RadixAddress getDelegateAddress() {
		return delegateAddress;
	}

	public RadixAddress getAddress() {
		return this.address;
	}

	public RRI getTokDefRef() {
		return this.tokenDefinitionReference;
	}

	public UInt256 getGranularity() {
		return this.granularity;
	}

	public Map<TokenTransition, TokenPermission> getTokenPermissions() {
		return tokenPermissions;
	}

	public TokenPermission getTokenPermission(TokenTransition transition) {
		return tokenPermissions.get(transition);
	}

	@JsonProperty("permissions")
	@DsonOutput(Output.ALL)
	private Map<String, String> getJsonPermissions() {
		return this.tokenPermissions.entrySet().stream()
			.collect(Collectors.toMap(e -> e.getKey().name().toLowerCase(), e -> e.getValue().name().toLowerCase()));
	}

	@JsonProperty("permissions")
	private void setJsonPermissions(Map<String, String> permissions) {
		if (permissions != null) {
			this.tokenPermissions = permissions.entrySet().stream()
				.collect(
					Collectors.toMap(
						e -> TokenTransition.valueOf(e.getKey().toUpperCase()),
						e -> TokenPermission.valueOf(e.getValue().toUpperCase())
					)
				);
		} else {
			throw new IllegalArgumentException("Permissions cannot be null.");
		}
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s:%s:%s:%s:%s:%s]",
			getClass().getSimpleName(),
			tokenDefinitionReference,
			amount,
			granularity,
			address,
			delegateAddress,
			planck,
			nonce
		);
	}

	public UInt256 getAmount() {
		return this.amount;
	}

	public long getPlanck() {
		return this.planck;
	}

	public long getNonce() {
		return this.nonce;
	}
}
