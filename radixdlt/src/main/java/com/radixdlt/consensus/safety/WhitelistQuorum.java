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

package com.radixdlt.consensus.safety;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.ECPublicKey;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * A quorum consisting of a static whitelist.
 */
public class WhitelistQuorum implements  QuorumRequirements {
	private final ImmutableSet<EUID> whitelist;

	private WhitelistQuorum(ImmutableSet<EUID> whitelist) {
		this.whitelist = whitelist;
	}

	@Override
	public int numRequiredVotes() {
		return whitelist.size();
	}

	@Override
	public boolean accepts(EUID author) {
		return whitelist.contains(author);
	}

	public static WhitelistQuorum from(Stream<ECPublicKey> whitelistedNodes) {
		return new WhitelistQuorum(whitelistedNodes
			.map(ECPublicKey::getUID)
			.collect(ImmutableSet.toImmutableSet()));
	}

	public static WhitelistQuorum from(ECPublicKey... whitelistedNodes) {
		return new WhitelistQuorum(Arrays.stream(whitelistedNodes)
			.map(ECPublicKey::getUID)
			.collect(ImmutableSet.toImmutableSet()));
	}

	public static WhitelistQuorum from(EUID... whitelistedNodes) {
		return new WhitelistQuorum(ImmutableSet.copyOf(whitelistedNodes));
	}
}
