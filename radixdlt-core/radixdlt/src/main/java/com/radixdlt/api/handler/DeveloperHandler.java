/* Copyright 2021 Radix DLT Ltd incorporated in England.
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

import com.google.inject.Inject;
import com.radixdlt.api.data.action.TransactionAction;
import com.radixdlt.api.service.ActionParserService;
import com.radixdlt.atom.Txn;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.identifiers.AccountAddressing;
import com.radixdlt.identifiers.NodeAddressing;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.identifiers.ValidatorAddressing;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.networks.Addressing;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.statecomputer.checkpoint.GenesisBuilder;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Pair;
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

	private static Pair<String, ECPublicKey> parseAddress(String type, String address) throws DeserializeException {
		switch (type) {
			case "account":
				return AccountAddressing.parseUnknownHrp(address).mapSecond(addr -> addr.publicKey().orElseThrow());
			case "node":
				return NodeAddressing.parseUnknownHrp(address);
			case "validator":
				return ValidatorAddressing.parseUnknownHrp(address);
			default:
				throw new IllegalArgumentException("type must be: [account|node|validator]");
		}
	}

	public JSONObject handleParseAddress(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("address", "type"),
			params -> Result.wrap(
				e -> Failure.failure(-1, e.getMessage()),
				() -> {
					var type = params.getString("type");
					var address = params.getString("address");
					var pair = parseAddress(type, address);
					var hrp = pair.getFirst();
					var pubKey = pair.getSecond();
					return jsonObject()
						.put("hrp", hrp)
						.put("public_key", Bytes.toHexString(pubKey.getCompressedBytes()));
				}
			)
		);
	}

	private static String createAddress(String type, String hrp, ECPublicKey key) {
		switch (type) {
			case "account":
				return AccountAddressing.bech32(hrp).of(REAddr.ofPubKeyAccount(key));
			case "node":
				return NodeAddressing.bech32(hrp).of(key);
			case "validator":
				return ValidatorAddressing.bech32(hrp).of(key);
			default:
				throw new IllegalArgumentException("type must be: [account|node|validator]");
		}
	}

	public JSONObject handleCreateAddress(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("public_key", "hrp", "type"),
			params -> Result.wrap(
				e -> Failure.failure(-1, e.getMessage()),
				() -> {
					var type = params.getString("type");
					var publicKeyHex = params.getString("public_key");
					var publicKey = ECPublicKey.fromHex(publicKeyHex);
					var hrp = params.getString("hrp");
					var address = createAddress(type, hrp, publicKey);
					return jsonObject()
						.put("address", address);
				}
			)
		);
	}
}
