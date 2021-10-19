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

import com.radixdlt.api.archive.construction.ConstructionHandler;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.RERules;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OutputStream;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.radixdlt.api.data.PreparedTransaction;
import com.radixdlt.api.util.ActionParser;
import com.radixdlt.api.service.SubmissionService;
import com.radixdlt.atom.Txn;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.api.util.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.util.JsonRpcUtil.jsonObject;

public class ConstructionHandlerTest {
	private static final ECPublicKey PUB_KEY = ECKeyPair.generateNew().getPublicKey();
	private static final REAddr ACCOUNT_ADDR = REAddr.ofPubKeyAccount(PUB_KEY);
	private static final Addressing addressing = Addressing.ofNetwork(Network.LOCALNET);
	private static final String FEE_PAYER = addressing.forAccounts().of(ACCOUNT_ADDR);

	private final SubmissionService submissionService = mock(SubmissionService.class);
	private final Forks forks = mock(Forks.class);
	private final ActionParser actionParser = new ActionParser(addressing, forks);
	private final ConstructionHandler handler = new ConstructionHandler(submissionService, actionParser, addressing);

	@Before
	public void setup() {
		final var reRules = mock(RERules.class);
		when(reRules.getMaxRounds()).thenReturn(View.of(10L));
		when(forks.getCandidateFork()).thenReturn(Optional.empty());
	}

	@Test
	public void testBuildTransactionPositional() {
		var prepared = PreparedTransaction.create(randomBytes(), randomBytes(), UInt256.TEN, List.of());

		when(submissionService.prepareTransaction(any(), any(), any(), eq(false)))
			.thenReturn(Result.ok(prepared));

		var actions = jsonArray()
			.put(
				jsonObject()
					.put("type", "RegisterValidator")
					.put("validator", addressing.forValidators().of(PUB_KEY))
			);
		var params = jsonArray()
			.put(actions)
			.put(FEE_PAYER)
			.put("message");

		var response = handler.handleConstructionBuildTransaction(requestWith(params));

		assertNotNull(response);
		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");

		assertNotNull(result);
		assertEquals("10", result.get("fee"));
	}

	@Test
	public void testBuildTransactionNamed() {
		var prepared = PreparedTransaction.create(randomBytes(), randomBytes(), UInt256.TEN, List.of());

		when(submissionService.prepareTransaction(any(), any(), any(), eq(false)))
			.thenReturn(Result.ok(prepared));

		var actions = jsonArray()
			.put(
				jsonObject()
					.put("type", "RegisterValidator")
					.put("validator", addressing.forValidators().of(PUB_KEY))
			);
		var params = jsonObject()
			.put("actions", actions)
			.put("feePayer", FEE_PAYER)
			.put("message", "message");

		var response = handler.handleConstructionBuildTransaction(requestWith(params));

		assertNotNull(response);
		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");

		assertNotNull(result);
		assertEquals("10", result.get("fee"));
	}

	@Test
	public void testFinalizeTransactionPositional() {
		var txn = Txn.create(randomBytes());

		when(submissionService.finalizeTxn(any(), any(), anyBoolean()))
			.thenReturn(Result.ok(txn));

		var blob = randomBytes();
		var hash = HashUtils.sha256(blob).asBytes();
		var keyPair = ECKeyPair.generateNew();
		var signature = keyPair.sign(hash);
		var params = jsonArray()
			.put(Hex.toHexString(blob))
			.put(encodeToDer(signature))
			.put(keyPair.getPublicKey().toHex());

		var response = handler.handleConstructionFinalizeTransaction(requestWith(params));

		assertNotNull(response);
		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");

		assertTrue(result.has("txID"));
		assertTrue(result.has("blob"));

		assertEquals(txn.getId(), result.get("txID"));
		assertEquals(Hex.toHexString(txn.getPayload()), result.get("blob"));
	}

	@Test
	public void testFinalizeTransactionNamed() {
		var txn = Txn.create(randomBytes());

		when(submissionService.finalizeTxn(any(), any(), anyBoolean()))
			.thenReturn(Result.ok(txn));

		var blob = randomBytes();
		var hash = HashUtils.sha256(blob).asBytes();
		var keyPair = ECKeyPair.generateNew();
		var signature = keyPair.sign(hash);
		var params = jsonObject()
			.put("blob", Hex.toHexString(blob))
			.put("signatureDER", encodeToDer(signature))
			.put("publicKeyOfSigner", keyPair.getPublicKey().toHex());

		var response = handler.handleConstructionFinalizeTransaction(requestWith(params));

		assertNotNull(response);
		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");

		assertTrue(result.has("txID"));
		assertTrue(result.has("blob"));

		assertEquals(txn.getId(), result.get("txID"));
		assertEquals(Hex.toHexString(txn.getPayload()), result.get("blob"));
	}

	@Test
	public void testSubmitTransactionPositional() {
		var blob = randomBytes();
		var txn = Txn.create(blob);

		when(submissionService.submitTx(any(), any()))
			.thenReturn(Result.ok(txn));

		var params = jsonArray()
			.put(Hex.toHexString(blob))
			.put(txn.getId().toString());

		var response = handler.handleConstructionSubmitTransaction(requestWith(params));

		assertNotNull(response);
		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");

		assertTrue(result.has("txID"));
		assertEquals(txn.getId(), result.get("txID"));
	}

	@Test
	public void testSubmitTransactionNamed() {
		var blob = randomBytes();
		var txn = Txn.create(blob);

		when(submissionService.submitTx(any(), any()))
			.thenReturn(Result.ok(txn));

		var params = jsonObject()
			.put("blob", Hex.toHexString(blob))
			.put("txID", txn.getId().toJson());

		var response = handler.handleConstructionSubmitTransaction(requestWith(params));

		assertNotNull(response);
		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");

		assertTrue(result.has("txID"));

		assertEquals(txn.getId(), result.get("txID"));
	}

	private String encodeToDer(ECDSASignature signature) {
		try {
			var vector = new ASN1EncodableVector();
			vector.add(new ASN1Integer(signature.getR()));
			vector.add(new ASN1Integer(signature.getS()));

			var baos = new ByteArrayOutputStream();
			var asnOS = ASN1OutputStream.create(baos);
			asnOS.writeObject(new DERSequence(vector));
			asnOS.flush();

			return Hex.toHexString(baos.toByteArray());
		} catch (Exception e) {
			fail(e.getMessage());
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
