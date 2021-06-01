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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.api.data.TxHistoryEntry;
import com.radixdlt.api.service.TransactionStatusService;
import com.radixdlt.identifiers.AID;

import static com.radixdlt.api.JsonRpcUtil.withRequiredStringParameter;

@Singleton
public class ArchiveTransactionsHandler {
	private final TransactionStatusService transactionStatusService;

	@Inject
	public ArchiveTransactionsHandler(TransactionStatusService transactionStatusService) {
		this.transactionStatusService = transactionStatusService;
	}

	public JSONObject handleTransactionsGetTransactionStatus(JSONObject request) {
		return withRequiredStringParameter(
			request,
			"txID",
			(idString) -> AID.fromString(idString)
				.map(txId -> transactionStatusService.getTransactionStatus(txId).asJson().put("txID", txId))
		);
	}

	public JSONObject handleTransactionsLookupTransaction(JSONObject request) {
		return withRequiredStringParameter(
			request,
			"txID",
			(idString) -> AID.fromString(idString)
				.flatMap(txId -> transactionStatusService.getTransaction(txId).map(TxHistoryEntry::asJson))
		);
	}
}
