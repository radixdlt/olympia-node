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
package org.radix.api.jsonrpc.handler;

import org.junit.Test;
import org.radix.api.services.LedgerService;

import com.radixdlt.identifiers.AID;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public class LedgerHandlerTest {
	private static final LedgerService ledgerService = mock(LedgerService.class);
	private static final LedgerHandler ledgerHandler = new LedgerHandler(ledgerService);

	@Test
	public void testHandleGetAtomStatus() {
		var jsonAtom = jsonObject().put("aid", AID.ZERO.toString());
		var request = jsonObject().put("id", 124).put("params", jsonAtom);
		when(ledgerService.getAtomStatus(any(), any())).thenReturn(jsonObject().put("content", "abc"));

		var response = ledgerHandler.handleGetAtomStatus(request);

		assertEquals("{\"content\":\"abc\"}", response.toString());
	}
}