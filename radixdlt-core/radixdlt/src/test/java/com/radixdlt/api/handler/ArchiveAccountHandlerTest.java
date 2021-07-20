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

import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import org.json.JSONObject;
import org.junit.Test;

import com.radixdlt.api.data.ActionEntry;
import com.radixdlt.api.data.TxHistoryEntry;
import com.radixdlt.api.service.ArchiveAccountService;
import com.radixdlt.api.store.TokenBalance;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import com.radixdlt.utils.functional.Result;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.api.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.data.BalanceEntry.createBalance;
import static com.radixdlt.utils.functional.Tuple.tuple;

public class ArchiveAccountHandlerTest {
	private static final ECPublicKey PUB_KEY = ECKeyPair.generateNew().getPublicKey();
	private static final REAddr ACCOUNT_ADDRESS = REAddr.ofPubKeyAccount(PUB_KEY);
	private static final Addressing addressing = Addressing.ofNetwork(Network.LOCALNET);
	private static final String ADDRESS = addressing.forAccounts().of(ACCOUNT_ADDRESS);
	private static final ECPublicKey V1 = ECKeyPair.generateNew().getPublicKey();
	private static final ECPublicKey V2 = ECKeyPair.generateNew().getPublicKey();
	private static final ECPublicKey V3 = ECKeyPair.generateNew().getPublicKey();

	private final ArchiveAccountService accountService = mock(ArchiveAccountService.class);
	private final ArchiveAccountHandler handler = new ArchiveAccountHandler(accountService, addressing);

	@Test
	public void testTokenBalancePositional() {
		var address1 = REAddr.ofHashedKey(PUB_KEY, "xyz");
		var address2 = REAddr.ofHashedKey(PUB_KEY, "yzs");
		var address3 = REAddr.ofHashedKey(PUB_KEY, "zxy");
		var balance1 = TokenBalance.create(addressing.forResources().of("xyz", address1), UInt384.TWO);
		var balance2 = TokenBalance.create(addressing.forResources().of("yzs", address2), UInt384.FIVE);
		var balance3 = TokenBalance.create(addressing.forResources().of("zxy", address3), UInt384.EIGHT);

		when(accountService.getTokenBalances(any(REAddr.class)))
			.thenReturn(Result.ok(List.of(balance1, balance2, balance3)));

		var response = handler.handleAccountGetBalances(requestWith(jsonArray().put(ADDRESS)));

		assertNotNull(response);

		var result = response.getJSONObject("result");
		assertEquals(ADDRESS, result.getString("owner"));

		var list = result.getJSONArray("tokenBalances");

		assertEquals(3, list.length());
		assertEquals(UInt384.TWO, list.getJSONObject(0).get("amount"));
		assertEquals(UInt384.FIVE, list.getJSONObject(1).get("amount"));
		assertEquals(UInt384.EIGHT, list.getJSONObject(2).get("amount"));
	}

	@Test
	public void testTokenBalanceNamed() {
		var address1 = REAddr.ofHashedKey(PUB_KEY, "xyz");
		var address2 = REAddr.ofHashedKey(PUB_KEY, "yzs");
		var address3 = REAddr.ofHashedKey(PUB_KEY, "zxy");
		var balance1 = TokenBalance.create(addressing.forResources().of("xyz", address1), UInt384.TWO);
		var balance2 = TokenBalance.create(addressing.forResources().of("yzs", address2), UInt384.FIVE);
		var balance3 = TokenBalance.create(addressing.forResources().of("zxy", address3), UInt384.EIGHT);

		when(accountService.getTokenBalances(any(REAddr.class)))
			.thenReturn(Result.ok(List.of(balance1, balance2, balance3)));

		var response = handler.handleAccountGetBalances(requestWith(jsonObject().put("address", ADDRESS)));

		assertNotNull(response);

		var result = response.getJSONObject("result");
		assertEquals(ADDRESS, result.getString("owner"));

		var list = result.getJSONArray("tokenBalances");

		assertEquals(3, list.length());
		assertEquals(UInt384.TWO, list.getJSONObject(0).get("amount"));
		assertEquals(UInt384.FIVE, list.getJSONObject(1).get("amount"));
		assertEquals(UInt384.EIGHT, list.getJSONObject(2).get("amount"));
	}

	@Test
	public void testStakePositionsPositional() {
		var balance1 = createBalance(ACCOUNT_ADDRESS, V1, "xrd", UInt384.TWO);
		var balance2 = createBalance(ACCOUNT_ADDRESS, V2, "xrd", UInt384.FIVE);
		var balance3 = createBalance(ACCOUNT_ADDRESS, V3, "xrd", UInt384.EIGHT);

		when(accountService.getStakePositions(any(REAddr.class)))
			.thenReturn(Result.ok(List.of(balance1, balance2, balance3)));

		var response = handler.handleAccountGetStakePositions(requestWith(jsonArray().put(ADDRESS)));

		assertNotNull(response);

		var list = response.getJSONArray("result");

		assertEquals(3, list.length());
		assertEquals(UInt384.TWO, list.getJSONObject(0).get("amount"));
		assertEquals(addressing.forValidators().of(balance1.getDelegate()), list.getJSONObject(0).get("validator"));

		assertEquals(UInt384.FIVE, list.getJSONObject(1).get("amount"));
		assertEquals(addressing.forValidators().of(balance2.getDelegate()), list.getJSONObject(1).get("validator"));

		assertEquals(UInt384.EIGHT, list.getJSONObject(2).get("amount"));
		assertEquals(addressing.forValidators().of(balance3.getDelegate()), list.getJSONObject(2).get("validator"));
	}

	@Test
	public void testStakePositionsNamed() {
		var balance1 = createBalance(ACCOUNT_ADDRESS, V1, "xrd", UInt384.TWO);
		var balance2 = createBalance(ACCOUNT_ADDRESS, V2, "xrd", UInt384.FIVE);
		var balance3 = createBalance(ACCOUNT_ADDRESS, V3, "xrd", UInt384.EIGHT);

		when(accountService.getStakePositions(any(REAddr.class)))
			.thenReturn(Result.ok(List.of(balance1, balance2, balance3)));

		var response = handler.handleAccountGetStakePositions(requestWith(jsonObject().put("address", ADDRESS)));

		assertNotNull(response);

		var list = response.getJSONArray("result");

		assertEquals(3, list.length());
		assertEquals(UInt384.TWO, list.getJSONObject(0).get("amount"));
		assertEquals(addressing.forValidators().of(balance1.getDelegate()), list.getJSONObject(0).get("validator"));

		assertEquals(UInt384.FIVE, list.getJSONObject(1).get("amount"));
		assertEquals(addressing.forValidators().of(balance2.getDelegate()), list.getJSONObject(1).get("validator"));

		assertEquals(UInt384.EIGHT, list.getJSONObject(2).get("amount"));
		assertEquals(addressing.forValidators().of(balance3.getDelegate()), list.getJSONObject(2).get("validator"));
	}

	@Test
	public void testTransactionHistoryPositional() {
		var entry = createTxHistoryEntry();

		when(accountService.getTransactionHistory(any(), eq(5), any(), eq(false)))
			.thenReturn(Result.ok(tuple(Optional.ofNullable(entry.timestamp()), List.of(entry))));

		var params = jsonArray().put(ADDRESS).put(5);
		var response = handler.handleAccountGetTransactionHistory(requestWith(params));

		assertNotNull(response);

		var result = response.getJSONObject("result");

		assertTrue(result.has("cursor"));
		assertTrue(result.has("transactions"));
		var transactions = result.getJSONArray("transactions");
		assertEquals(1, transactions.length());

		validateHistoryEntry(entry, transactions.getJSONObject(0));
	}

	@Test
	public void testTransactionHistoryNamed() {
		var entry = createTxHistoryEntry();

		when(accountService.getTransactionHistory(any(), eq(5), any(), eq(false)))
			.thenReturn(Result.ok(tuple(Optional.ofNullable(entry.timestamp()), List.of(entry))));

		var params = jsonObject().put("address", ADDRESS).put("size", "5");
		var response = handler.handleAccountGetTransactionHistory(requestWith(params));

		assertNotNull(response);

		var result = response.getJSONObject("result");

		assertTrue(result.has("cursor"));
		assertTrue(result.has("transactions"));
		var transactions = result.getJSONArray("transactions");
		assertEquals(1, transactions.length());

		validateHistoryEntry(entry, transactions.getJSONObject(0));
	}

	@Test
	public void testTransactionHistoryVerbosePositional() {
		var entry = createTxHistoryEntry();

		when(accountService.getTransactionHistory(any(), eq(5), any(), eq(true)))
			.thenReturn(Result.ok(tuple(Optional.ofNullable(entry.timestamp()), List.of(entry))));

		var params = jsonArray().put(ADDRESS).put(5).put("0:0").put("true");
		var response = handler.handleAccountGetTransactionHistory(requestWith(params));

		assertNotNull(response);

		var result = response.getJSONObject("result");

		assertTrue(result.has("cursor"));
		assertTrue(result.has("transactions"));
		var transactions = result.getJSONArray("transactions");
		assertEquals(1, transactions.length());

		validateHistoryEntry(entry, transactions.getJSONObject(0));
	}

	@Test
	public void testTransactionHistoryVerboseNamed() {
		var entry = createTxHistoryEntry();

		when(accountService.getTransactionHistory(any(), eq(5), any(), eq(true)))
			.thenReturn(Result.ok(tuple(Optional.ofNullable(entry.timestamp()), List.of(entry))));

		var params = jsonObject().put("address", ADDRESS).put("size", "5").put("verbose", true);
		var response = handler.handleAccountGetTransactionHistory(requestWith(params));

		assertNotNull(response);

		var result = response.getJSONObject("result");

		assertTrue(result.has("cursor"));
		assertTrue(result.has("transactions"));
		var transactions = result.getJSONArray("transactions");
		assertEquals(1, transactions.length());

		validateHistoryEntry(entry, transactions.getJSONObject(0));
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

	private TxHistoryEntry createTxHistoryEntry() {
		var now = Instant.ofEpochMilli(Instant.now().toEpochMilli());
		var action = ActionEntry.unknown();
		return TxHistoryEntry.create(AID.ZERO, now, UInt256.ONE, "text", List.of(action));
	}

	private JSONObject requestWith(Object params) {
		return jsonObject().put("id", "1").putOpt("params", params);
	}
}
