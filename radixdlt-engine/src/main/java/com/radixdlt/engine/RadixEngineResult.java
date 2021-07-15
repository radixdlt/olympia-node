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

package com.radixdlt.engine;

import com.radixdlt.constraintmachine.REProcessedTxn;

import java.util.List;

public final class RadixEngineResult {
	private final List<REProcessedTxn> processedTxns;
	private final long verificationTime;
	private final long storeTime;

	private RadixEngineResult(List<REProcessedTxn> processedTxns, long verificationTime, long storeTime) {
		this.processedTxns = processedTxns;
		this.verificationTime = verificationTime;
		this.storeTime = storeTime;
	}

	public static RadixEngineResult create(
		List<REProcessedTxn> processedTxns,
		long verificationTime,
		long storeTime
	) {
		return new RadixEngineResult(processedTxns, verificationTime, storeTime);
	}

	public long getVerificationTime() {
		return verificationTime;
	}

	public long getStoreTime() {
		return storeTime;
	}

	// TODO: Create separate class for single transaction results
	public REProcessedTxn getProcessedTxn() {
		if (processedTxns.size() > 1) {
			throw new IllegalStateException("Multiple processed transactions.");
		}
		return processedTxns.get(0);
	}

	public List<REProcessedTxn> getProcessedTxns() {
		return processedTxns;
	}
}
