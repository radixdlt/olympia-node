/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.api.handler;

import com.google.inject.Inject;
import com.radixdlt.api.data.action.TransactionAction;
import com.radixdlt.api.service.ActionParserService;
import com.radixdlt.atom.Txn;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.identifiers.AccountAddressing;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.checkpoint.GenesisBuilder;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;
import org.json.JSONObject;

import java.util.List;
import java.util.stream.Collectors;

import static com.radixdlt.api.JsonRpcUtil.*;
import static com.radixdlt.api.data.ApiErrors.UNABLE_TO_PREPARE_TX;
import static com.radixdlt.utils.functional.Result.allOf;

public final class DeveloperHandler {
	private final Addressing addressing;
	private final ActionParserService actionParserService;
	private final GenesisBuilder genesisBuilder;
	private final REParser parser;

	@Inject
	public DeveloperHandler(
		ActionParserService actionParserService,
		GenesisBuilder genesisBuilder,
		Addressing addressing,
		REParser parser
	) {
		this.actionParserService = actionParserService;
		this.genesisBuilder = genesisBuilder;
		this.addressing = addressing;
		this.parser = parser;
	}

	private Result<VerifiedTxnsAndProof> build(List<TransactionAction> steps) {
		var actions = steps.stream().flatMap(TransactionAction::toAction).collect(Collectors.toList());
		return Result.wrap(
			UNABLE_TO_PREPARE_TX,
			() -> {
				try {
					var txn = genesisBuilder.build(System.currentTimeMillis(), actions);
					var proof = genesisBuilder.generateGenesisProof(txn);
					return VerifiedTxnsAndProof.create(List.of(txn), proof);
				} catch (Exception e) {
					e.printStackTrace();
					throw e;
				}
			}
		);
	}

	public JSONObject handleGenesisConstruction(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("actions"),
			params ->
				allOf(safeArray(params, "actions"))
					.flatMap(actions -> actionParserService.parse(actions).flatMap(this::build)
						.map(p -> {
							var o = jsonObject();
							var txns = jsonArray();
							p.getTxns().forEach(txn -> txns.put(Bytes.toHexString(txn.getPayload())));
							var proof = p.getProof().asJSON(addressing);
							return o.put("txns", txns).put("proof", proof);
						}))
		);
	}


	public JSONObject handleParseTxn(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("txn"),
			params -> Result.wrap(
				e -> Failure.failure(-1, e.getMessage()),
				() -> {
					var txnHex = params.getString("txn");
					var txn = Txn.create(Bytes.fromHexString(txnHex));
					var result = parser.parse(txn);
					var resultJson = jsonObject();
					var instructionsJson = jsonArray();
					resultJson.put("txId", txn.getId().toJson());
					resultJson.put("size", txn.getPayload().length);
					resultJson.put("instructions", instructionsJson);
					result.instructions().forEach(i -> {
						var buf = i.getDataByteBuffer();
						instructionsJson.put(jsonObject()
							.put("op", i.getMicroOp().toString())
							.put("data", i.getData() == null ? null : i.getData().toString())
							.put("data_raw", Bytes.toHexString(buf.array(), buf.position(), i.getDataLength()))
							.put("data_size", i.getDataLength())
						);
					});
					return resultJson;
				}
			)
		);
	}

	public JSONObject handleParseAddress(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("address"),
			params -> Result.wrap(
				e -> Failure.failure(-1, e.getMessage()),
				() -> {
					var address = params.getString("address");
					var p = AccountAddressing.parseUnknownHrp(address);
					var hrp = p.getFirst();
					var pubKey = p.getSecond().publicKey().orElseThrow();
					return jsonObject()
						.put("hrp", hrp)
						.put("public_key", Bytes.toHexString(pubKey.getCompressedBytes()));
				}
			)
		);
	}
}
