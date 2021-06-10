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
package com.radixdlt.client.lib.api;

import org.junit.Ignore;
import org.junit.Test;

import com.radixdlt.client.lib.dto.TransactionDTO;
import com.radixdlt.client.lib.dto.TransactionHistory;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.UInt256;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

//TODO: move to acceptance tests
public class DefaultRadixApiHistoryPaginationTest {
	private static final String BASE_URL = "http://localhost/";
	public static final ECKeyPair KEY_PAIR1 = keyPairOf(1);
	public static final ECKeyPair KEY_PAIR2 = keyPairOf(2);
	private static final AccountAddress ACCOUNT_ADDRESS1 = AccountAddress.create(KEY_PAIR1.getPublicKey());
	private static final AccountAddress ACCOUNT_ADDRESS2 = AccountAddress.create(KEY_PAIR2.getPublicKey());

	@Test
	@Ignore
	public void testAddManyTransactions() {
		RadixApi.connect(BASE_URL)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> {
				for (int i = 0; i < 20; i++) {
					addTransaction(client, UInt256.from(i + 10));
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			});
	}

	@Test
	@Ignore
	public void testTransactionHistoryInPages() {
		RadixApi.connect(BASE_URL)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(
				client -> {
					var cursorHolder = new AtomicReference<NavigationCursor>();
					do {
						client.account().history(ACCOUNT_ADDRESS1, 5, Optional.ofNullable(cursorHolder.get()))
							.onFailure(failure -> fail(failure.toString()))
							.onSuccess(v -> v.getCursor().ifPresent(System.out::println))
							.onSuccess(v -> v.getCursor().ifPresentOrElse(cursorHolder::set, () -> cursorHolder.set(null)))
							.map(TransactionHistory::getTransactions)
							.map(this::formatTxns)
							.onSuccess(System.out::println);
					} while (cursorHolder.get() != null && !cursorHolder.get().value().isEmpty());
				}
			);
	}

	private List<String> formatTxns(List<TransactionDTO> t) {
		return t.stream()
			.map(v -> String.format(
				"%s (%s) - %s (%d:%d)%n",
				v.getTxID(),
				v.getMessage().orElse("<none>"),
				v.getSentAt().getInstant(),
				v.getSentAt().getInstant().getEpochSecond(),
				v.getSentAt().getInstant().getNano()
			))
			.collect(Collectors.toList());
	}

	private void addTransaction(RadixApi client, UInt256 amount) {
		var request = TransactionRequest.createBuilder()
			.transfer(
				ACCOUNT_ADDRESS1,
				ACCOUNT_ADDRESS2,
				amount,
				"xrd_rb1qya85pwq"
			)
			.message("Test message")
			.build();

		client.transaction().build(request)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(builtTransactionDTO -> assertEquals(UInt256.from(100000000000000000L), builtTransactionDTO.getFee()))
			.map(builtTransactionDTO -> builtTransactionDTO.toFinalized(KEY_PAIR1))
			.onSuccess(finalizedTransaction -> client.transaction().finalize(finalizedTransaction)
				.onSuccess(txDTO -> assertNotNull(txDTO.getTxId()))
				.map(txDTO -> finalizedTransaction.withTxId(txDTO.getTxId()))
				.onSuccess(submittableTransaction -> client.transaction().submit(submittableTransaction)
					.onFailure(failure -> fail(failure.toString()))
					.onSuccess(txDTO -> submittableTransaction.rawTxId()
						.ifPresentOrElse(aid -> assertEquals(aid, txDTO.getTxId()), () -> fail("Should not happen")))));
	}

	private static ECKeyPair keyPairOf(int pk) {
		var privateKey = new byte[ECKeyPair.BYTES];

		Ints.copyTo(pk, privateKey, ECKeyPair.BYTES - Integer.BYTES);

		try {
			return ECKeyPair.fromPrivateKey(privateKey);
		} catch (PrivateKeyException | PublicKeyException e) {
			throw new IllegalArgumentException("Error while generating public key", e);
		}
	}
}
