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

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OutputStream;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONObject;
import org.junit.Test;

import com.radixdlt.api.service.ActionParserService;
import com.radixdlt.api.service.SubmissionService;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.AccountAddress;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.functional.Result;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.api.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;

public class ConstructionHandlerTest {
	private static final ECPublicKey PUB_KEY = ECKeyPair.generateNew().getPublicKey();
	private static final REAddr ACCOUNT_ADDR = REAddr.ofPubKeyAccount(PUB_KEY);
	private static final String ADDRESS = AccountAddress.of(ACCOUNT_ADDR);
	private static final ECPublicKey V1 = ECKeyPair.generateNew().getPublicKey();
	private static final ECPublicKey V2 = ECKeyPair.generateNew().getPublicKey();
	private static final ECPublicKey V3 = ECKeyPair.generateNew().getPublicKey();

	private final SubmissionService submissionService = mock(SubmissionService.class);
	private final ActionParserService actionParserService = mock(ActionParserService.class);
	private final ConstructionHandler handler = new ConstructionHandler(submissionService, actionParserService);

	//TODO: testBuildTransaction, possible issues with positional vs named parameters
	@Test
	public void testFinalizeTransactionPositional() {
		var aid = AID.from(randomBytes());

		when(submissionService.calculateTxId(any(), any()))
			.thenReturn(Result.ok(aid));

		var blob = randomBytes();
		var hash = HashUtils.sha256(blob).asBytes();

		var transaction = jsonObject()
			.put("blob", Hex.toHexString(blob))
			.put("hashOfBlobToSign", Hex.toHexString(hash));

		var keyPair = ECKeyPair.generateNew();

		var signature = keyPair.sign(hash);
		var params = jsonArray()
			.put(transaction)
			.put(encodeToDer(signature))
			.put(keyPair.getPublicKey().toHex());

		var response = handler.handleFinalizeTransaction(requestWith(params));

		assertNotNull(response);
		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");

		assertTrue(result.has("txID"));

		assertEquals(aid, result.get("txID"));
	}

	@Test
	public void testFinalizeTransactionNamed() {
		var aid = AID.from(randomBytes());

		when(submissionService.calculateTxId(any(), any()))
			.thenReturn(Result.ok(aid));

		var blob = randomBytes();
		var hash = HashUtils.sha256(blob).asBytes();

		var transaction = jsonObject()
			.put("blob", Hex.toHexString(blob))
			.put("hashOfBlobToSign", Hex.toHexString(hash));

		var keyPair = ECKeyPair.generateNew();

		var signature = keyPair.sign(hash);
		var params = jsonObject()
			.put("transaction", transaction)
			.put("signatureDER", encodeToDer(signature))
			.put("publicKeyOfSigner", keyPair.getPublicKey().toHex());

		var response = handler.handleFinalizeTransaction(requestWith(params));

		assertNotNull(response);
		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");

		assertTrue(result.has("txID"));

		assertEquals(aid, result.get("txID"));
	}

	@Test
	public void testSubmitTransactionPositional() {
		var aid = AID.from(randomBytes());

		when(submissionService.submitTx(any(), any(), any()))
			.thenReturn(Result.ok(aid));

		var blob = randomBytes();
		var hash = HashUtils.sha256(blob).asBytes();

		var transaction = jsonObject()
			.put("blob", Hex.toHexString(blob))
			.put("hashOfBlobToSign", Hex.toHexString(hash));

		var keyPair = ECKeyPair.generateNew();

		var signature = keyPair.sign(hash);
		var params = jsonArray()
			.put(transaction)
			.put(encodeToDer(signature))
			.put(keyPair.getPublicKey().toHex())
			.put(aid.toString());

		var response = handler.handleSubmitTransaction(requestWith(params));

		assertNotNull(response);
		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");

		assertTrue(result.has("txID"));

		assertEquals(aid, result.get("txID"));
	}

	@Test
	public void testSubmitTransactionNamed() {
		var aid = AID.from(randomBytes());

		when(submissionService.submitTx(any(), any(), any()))
			.thenReturn(Result.ok(aid));

		var blob = randomBytes();
		var hash = HashUtils.sha256(blob).asBytes();

		var transaction = jsonObject()
			.put("blob", Hex.toHexString(blob))
			.put("hashOfBlobToSign", Hex.toHexString(hash));

		var keyPair = ECKeyPair.generateNew();

		var signature = keyPair.sign(hash);
		var params = jsonObject()
			.put("transaction", transaction)
			.put("signatureDER", encodeToDer(signature))
			.put("publicKeyOfSigner", keyPair.getPublicKey().toHex())
			.put("txID", aid.toString());

		var response = handler.handleSubmitTransaction(requestWith(params));

		assertNotNull(response);
		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");

		assertTrue(result.has("txID"));

		assertEquals(aid, result.get("txID"));
	}

	private String encodeToDer(ECDSASignature signature) {
		try {
			ASN1EncodableVector vector = new ASN1EncodableVector();
			vector.add(new ASN1Integer(signature.getR()));
			vector.add(new ASN1Integer(signature.getS()));

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ASN1OutputStream asnOS = ASN1OutputStream.create(baos);

			asnOS.writeObject(new DERSequence(vector));
			asnOS.flush();

			return Hex.toHexString(baos.toByteArray());
		} catch (Exception e) {
			fail();
			return null;
		}
	}

	private byte[] randomBytes() {
		return HashUtils.random256().asBytes();
	}

	private JSONObject requestWith(Object params) {
		return jsonObject().put("id", "1").putOpt("params", params);
	}
}