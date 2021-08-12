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

import com.google.inject.Inject;
import com.radixdlt.api.data.PreparedTransaction;
import com.radixdlt.api.service.ActionParserService;
import com.radixdlt.api.service.SubmissionService;
import com.radixdlt.atom.Txn;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyUtils;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.utils.functional.Result;
import com.radixdlt.utils.functional.Result.Mapper2;

import java.util.List;
import java.util.Optional;

import static org.bouncycastle.util.encoders.Hex.toHexString;

import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.JsonRpcUtil.optString;
import static com.radixdlt.api.JsonRpcUtil.safeArray;
import static com.radixdlt.api.JsonRpcUtil.safeBlob;
import static com.radixdlt.api.JsonRpcUtil.safeString;
import static com.radixdlt.api.JsonRpcUtil.withRequiredParameters;
import static com.radixdlt.api.ApiErrors.INVALID_SIGNATURE_DER;
import static com.radixdlt.api.ApiErrors.INVALID_TX_ID;
import static com.radixdlt.identifiers.CommonErrors.INVALID_PUBLIC_KEY;
import static com.radixdlt.utils.functional.Result.allOf;
import static com.radixdlt.utils.functional.Result.fromOptional;
import static com.radixdlt.utils.functional.Result.wrap;
import static com.radixdlt.utils.functional.Tuple.tuple;

public class ConstructionHandler {
	private final SubmissionService submissionService;
	private final ActionParserService actionParserService;
	private final Addressing addressing;

	@Inject
	public ConstructionHandler(
		SubmissionService submissionService,
		ActionParserService actionParserService,
		Addressing addressing
	) {
		this.submissionService = submissionService;
		this.actionParserService = actionParserService;
		this.addressing = addressing;
	}

	public JSONObject handleConstructionBuildTransaction(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("actions", "feePayer"),
			List.of("message", "disableResourceAllocationAndDestroy"),
			params ->
				allOf(safeArray(params, "actions"), account(params))
					.flatMap((actions, feePayer) -> actionParserService.parse(actions)
						.flatMap(steps -> submissionService.prepareTransaction(
							feePayer,
							steps,
							optString(params, "message"),
							params.optBoolean("disableResourceAllocationAndDestroy")
						))
						.map(PreparedTransaction::asJson))
		);
	}

	public JSONObject handleConstructionFinalizeTransaction(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("blob", "signatureDER", "publicKeyOfSigner"),
			List.of("immediateSubmit"),
			params ->
				allOf(parseBlob(params), parseSignatureDer(params), parsePublicKey(params))
					.flatMap((blob, signature, publicKey) -> toRecoverable(blob, signature, publicKey)
						.flatMap(recoverable -> submissionService.finalizeTxn(
							blob, recoverable, params.optBoolean("immediateSubmit")
						)))
					.map(ConstructionHandler::formatTx)
		);
	}

	public JSONObject handleConstructionSubmitTransaction(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("blob"),
			List.of("txID"),
			params ->
				safeBlob(params, "blob")
					.flatMap(blob -> parseTxId(blob, params)
						.flatMap(submissionService::submitTx))
					.map(Txn::getId)
					.map(ConstructionHandler::formatTxId)
		);
	}

	private static Result<ECDSASignature> toRecoverable(byte[] blob, ECDSASignature signature, ECPublicKey publicKey) {
		return ECKeyUtils.toRecoverable(signature, HashUtils.sha256(blob).asBytes(), publicKey);
	}

	private static Result<byte[]> parseBlob(JSONObject params) {
		return safeBlob(params, "blob");
	}

	private static Result<ECDSASignature> parseSignatureDer(JSONObject params) {
		return safeBlob(params, "signatureDER")
			.flatMap(param -> wrap(INVALID_SIGNATURE_DER, () -> ECDSASignature.decodeFromDER(param)));
	}

	private static Result<ECPublicKey> parsePublicKey(JSONObject params) {
		return safeBlob(params, "publicKeyOfSigner")
			.flatMap(param -> wrap(INVALID_PUBLIC_KEY, () -> ECPublicKey.fromBytes(param)));
	}

	private static Mapper2<byte[], Optional<AID>> parseTxId(byte[] blob, JSONObject params) {
		var optTxId = optString(params, "txID");

		if (optTxId.isEmpty()) {
			return () -> Result.ok(tuple(blob, Optional.empty()));
		}

		return () -> fromOptional(INVALID_TX_ID.with("txID"), optTxId)
			.flatMap(AID::fromString)
			.map(aid -> tuple(blob, Optional.of(aid)));
	}

	private Result<REAddr> account(JSONObject params) {
		return safeString(params, "feePayer")
			.flatMap(addressing.forAccounts()::parseFunctional);
	}

	private static JSONObject formatTxId(AID txId) {
		return jsonObject().put("txID", txId);
	}

	private static JSONObject formatTx(Txn txn) {
		return jsonObject()
			.put("txID", txn.getId())
			.put("blob", toHexString(txn.getPayload()));
	}
}
