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

/**
 * Event which represents that the committed state has been synced
 */
public final class CommittedStateSync {
	private final LedgerHeader header;

	public CommittedStateSync(LedgerHeader header) {
		this.header = header;
	}

	public LedgerHeader getHeader() {
		return header;
	}

	@Override
	public String toString() {
		return String.format("%s{header=%s", this.getClass().getSimpleName(), header);
	}
}
