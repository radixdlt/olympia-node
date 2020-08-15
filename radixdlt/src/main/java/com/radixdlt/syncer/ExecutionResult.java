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

package com.radixdlt.syncer;

import com.radixdlt.crypto.Hash;

public final class ExecutionResult {
	private final long stateVersion;
	private final boolean isEndOfEpoch;
	private final Hash timestampedSignaturesHash;

	private ExecutionResult(long stateVersion, Hash timestampedSignaturesHash, boolean isEndOfEpoch) {
		this.stateVersion = stateVersion;
		this.isEndOfEpoch = isEndOfEpoch;
		this.timestampedSignaturesHash = timestampedSignaturesHash;
	}

	public static ExecutionResult create(
		long stateVersion,
		Hash timestampedSignaturesHash,
		boolean isEndOfEpoch
	) {
		return new ExecutionResult(stateVersion, timestampedSignaturesHash, isEndOfEpoch);
	}

	public long getStateVersion() {
		return stateVersion;
	}

	public boolean isEndOfEpoch() {
		return isEndOfEpoch;
	}

	public Hash getTimestampedSignaturesHash() {
		return timestampedSignaturesHash;
	}
}
