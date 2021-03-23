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

import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.AID;
import com.radixdlt.store.AtomIndex;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LedgerServiceTest {
	@Test
	public void atomStatusIsStoredIfPresentInLedger() {
		var ledger = mock(AtomIndex.class);
		var ledgerService = new LedgerService(ledger);

		var request = mock(JSONObject.class);
		when(request.getString("id")).thenReturn("requestId");

		var aid = AID.from(HashUtils.random256().asBytes());
		when(ledger.contains(aid)).thenReturn(true);

		var result = ledgerService.getAtomStatus(request, aid.toString());

		assertEquals("STORED", result.getJSONObject("result").getString("status"));
	}

	@Test
	public void atomStatusIsDoesNotExistsIfMissingInLedger() {
		var ledger = mock(AtomIndex.class);
		var ledgerService = new LedgerService(ledger);

		var request = mock(JSONObject.class);
		when(request.getString("id")).thenReturn("requestId");

		var aid = AID.from(HashUtils.random256().asBytes());
		when(ledger.contains(aid)).thenReturn(false);

		var result = ledgerService.getAtomStatus(request, aid.toString());

		assertEquals("DOES_NOT_EXIST", result.getJSONObject("result").getString("status"));
	}
}