/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.atommodel.Accountable;
import com.radixdlt.client.atommodel.Ownable;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.identifiers.RRI;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt256;

/**
 *  A particle which represents an amount of unallocated tokens which can be minted.
 */
@SerializerId2("radix.particles.unallocated_tokens")
public class UnallocatedTokensParticle extends Particle implements Accountable, Ownable {

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

	public UnallocatedTokensParticle() {
		super();
	}

	public UnallocatedTokensParticle(
		UInt256 amount,
		UInt256 granularity,
		long nonce,
		RRI tokenDefinitionReference,
		Map<TokenTransition, TokenPermission> tokenPermissions
	) {
		super(tokenDefinitionReference.getAddress().euid());

		// Redundant null check added for completeness
		Objects.requireNonNull(amount, "amount is required");
		if (amount.isZero()) {
			throw new IllegalArgumentException("Amount is zero");
		}

		this.tokenDefinitionReference = tokenDefinitionReference;
		this.granularity = granularity;
		this.nonce = nonce;
		this.amount = amount;
		this.tokenPermissions = ImmutableMap.copyOf(tokenPermissions);
	}

	public Map<TokenTransition, TokenPermission> getTokenPermissions() {
		return tokenPermissions;
	}

	@JsonProperty("permissions")
	@DsonOutput(value = {Output.ALL})
	private Map<String, String> getJsonPermissions() {
		return this.tokenPermissions.entrySet().stream()
			.collect(Collectors.toMap(e -> e.getKey().name().toLowerCase(), e -> e.getValue().name().toLowerCase()));
	}

	@JsonProperty("permissions")
	private void setJsonPermissions(Map<String, String> permissions) {
		if (permissions != null) {
			this.tokenPermissions = permissions.entrySet().stream()
				.collect(Collectors.toMap(
					e -> TokenTransition.valueOf(e.getKey().toUpperCase()), e -> TokenPermission.valueOf(e.getValue().toUpperCase())
				));
		} else {
			throw new IllegalArgumentException("Permissions cannot be null.");
		}
	}

	@Override
	public Set<RadixAddress> getAddresses() {
		return Collections.singleton(this.tokenDefinitionReference.getAddress());
	}

	@Override
	public RadixAddress getAddress() {
		return this.tokenDefinitionReference.getAddress();
	}

	public RRI getTokDefRef() {
		return this.tokenDefinitionReference;
	}

	public UInt256 getGranularity() {
		return this.granularity;
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
}
