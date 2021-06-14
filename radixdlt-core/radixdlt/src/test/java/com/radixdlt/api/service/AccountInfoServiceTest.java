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
package com.radixdlt.api.service;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.radixdlt.api.Rri;
import com.radixdlt.api.data.ActionEntry;
import com.radixdlt.api.data.TxHistoryEntry;
import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.api.store.ClientApiStore.BalanceType;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
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

public class AccountInfoServiceTest {
	private static final ECPublicKey OWNER_KEY = ECKeyPair.generateNew().getPublicKey();
	private static final REAddr OWNER_ACCOUNT = REAddr.ofPubKeyAccount(OWNER_KEY);
	private static final ECPublicKey TOKEN_KEY = ECKeyPair.generateNew().getPublicKey();

	private final RadixEngine<LedgerAndBFTProof> radixEngine = mock(RadixEngine.class);
	private final AccountInfoService archiveService = new AccountInfoService(radixEngine, OWNER_KEY);

	@Test
	public void name() {
		//TODO: add tests
	}
}