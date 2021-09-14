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
package com.radixdlt.client.lib.api.async;

import org.junit.Ignore;
import org.junit.Test;

import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.NavigationCursor;
import com.radixdlt.client.lib.api.TransactionRequest;
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

/*
 * Before running this test, launch in separate console local network (cd radixdlt-core/docker && ./scripts/rundocker.sh 2).
 *
 * Then comment '@Ignore' annotations for both tests.
 *
 * Then run testAddManyTransactions() few times (it generates a number of transfer transactions)
 *
 * Then run testTransactionHistoryInPages(). It should print list of transactions split into batches of 50 (see parameters)
 */
//TODO: move to acceptance tests
public class AsyncRadixApiHistoryPaginationTest {
	private static final String BASE_URL = "http://localhost/";
	public static final ECKeyPair KEY_PAIR1 = keyPairOf(1);
	public static final ECKeyPair KEY_PAIR2 = keyPairOf(2);
	private static final AccountAddress ACCOUNT_ADDRESS1 = AccountAddress.create(KEY_PAIR1.getPublicKey());
	private static final AccountAddress ACCOUNT_ADDRESS2 = AccountAddress.create(KEY_PAIR2.getPublicKey());

	@Test
	@Ignore("Online test")
	public void testAddManyTransactions() {
		RadixApi.connect(BASE_URL)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> {
				for (int i = 0; i < 20; i++) {
					addTransaction(client, i);
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			});
	}

	@Test
	@Ignore("Online test")
	public void testTransactionHistoryInPages() {
		RadixApi.connect(BASE_URL)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(
				client -> {
					var cursorHolder = new AtomicReference<NavigationCursor>();
					do {
						client.account().history(ACCOUNT_ADDRESS1, 50, Optional.ofNullable(cursorHolder.get())).join()
							.onFailure(failure -> fail(failure.toString()))
							.onSuccess(v -> v.getCursor().ifPresent(System.out::println))
							.onSuccess(v -> v.getCursor().ifPresentOrElse(cursorHolder::set, () -> cursorHolder.set(null)))
							.map(TransactionHistory::getTransactions)
							.map(this::formatTxns)
							.onSuccess(System.out::println);
					} while (cursorHolder.get() != null && !cursorHolder.get().value().isEmpty());
				});
	}

	private List<String> formatTxns(List<TransactionDTO> t) {
		return t.stream()
			.map(v -> String.format(
				"%s (%s) - %s (%d:%d), Fee: %s%n",
				v.getTxID(),
				v.getMessage().orElse("<none>"),
				v.getSentAt().getInstant(),
				v.getSentAt().getInstant().getEpochSecond(),
				v.getSentAt().getInstant().getNano(),
				v.getFee()
			))
			.collect(Collectors.toList());
	}

	private void addTransaction(RadixApi client, int count) {
		var request = TransactionRequest.createBuilder(ACCOUNT_ADDRESS1)
			.transfer(
				ACCOUNT_ADDRESS1,
				ACCOUNT_ADDRESS2,
				UInt256.from(count + 10),
				"xrd_dr1qyrs8qwl"
			)
			.message("Test message " + count)
			.build();

		client.transaction().build(request).join()
			.onFailure(failure -> fail(failure.toString()))
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
