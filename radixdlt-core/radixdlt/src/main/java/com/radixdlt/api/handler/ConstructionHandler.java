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
import com.radixdlt.api.data.PreparedTransaction;
import com.radixdlt.api.service.ActionParserService;
import com.radixdlt.api.service.SubmissionService;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyUtils;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.functional.Result;

import java.util.List;

import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.JsonRpcUtil.optString;
import static com.radixdlt.api.JsonRpcUtil.safeArray;
import static com.radixdlt.api.JsonRpcUtil.safeBlob;
import static com.radixdlt.api.JsonRpcUtil.safeObject;
import static com.radixdlt.api.JsonRpcUtil.safeString;
import static com.radixdlt.api.JsonRpcUtil.withRequiredParameters;
import static com.radixdlt.api.data.ApiErrors.INVALID_PUBLIC_KEY;
import static com.radixdlt.api.data.ApiErrors.INVALID_SIGNATURE_DER;
import static com.radixdlt.utils.functional.Result.allOf;
import static com.radixdlt.utils.functional.Result.wrap;

public class ConstructionHandler {
	private final SubmissionService submissionService;
	private final ActionParserService actionParserService;

	@Inject
	public ConstructionHandler(SubmissionService submissionService, ActionParserService actionParserService) {
		this.submissionService = submissionService;
		this.actionParserService = actionParserService;
	}

	public JSONObject handleBuildTransaction(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("actions"),
			List.of("message"),
			params ->
				safeArray(params, "actions")
					.flatMap(actions -> actionParserService.parse(actions)
						.flatMap(steps -> submissionService.prepareTransaction(steps, optString(params, "message")))
						.map(PreparedTransaction::asJson)
					)
		);
	}

	public JSONObject handleFinalizeTransaction(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("transaction", "signatureDER", "publicKeyOfSigner"),
			List.of(),
			params ->
				allOf(parseBlob(params), parseSignatureDer(params), parsePublicKey(params))
					.flatMap((blob, signature, publicKey) -> toRecoverable(blob, signature, publicKey)
						.flatMap(recoverable -> submissionService.calculateTxId(blob, recoverable)))
					.map(ConstructionHandler::formatTxId)
		);
	}

	public JSONObject handleSubmitTransaction(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("transaction", "signatureDER", "publicKeyOfSigner", "txID"),
			List.of(),
			params ->
				allOf(parseBlob(params), parseSignatureDer(params), parsePublicKey(params), parseTxId(params))
					.flatMap((blob, signature, publicKey, txId) -> toRecoverable(blob, signature, publicKey)
						.flatMap(recoverable -> submissionService.submitTx(blob, recoverable, txId)))
					.map(ConstructionHandler::formatTxId)
		);
	}

	private static Result<ECDSASignature> toRecoverable(byte[] blob, ECDSASignature signature, ECPublicKey publicKey) {
		return ECKeyUtils.toRecoverable(signature, HashUtils.sha256(blob).asBytes(), publicKey);
	}

	private static Result<byte[]> parseBlob(JSONObject params) {
		return safeObject(params, "transaction")
			.flatMap(txObj -> safeBlob(txObj, "blob"));
	}

	private static Result<ECDSASignature> parseSignatureDer(JSONObject params) {
		return safeBlob(params, "signatureDER")
			.flatMap(param -> wrap(INVALID_SIGNATURE_DER, () -> ECDSASignature.decodeFromDER(param)));
	}

	private static Result<ECPublicKey> parsePublicKey(JSONObject params) {
		return safeBlob(params, "publicKeyOfSigner")
			.flatMap(param -> wrap(INVALID_PUBLIC_KEY, () -> ECPublicKey.fromBytes(param)));
	}

	private static Result<AID> parseTxId(JSONObject params) {
		return safeString(params, "txID").flatMap(AID::fromString);
	}

	private static JSONObject formatTxId(AID txId) {
		return jsonObject().put("txID", txId);
	}
}
