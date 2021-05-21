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
package com.radixdlt.api.handler;

import org.json.JSONObject;
import org.junit.Test;

import com.radixdlt.api.data.ActionEntry;
import com.radixdlt.api.data.TransactionStatus;
import com.radixdlt.api.data.TxHistoryEntry;
import com.radixdlt.api.service.TransactionStatusService;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.api.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.data.TransactionStatus.CONFIRMED;
import static com.radixdlt.api.data.TransactionStatus.FAILED;
import static com.radixdlt.api.data.TransactionStatus.PENDING;
import static com.radixdlt.api.data.TransactionStatus.TRANSACTION_NOT_FOUND;

public class ArchiveTransactionsHandlerTest {
	private final TransactionStatusService transactionStatusService = mock(TransactionStatusService.class);
	private final ArchiveTransactionsHandler handler = new ArchiveTransactionsHandler(transactionStatusService);

	@Test
	public void testLookupTransactionPositional() {
		var txId = randomAID();
		var entry = createTxHistoryEntry(txId);

		when(transactionStatusService.getTransaction(txId)).thenReturn(Result.ok(entry));

		var params = jsonArray().put(txId.toString());
		var response = handler.handleTransactionsLookupTransaction(requestWith(params));

		assertNotNull(response);
		validateHistoryEntry(entry, response.getJSONObject("result"));
	}

	@Test
	public void testLookupTransactionNamed() {
		var txId = randomAID();
		var entry = createTxHistoryEntry(txId);

		when(transactionStatusService.getTransaction(txId)).thenReturn(Result.ok(entry));

		var params = jsonObject().put("txID", txId.toString());
		var response = handler.handleTransactionsLookupTransaction(requestWith(params));

		assertNotNull(response);
		validateHistoryEntry(entry, response.getJSONObject("result"));
	}

	@Test
	public void testTransactionStatusPositional() {
		var txId = randomAID();

		when(transactionStatusService.getTransactionStatus(any()))
			.thenReturn(PENDING, CONFIRMED, FAILED, TRANSACTION_NOT_FOUND);

		var params = jsonArray().put(txId.toString());
		var request = requestWith(params);

		validateTransactionStatusResponse(PENDING, txId, handler.handleTransactionsGetTransactionStatus(request));
		validateTransactionStatusResponse(CONFIRMED, txId, handler.handleTransactionsGetTransactionStatus(request));
		validateTransactionStatusResponse(FAILED, txId, handler.handleTransactionsGetTransactionStatus(request));
		validateTransactionStatusResponse(TRANSACTION_NOT_FOUND, txId, handler.handleTransactionsGetTransactionStatus(request));
	}

	@Test
	public void testTransactionStatusNamed() {
		var txId = randomAID();

		when(transactionStatusService.getTransactionStatus(any()))
			.thenReturn(PENDING, CONFIRMED, FAILED, TRANSACTION_NOT_FOUND);

		var params = jsonObject().put("txID", txId.toString());
		var request = requestWith(params);

		validateTransactionStatusResponse(PENDING, txId, handler.handleTransactionsGetTransactionStatus(request));
		validateTransactionStatusResponse(CONFIRMED, txId, handler.handleTransactionsGetTransactionStatus(request));
		validateTransactionStatusResponse(FAILED, txId, handler.handleTransactionsGetTransactionStatus(request));
		validateTransactionStatusResponse(TRANSACTION_NOT_FOUND, txId, handler.handleTransactionsGetTransactionStatus(request));
	}

	private AID randomAID() {
		return AID.from(HashUtils.random256().asBytes());
	}

	private void validateTransactionStatusResponse(TransactionStatus status, AID txId, JSONObject response) {
		assertNotNull(response);

		var result = response.getJSONObject("result");
		assertEquals(txId, result.get("txID"));

		if (status == TRANSACTION_NOT_FOUND) {
			assertEquals(status.name(), result.get("failure"));
			assertFalse(result.has("status"));
		} else {
			assertEquals(status.name(), result.get("status"));
			assertFalse(result.has("failure"));
		}
	}

	private void validateHistoryEntry(TxHistoryEntry entry, JSONObject historyEntry) {
		assertEquals(UInt256.ONE, historyEntry.get("fee"));
		assertEquals(DateTimeFormatter.ISO_INSTANT.format(entry.timestamp()), historyEntry.getString("sentAt"));
		assertEquals(entry.getTxId(), historyEntry.get("txID"));

		assertTrue(historyEntry.has("actions"));
		var actions = historyEntry.getJSONArray("actions");
		assertEquals(1, actions.length());

		var singleAction = actions.getJSONObject(0);
		assertEquals("Other", singleAction.getString("type"));
	}

	private TxHistoryEntry createTxHistoryEntry(AID txId) {
		var now = Instant.ofEpochMilli(Instant.now().toEpochMilli());
		var action = ActionEntry.unknown();
		return TxHistoryEntry.create(txId, now, UInt256.ONE, "text", List.of(action));
	}

	private JSONObject requestWith(Object params) {
		return jsonObject().put("id", "1").putOpt("params", params);
	}
}
