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
package org.radix.api.services;

import org.json.JSONObject;
import org.junit.Test;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.AID;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.LedgerSearchMode;
import com.radixdlt.store.SearchCursor;
import com.radixdlt.store.StoreIndex.LedgerIndexType;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LedgerServiceTest {
	private static final String ADDRESS_STRING = "23B6fH3FekJeP6e5guhZAk6n9z4fmTo5Tngo3a11Wg5R8gsWTV2x";

	@Test
	public void atomStatusIsStoredIfPresentInLedger() {
		var ledger = mock(LedgerEntryStore.class);
		var serialization = mock(Serialization.class);
		var ledgerService = new LedgerService(ledger, serialization);

		var request = mock(JSONObject.class);
		when(request.getString("id")).thenReturn("requestId");

		var aid = AID.from(HashUtils.random256().asBytes());
		when(ledger.contains(aid)).thenReturn(true);

		var result = ledgerService.getAtomStatus(request, aid.toString());

		assertEquals("STORED", result.getJSONObject("result").getString("status"));
	}

	@Test
	public void atomStatusIsDoesNotExistsIfMissingInLedger() {
		var ledger = mock(LedgerEntryStore.class);
		var serialization = mock(Serialization.class);
		var ledgerService = new LedgerService(ledger, serialization);

		var request = mock(JSONObject.class);
		when(request.getString("id")).thenReturn("requestId");

		var aid = AID.from(HashUtils.random256().asBytes());
		when(ledger.contains(aid)).thenReturn(false);

		var result = ledgerService.getAtomStatus(request, aid.toString());

		assertEquals("DOES_NOT_EXIST", result.getJSONObject("result").getString("status"));
	}

	@Test
	public void returnsAllAtomsFoundInLedger() {
		var ledger = mock(LedgerEntryStore.class);
		var serialization = DefaultSerialization.getInstance();
		var ledgerService = new LedgerService(ledger, serialization);

		var request = mock(JSONObject.class);
		when(request.getString("id")).thenReturn("requestId");

		var aid0 = AID.from(HashUtils.random256().asBytes());
		var aid1 = AID.from(HashUtils.random256().asBytes());
		var aid2 = AID.from(HashUtils.random256().asBytes());
		var cursor = mock(SearchCursor.class);
		when(ledger.search(eq(LedgerIndexType.DUPLICATE), any(), eq(LedgerSearchMode.EXACT))).thenReturn(cursor);
		when(cursor.get()).thenReturn(aid0, aid1, aid2);
		when(cursor.next()).thenReturn(cursor, cursor, null);

		var result = ledgerService.getAtoms(request, ADDRESS_STRING);

		assertEquals(3, result.getJSONArray("result").length());
	}
}