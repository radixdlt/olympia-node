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
package com.radixdlt.client.lib.api.async;

import org.junit.Ignore;
import org.junit.Test;

import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.NavigationCursor;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.api.ValidatorAddress;
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

import static com.radixdlt.client.lib.api.async.RadixApi.connect;
import static com.radixdlt.client.lib.api.token.Amount.amount;

//TODO: move to acceptance tests and repurpose to integration testing of the API's.
public class AsyncRadixApiTest {
	private static final String BASE_URL = "http://localhost/";
	public static final ECKeyPair KEY_PAIR1 = keyPairOf(1);
	public static final ECKeyPair KEY_PAIR2 = keyPairOf(2);
	private static final AccountAddress ACCOUNT_ADDRESS1 = AccountAddress.create(KEY_PAIR1.getPublicKey());
	private static final AccountAddress ACCOUNT_ADDRESS2 = AccountAddress.create(KEY_PAIR2.getPublicKey());

	@Test
	@Ignore //Useful testbed for experiments
	public void testBuildTransactionWithMessage() {
		var request = TransactionRequest.createBuilder(ACCOUNT_ADDRESS1)
			.transfer(
				ACCOUNT_ADDRESS1,
				ACCOUNT_ADDRESS2,
				UInt256.NINE,
				"xrd_dr1qyrs8qwl"
			)
			.message("Test message")
			.build();

		connect(BASE_URL)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.transaction().build(request)
				.join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(builtTransactionDTO -> assertEquals(UInt256.from(100000000000000000L), builtTransactionDTO.getFee()))
				.map(builtTransactionDTO -> builtTransactionDTO.toFinalized(KEY_PAIR1))
				.onSuccess(finalizedTransaction -> client.transaction().finalize(finalizedTransaction, false)
					.join()
					.onSuccess(txDTO -> assertNotNull(txDTO.getTxId()))
					.onSuccess(submittableTransaction -> client.transaction().submit(submittableTransaction)
						.join()
						.onFailure(failure -> fail(failure.toString()))
						.onSuccess(txDTO -> assertEquals(submittableTransaction.getTxId(), txDTO.getTxId())))));
	}

	@Test
	@Ignore    //Useful testbed for experiments
	public void testTransactionHistoryInPages() {
		connect(BASE_URL)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(
				client -> {
					var cursorHolder = new AtomicReference<NavigationCursor>();
					do {
						client.account().history(ACCOUNT_ADDRESS1, 5, Optional.ofNullable(cursorHolder.get()))
							.join()
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

	@Test
	@Ignore //Useful testbed for experiments
	public void addManyTransactions() {
		connect(BASE_URL)
			.map(RadixApi::withTrace)
			.join()
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
	public void listStakes() {
		connect(BASE_URL)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.account().stakes(ACCOUNT_ADDRESS1)
				.join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(stakePositionsDTOS -> System.out.println("Stake positions: " + stakePositionsDTOS.toString()))
			);
	}

	@Test
	@Ignore
	public void listUnStakes() {
		connect(BASE_URL)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.account().unstakes(ACCOUNT_ADDRESS1)
				.join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(unstakePositionsDTOS -> System.out.println("UnStake positions: " + unstakePositionsDTOS.toString()))
			);
	}

	@Test
	@Ignore
	public void makeStake() {
		connect(BASE_URL)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> makeStake(client, amount(200).tokens()));
	}

	@Test
	@Ignore
	public void makeUnStake() {
		connect(BASE_URL)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> makeUnStake(client, amount(100).tokens()));
	}

	@Test
	@Ignore
	public void transferUnStake() {
		connect(BASE_URL)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> transferUnStake(client, amount(100).tokens()));
	}

	private void transferUnStake(RadixApi client, UInt256 amount) {
		var request = TransactionRequest.createBuilder(ACCOUNT_ADDRESS2)
			.transfer(
				ACCOUNT_ADDRESS2,
				ACCOUNT_ADDRESS1,
				amount,
				"xrd_dr1qyrs8qwl"
			)
			.message("Test message")
			.build();

		client.transaction().build(request).join()
			.onFailure(failure -> fail(failure.toString()))
			.map(builtTransactionDTO -> builtTransactionDTO.toFinalized(KEY_PAIR2))
			.onSuccess(finalizedTransaction -> client.transaction().finalize(finalizedTransaction, false).join()
				.onSuccess(submittableTransaction -> client.transaction().submit(submittableTransaction).join()
					.onFailure(failure -> fail(failure.toString()))
					.onSuccess(txDTO -> assertEquals(submittableTransaction.getTxId(), txDTO.getTxId()))));
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

	private void makeStake(RadixApi client, UInt256 amount) {
		client.local().validatorInfo()
			.join()
			.map(account -> TransactionRequest.createBuilder(ACCOUNT_ADDRESS1)
				.stake(ACCOUNT_ADDRESS1, account.getAddress(), amount)
				.build())
			.onSuccess(request -> client.transaction().build(request)
				.join()
				.onFailure(failure -> fail(failure.toString()))
				.map(builtTransactionDTO -> builtTransactionDTO.toFinalized(KEY_PAIR1))
				.flatMap(finalizedTransaction -> client.transaction().finalize(finalizedTransaction, true).join())
				.onSuccess(System.out::println));
	}

	private void makeUnStake(RadixApi client, UInt256 amount) {
		var request = TransactionRequest.createBuilder(ACCOUNT_ADDRESS1)
			.unstake(ACCOUNT_ADDRESS1, ValidatorAddress.of(KEY_PAIR2.getPublicKey()), amount)
			.build();

		client.transaction().build(request).join()
			.onFailure(failure -> fail(failure.toString()))
			.map(builtTransactionDTO -> builtTransactionDTO.toFinalized(KEY_PAIR1))
			.onSuccess(finalizedTransaction -> client.transaction().finalize(finalizedTransaction, false).join()
				.onSuccess(submittableTransaction -> client.transaction().submit(submittableTransaction).join()
					.onFailure(failure -> fail(failure.toString()))
					.onSuccess(txDTO -> assertEquals(submittableTransaction.getTxId(), txDTO.getTxId()))));
	}

	private void addTransaction(RadixApi client, UInt256 amount) {
		var request = TransactionRequest.createBuilder(ACCOUNT_ADDRESS1)
			.transfer(
				ACCOUNT_ADDRESS1,
				ACCOUNT_ADDRESS2,
				amount,
				"xrd_dr1qyrs8qwl"
			)
			.message("Test message")
			.build();

		client.transaction().build(request).join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(builtTransactionDTO -> assertEquals(amount(73800L).micros(), builtTransactionDTO.getFee()))
			.map(builtTransactionDTO -> builtTransactionDTO.toFinalized(KEY_PAIR1))
			.onSuccess(finalizedTransaction -> client.transaction().finalize(finalizedTransaction, false).join()
				.onSuccess(txDTO -> assertNotNull(txDTO.getTxId()))
				.onSuccess(submittableTransaction -> client.transaction().submit(submittableTransaction).join()
					.onFailure(failure -> fail(failure.toString()))
					.onSuccess(txDTO -> assertEquals(submittableTransaction.getTxId(), txDTO.getTxId()))));
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
