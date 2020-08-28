/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.consensus;

import com.radixdlt.consensus.bft.BFTValidatorSet;
import java.util.Optional;

/**
 * Results from a prepared stage execution. All fields should be persisted on ledger.
 */
public final class PreparedCommand {
	private final long stateVersion;
	private final long timestamp;
	private final BFTValidatorSet nextValidatorSet;

	private PreparedCommand(long stateVersion, long timestamp, BFTValidatorSet nextValidatorSet) {
		this.stateVersion = stateVersion;
		this.nextValidatorSet = nextValidatorSet;
		this.timestamp = timestamp;
	}

	public static PreparedCommand create(
		long stateVersion,
		long timestamp
	) {
		return new PreparedCommand(stateVersion, timestamp, null);
	}

	public static PreparedCommand create(
		long stateVersion,
		long timestamp,
		BFTValidatorSet nextValidatorSet
	) {
		return new PreparedCommand(stateVersion, timestamp, nextValidatorSet);
	}

	public long getStateVersion() {
		return stateVersion;
	}

	public Optional<BFTValidatorSet> getNextValidatorSet() {
		return Optional.ofNullable(nextValidatorSet);
	}

	public long timestamp() {
		return this.timestamp;
	}
}
