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

package com.radixdlt.store;

import java.util.Objects;

public final class LedgerEntryStoreResult {
	private static final LedgerEntryStoreResult SUCCESS = new LedgerEntryStoreResult(null);

	private final LedgerEntryConflict conflictInfo;

	private LedgerEntryStoreResult(LedgerEntryConflict conflictInfo) {
		this.conflictInfo = conflictInfo;
	}

	public boolean isSuccess() {
		return this == SUCCESS;
	}

	public LedgerEntryStoreResult ifSuccess(Runnable runnable) {
		if (isSuccess()) {
			runnable.run();
		}

		return this;
	}

	//TODO: retrieving this info is rather complicated, but we don't use it. Cleanup this code?
	public LedgerEntryConflict getConflictInfo() {
		return conflictInfo;
	}

	public static LedgerEntryStoreResult success() {
		return SUCCESS;
	}

	public static LedgerEntryStoreResult conflict(LedgerEntryConflict conflict) {
		Objects.requireNonNull(conflict, "conflict is required");
		return new LedgerEntryStoreResult(conflict);
	}
}
