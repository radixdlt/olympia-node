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

package com.radixdlt.accounting;

import com.radixdlt.atommodel.tokens.Bucket;
import com.radixdlt.identifiers.REAddr;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class TwoActorEntry {
	private final Bucket from;
	private final Bucket to;
	private final REAddr resourceAddr;
	private final BigInteger amount;

	private TwoActorEntry(Bucket from, Bucket to, REAddr resourceAddr, BigInteger amount) {
		this.from = from;
		this.to = to;
		this.resourceAddr = resourceAddr;
		this.amount = amount;
	}

	public Optional<Bucket> from() {
		return Optional.ofNullable(from);
	}

	public Optional<Bucket> to() {
		return Optional.ofNullable(to);
	}

	public Optional<REAddr> resourceAddr() {
		return Optional.ofNullable(resourceAddr);
	}

	public BigInteger amount() {
		return amount;
	}

	public static Optional<TwoActorEntry> parse(Map<Bucket, BigInteger> bucketAccounting) {
		if (bucketAccounting.isEmpty() || bucketAccounting.size() > 2) {
			return Optional.empty();
		}

		// TODO: Because of betanetV1/V2 can't rely on ordering
		// TODO: Fix for mainnet
		var resourceAddrs = bucketAccounting.keySet().stream()
			.map(Bucket::resourceAddr)
			.distinct()
			.collect(Collectors.toList());
		if (resourceAddrs.size() != 1) {
			return Optional.empty();
		}

		var amts = bucketAccounting.values().stream()
			.map(BigInteger::abs)
			.distinct()
			.collect(Collectors.toList());
		if (amts.size() != 1) {
			return Optional.empty();
		}

		var resourceAddr = resourceAddrs.get(0);
		var amt = amts.get(0);
		var negative = bucketAccounting.entrySet().stream()
			.filter(e -> e.getValue().signum() < 0)
			.map(Map.Entry::getKey)
			.findAny();
		var positive = bucketAccounting.entrySet().stream()
			.filter(e -> e.getValue().signum() > 0)
			.map(Map.Entry::getKey)
			.findAny();

		if (negative.isEmpty()) {
			var mint = positive.orElseThrow();
			return Optional.of(new TwoActorEntry(null, mint, resourceAddr, amt));
		} else if (positive.isEmpty()) {
			var burn = negative.orElseThrow();
			return Optional.of(new TwoActorEntry(burn, null, resourceAddr, amt));
		} else {
			var from = negative.orElseThrow();
			var to = positive.orElseThrow();
			return Optional.of(new TwoActorEntry(from, to, resourceAddr, amt));
		}
	}
}
