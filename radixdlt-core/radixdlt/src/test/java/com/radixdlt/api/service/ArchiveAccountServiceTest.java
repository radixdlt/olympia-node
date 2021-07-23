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
package com.radixdlt.api.service;

import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.radixdlt.api.data.ActionEntry;
import com.radixdlt.api.data.TxHistoryEntry;
import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.api.store.ClientApiStore.BalanceType;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import com.radixdlt.utils.functional.Result;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.api.data.BalanceEntry.createBalance;

public class ArchiveAccountServiceTest {
	private static final ECPublicKey OWNER_KEY = ECKeyPair.generateNew().getPublicKey();
	private static final REAddr OWNER_ACCOUNT = REAddr.ofPubKeyAccount(OWNER_KEY);
	private static final ECPublicKey TOKEN_KEY = ECKeyPair.generateNew().getPublicKey();

	private final Addressing addressing = Addressing.ofNetwork(Network.LOCALNET);
	private final ClientApiStore clientApiStore = mock(ClientApiStore.class);
	private final ArchiveAccountService archiveService = new ArchiveAccountService(clientApiStore);

	@Test
	public void testGetTokenBalancesForFunds() {
		var address = TOKEN_KEY;
		var address1 = REAddr.ofHashedKey(address, "fff");
		var rri1 = addressing.forResources().of("fff", address1);
		var address2 = REAddr.ofHashedKey(address, "rar");
		var rri2 = addressing.forResources().of("rar", address2);
		var balance1 = createBalance(OWNER_ACCOUNT, null, rri1, UInt384.FIVE);
		var balance2 = createBalance(OWNER_ACCOUNT, null, rri2, UInt384.NINE);
		var balances = Result.ok(List.of(balance1, balance2));

		when(clientApiStore.getTokenBalances(OWNER_ACCOUNT, BalanceType.SPENDABLE))
			.thenReturn(balances);

		archiveService.getTokenBalances(OWNER_ACCOUNT)
			.onSuccess(list -> {
				assertEquals(2, list.size());
				assertEquals(UInt384.FIVE, list.get(0).getAmount());
				assertEquals(UInt384.NINE, list.get(1).getAmount());
			})
			.onFailureDo(Assert::fail);
	}

	@Test
	@Ignore
	public void testGetTokenBalancesForStakes() {
		var address = TOKEN_KEY;
		var address1 = REAddr.ofHashedKey(address, "fff");
		var rri1 = addressing.forResources().of("fff", address1);
		var address2 = REAddr.ofHashedKey(address, "rar");
		var rri2 = addressing.forResources().of("rar", address2);
		var balance1 = createBalance(OWNER_ACCOUNT, null, rri1, UInt384.FIVE);
		var balance2 = createBalance(OWNER_ACCOUNT, null, rri2, UInt384.NINE);
		var balance3 = createBalance(OWNER_ACCOUNT, null,
									 addressing.forResources().of("xrd", REAddr.ofNativeToken()), UInt384.TWO
		);
		var balances = Result.ok(List.of(balance1, balance2, balance3));

		when(clientApiStore.getTokenBalances(OWNER_ACCOUNT, BalanceType.STAKES))
			.thenReturn(balances);

		archiveService.getStakePositions(OWNER_ACCOUNT)
			.onSuccess(list -> {
				assertEquals(3, list.size());
				assertEquals(UInt384.FIVE, list.get(0).getAmount());
				assertEquals(UInt384.NINE, list.get(1).getAmount());
				assertEquals(UInt384.TWO, list.get(2).getAmount());
			})
			.onFailureDo(Assert::fail);
	}

	@Test
	public void testGetTransactionHistory() {
		var entry = createTxHistoryEntry();

		when(clientApiStore.getTransactionHistory(eq(OWNER_ACCOUNT), eq(1), eq(Optional.empty()), eq(false)))
			.thenReturn(Result.ok(List.of(entry)));

		archiveService.getTransactionHistory(OWNER_ACCOUNT, 1, Optional.empty(), false)
			.onSuccess(tuple -> tuple.map((cursor, list) -> {
				assertTrue(cursor.isPresent());
				assertEquals(entry.timestamp(), cursor.get());

				assertEquals(1, list.size());
				assertEquals(entry, list.get(0));

				return null;
			}))
			.onFailureDo(Assert::fail);
	}

	private TxHistoryEntry createTxHistoryEntry() {
		var now = Instant.ofEpochMilli(Instant.now().toEpochMilli());
		var action = ActionEntry.unknown();
		return TxHistoryEntry.create(AID.ZERO, now, UInt256.ONE, "text", List.of(action));
	}
}