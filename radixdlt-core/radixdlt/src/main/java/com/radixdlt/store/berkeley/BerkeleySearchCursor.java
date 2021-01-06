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

package com.radixdlt.store.berkeley;

import com.radixdlt.identifiers.AID;
import com.radixdlt.store.SearchCursor;
import com.radixdlt.store.StoreIndex;
import org.bouncycastle.util.Arrays;

import java.util.Objects;

/**
 * A Tempo implementation of a {@link SearchCursor}
 */
public class BerkeleySearchCursor implements SearchCursor {
	private final StoreIndex.LedgerIndexType type;
	private final byte[] primary;
	private final byte[] index;
	private final BerkeleyLedgerEntryStore store;

	BerkeleySearchCursor(BerkeleyLedgerEntryStore store, StoreIndex.LedgerIndexType type, byte[] primary, byte[] index) {
		this.type = type;
		this.primary = Arrays.clone(Objects.requireNonNull(primary));
		this.index = Arrays.clone(Objects.requireNonNull(index));
		this.store = store;
	}

	@Override
	public StoreIndex.LedgerIndexType getType() {
		return this.type;
	}

	public byte[] getPrimary() {
		return this.primary;
	}

	public byte[] getIndex() {
		return this.index;
	}

	@Override
	public AID get() {
		return AID.from(this.primary, Long.BYTES + 1);
	}

	@Override
	public SearchCursor next() {
		return this.store.getNext(this);
	}

	@Override
	public SearchCursor previous() {
		return this.store.getPrev(this);
	}

	@Override
	public SearchCursor first() {
		return this.store.getFirst(this);
	}

	@Override
	public SearchCursor last() {
		return this.store.getLast(this);
	}
}
