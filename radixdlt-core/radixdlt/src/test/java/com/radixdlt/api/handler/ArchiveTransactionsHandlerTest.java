/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */
package com.radixdlt.api.handler;

import org.json.JSONObject;
import org.junit.Test;

import com.radixdlt.api.data.ActionEntry;
import com.radixdlt.api.data.TransactionStatus;
import com.radixdlt.api.data.TxHistoryEntry;
import com.radixdlt.api.transactions.TransactionStatusService;
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
