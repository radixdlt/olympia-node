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
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt256;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *  A particle which represents an amount of unallocated tokens which can be minted.
 */
@SerializerId2("radix.particles.unallocated_tokens")
public final class UnallocatedTokensParticle extends Particle {
	@JsonProperty("tokenDefinitionReference")
	@DsonOutput(DsonOutput.Output.ALL)
	private RRI tokenDefinitionReference;

	@JsonProperty("granularity")
	@DsonOutput(DsonOutput.Output.ALL)
	private UInt256 granularity;

	@JsonProperty("nonce")
	@DsonOutput(DsonOutput.Output.ALL)
	private long nonce;

	@JsonProperty("amount")
	@DsonOutput(DsonOutput.Output.ALL)
	private UInt256 amount;

	private Map<TokenTransition, TokenPermission> tokenPermissions;

	UnallocatedTokensParticle() {
		// Serializer only
		super();
	}

	public UnallocatedTokensParticle(
		UInt256 amount,
		UInt256 granularity,
		RRI tokenDefinitionReference,
		Map<TokenTransition, TokenPermission> tokenPermissions
	) {
		this.granularity = Objects.requireNonNull(granularity);
		this.tokenDefinitionReference = Objects.requireNonNull(tokenDefinitionReference);
		this.nonce = System.nanoTime();
		this.amount = Objects.requireNonNull(amount);
		this.tokenPermissions = ImmutableMap.copyOf(tokenPermissions);
	}

	public UnallocatedTokensParticle(
			UInt256 amount,
			UInt256 granularity,
			RRI tokenDefinitionReference,
			Map<TokenTransition, TokenPermission> tokenPermissions,
			long nonce
	) {
		this.granularity = Objects.requireNonNull(granularity);
		this.tokenDefinitionReference = Objects.requireNonNull(tokenDefinitionReference);
		this.nonce = System.nanoTime();
		this.amount = Objects.requireNonNull(amount);
		this.tokenPermissions = ImmutableMap.copyOf(tokenPermissions);
		this.nonce = nonce;
	}

	@Override
	public Set<EUID> getDestinations() {
		return ImmutableSet.of(this.tokenDefinitionReference.getAddress().euid());
	}

	public RadixAddress getAddress() {
		return this.tokenDefinitionReference.getAddress();
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
		TokenPermission tokenPermission = tokenPermissions.get(transition);
		if (tokenPermission != null) {
			return tokenPermission;
		}

		throw new IllegalArgumentException("No token permission set for " + transition + " in " + tokenPermissions);
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
		return String.format("%s[%s:%s:%s:%s]",
			getClass().getSimpleName(),
			String.valueOf(tokenDefinitionReference),
			String.valueOf(amount),
			String.valueOf(granularity),
			nonce);
	}

	public UInt256 getAmount() {
		return this.amount;
	}

	public long getNonce() {
		return this.nonce;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof UnallocatedTokensParticle)) {
			return false;
		}
		UnallocatedTokensParticle that = (UnallocatedTokensParticle) o;
		return nonce == that.nonce
				&& Objects.equals(tokenDefinitionReference, that.tokenDefinitionReference)
				&& Objects.equals(granularity, that.granularity)
				&& Objects.equals(amount, that.amount)
				&& Objects.equals(tokenPermissions, that.tokenPermissions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tokenDefinitionReference, granularity, nonce, amount, tokenPermissions);
	}
}
