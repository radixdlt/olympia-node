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

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.networks.Addressing;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import com.google.inject.Inject;
import com.radixdlt.api.data.action.TransactionAction;
import com.radixdlt.api.faucet.FaucetToken;
import com.radixdlt.api.service.SubmissionService;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.qualifier.LocalSigner;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.util.ArrayList;
import java.util.Set;

import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.JsonRpcUtil.withRequiredStringParameter;

import static java.util.Optional.empty;

public class FaucetHandler {
	private static final Logger logger = LogManager.getLogger();
	private static final UInt256 AMOUNT = Amount.ofTokens(10).toSubunits();

	private final SubmissionService submissionService;
	private final REAddr account;
	private final Set<REAddr> tokensToSend;
	private final HashSigner hashSigner;
	private final Addressing addressing;

	@Inject
	public FaucetHandler(
		SubmissionService submissionService,
		@Self REAddr account,
		@FaucetToken Set<REAddr> tokensToSend,
		@LocalSigner HashSigner hashSigner,
		Addressing addressing
	) {
		this.submissionService = submissionService;
		this.account = account;
		this.tokensToSend = tokensToSend;
		this.hashSigner = hashSigner;
		this.addressing = addressing;
	}

	public JSONObject requestTokens(JSONObject request) {
		return withRequiredStringParameter(
			request, "address",
			address -> addressing.forAccounts().parseFunctional(address).flatMap(this::sendTokens)
		);
	}

	private Result<JSONObject> sendTokens(REAddr destination) {
		logger.info("Sending {} {} to {}", AMOUNT, tokensToSend, addressing.forAccounts().of(destination));

		var steps = new ArrayList<TransactionAction>();

		tokensToSend.forEach(rri -> steps.add(transfer(destination, rri)));

		return submissionService.oneStepSubmit(account, steps, empty(), hashSigner, false)
			.map(FaucetHandler::formatTxId);
	}

	private TransactionAction transfer(REAddr destination, REAddr rri) {
		return TransactionAction.transfer(account, destination, AMOUNT, rri);
	}

	private static JSONObject formatTxId(AID txId) {
		return jsonObject().put("txID", txId);
	}
}
