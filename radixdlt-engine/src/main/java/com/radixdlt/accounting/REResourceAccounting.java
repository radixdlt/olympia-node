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

import com.radixdlt.application.tokens.Bucket;
import com.radixdlt.application.tokens.ResourceInBucket;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class REResourceAccounting {
	private final Map<Bucket, BigInteger> bucketAccounting;
	private final Map<ECPublicKey, BigInteger> stakeOwnershipAccounting;
	private final Map<REAddr, BigInteger> resourceAccounting;

	private REResourceAccounting(
		Map<Bucket, BigInteger> bucketAccounting,
		Map<ECPublicKey, BigInteger> stakeOwnershipAccounting,
		Map<REAddr, BigInteger> resourceAccounting
	) {
		this.bucketAccounting = bucketAccounting;
		this.stakeOwnershipAccounting = stakeOwnershipAccounting;
		this.resourceAccounting = resourceAccounting;
	}

	public Map<Bucket, BigInteger> bucketAccounting() {
		return bucketAccounting;
	}

	public Map<ECPublicKey, BigInteger> stakeOwnershipAccounting() {
		return stakeOwnershipAccounting;
	}

	public Map<REAddr, BigInteger> resourceAccounting() {
		return resourceAccounting;
	}

	private static BigInteger sumIfZeroThenNull(BigInteger a, BigInteger b) {
		var sum = a.add(b);
		return sum.equals(BigInteger.ZERO) ? null : sum;
	}

	public static REResourceAccounting compute(List<REStateUpdate> updates) {
		Map<Bucket, BigInteger> bucketAccounting = new HashMap<>();

		for (var update : updates) {
			var substate = update.getParsed();
			if (substate instanceof ResourceInBucket) {
				var resourceInBucket = (ResourceInBucket) substate;
				bucketAccounting.merge(
					resourceInBucket.bucket(),
					new BigInteger(update.isBootUp() ? 1 : -1, resourceInBucket.getAmount().toByteArray(), 0, UInt256.BYTES),
					REResourceAccounting::sumIfZeroThenNull
				);
			}
		}

		var stakeOwnershipAccounting = bucketAccounting.entrySet().stream()
			.filter(e -> e.getKey().resourceAddr() == null)
			.collect(Collectors.toMap(
				e -> e.getKey().getValidatorKey(),
				Map.Entry::getValue,
				REResourceAccounting::sumIfZeroThenNull
			));
		var resourceAccounting = bucketAccounting.entrySet().stream()
			.filter(e -> e.getKey().resourceAddr() != null)
			.collect(Collectors.toMap(
				e -> e.getKey().resourceAddr(),
				Map.Entry::getValue,
				REResourceAccounting::sumIfZeroThenNull
			));

		return new REResourceAccounting(
			bucketAccounting,
			stakeOwnershipAccounting,
			resourceAccounting
		);
	}
}
