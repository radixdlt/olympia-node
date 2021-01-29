/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.store.mvstore;

import com.radixdlt.identifiers.AID;
import com.radixdlt.store.SearchCursor;
import com.radixdlt.store.StoreIndex.LedgerIndexType;
import org.h2.mvstore.MVMap;
import org.h2.value.VersionedValue;

public class DuplicateSearchCursor implements SearchCursor {
	private final MVMap<byte[], VersionedValue> map;
	private final byte[] key;
	private final KeyList list;
	private int index;

	public DuplicateSearchCursor(MVMap<byte[], VersionedValue> map, byte[] key, boolean goLast) {
		this.map = map;
		this.key = key;
		this.list = KeyList.fromBytes((byte[]) map.get(key).getCurrentValue());
		this.index = Math.max(0, goLast ? list.size() - 1 : 0);
	}

	@Override
	public LedgerIndexType getType() {
		return LedgerIndexType.DUPLICATE;
	}

	@Override
	public AID get() {
		return AID.from(list.key(index), Long.BYTES + 1);
	}

	@Override
	public SearchCursor next() {
		if (index < (list.size() - 1)) {
			index++;
			return this;
		}

		return makeCursor(map.higherKey(key), false);
	}

	@Override
	public SearchCursor previous() {
		if (index > 0) {
			index--;
			return this;
		}

		return makeCursor(map.lowerKey(key), true);
	}

	@Override
	public SearchCursor first() {
		byte[] firstKey = map.firstKey();
		return makeCursor(firstKey, false);
	}

	@Override
	public SearchCursor last() {
		return makeCursor(map.lastKey(), true);
	}

	private SearchCursor makeCursor(byte[] nextKey, boolean goLast) {
		return nextKey == null
			   ? null
			   : new DuplicateSearchCursor(map, nextKey, goLast);
	}
}
