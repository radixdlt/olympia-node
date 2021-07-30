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

package com.radixdlt.api.store.berkeley;

import com.google.common.collect.Streams;
import com.google.inject.Inject;
import com.radixdlt.accounting.REResourceAccounting;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.RoundData;
import com.radixdlt.application.tokens.ResourceCreatedEvent;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.berkeley.BerkeleyAdditionalStore;
import com.radixdlt.utils.Longs;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Get;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;
import static com.sleepycat.je.LockMode.DEFAULT;
import static com.sleepycat.je.OperationStatus.SUCCESS;

public final class BerkeleyTransactionIndexArchiveStore implements BerkeleyAdditionalStore {
	private static final String TRANSACTIONS_DB = "radix.transactions";
	private static final String RESOURCE_SYMBOLS_DB = "radix.resource_symbols";
	private Database resources;
	private Database transactions;
	private final Addressing addressing;

	@Inject
	BerkeleyTransactionIndexArchiveStore(Addressing addressing) {
		this.addressing = addressing;
	}

	@Override
	public void open(DatabaseEnvironment dbEnv) {
		var env = dbEnv.getEnvironment();
		transactions = env.openDatabase(null, TRANSACTIONS_DB, new DatabaseConfig()
			.setAllowCreate(true)
			.setTransactional(true)
			.setKeyPrefixing(true)
			.setBtreeComparator(lexicographicalComparator())
		);
		resources = env.openDatabase(null, RESOURCE_SYMBOLS_DB, new DatabaseConfig()
			.setAllowCreate(true)
			.setTransactional(true)
			.setKeyPrefixing(true)
			.setBtreeComparator(lexicographicalComparator())
		);
	}

	@Override
	public void close() {
		if (transactions != null) {
			transactions.close();
		}

		if (resources != null) {
			resources.close();
		}
	}

	private void storeResourceSymbol(Transaction dbTxn, ResourceCreatedEvent resourceCreatedEvent) {
		var key = new DatabaseEntry(resourceCreatedEvent.getTokenResource().getAddr().getBytes());
		var symbol = resourceCreatedEvent.getSymbol();
		var value = new DatabaseEntry(symbol.getBytes(StandardCharsets.UTF_8));
		var status = resources.putNoOverwrite(dbTxn, key, value);
		if (status != OperationStatus.SUCCESS) {
			throw new IllegalStateException();
		}
	}

	private String getRri(Transaction dbTxn, REAddr addr) {
		var key = new DatabaseEntry(addr.getBytes());
		var value = new DatabaseEntry();
		var status = resources.get(dbTxn, key, value, null);
		if (status != OperationStatus.SUCCESS) {
			throw new IllegalStateException();
		}

		var symbol = new String(value.getData(), StandardCharsets.UTF_8);
		return addressing.forResources().of(symbol, addr);
	}

	public Stream<JSONObject> get(long stateVersion) {
		var cursor = transactions.openCursor(null, null);
		var iterator = new Iterator<byte[]>() {
			final DatabaseEntry key = new DatabaseEntry(Longs.toByteArray(stateVersion));
			final DatabaseEntry value = new DatabaseEntry();
			OperationStatus status = cursor.get(key, value, Get.SEARCH, null) != null ? SUCCESS : OperationStatus.NOTFOUND;

			@Override
			public boolean hasNext() {
				return status == SUCCESS;
			}

			@Override
			public byte[] next() {
				if (status != SUCCESS) {
					throw new NoSuchElementException();
				}
				var next = value.getData();
				status = cursor.getNext(key, value, null);
				return next;
			}
		};
		return Streams.stream(iterator)
			.map(b -> new JSONObject(new String(b, StandardCharsets.UTF_8)))
			.onClose(cursor::close);
	}

	@Override
	public void process(Transaction dbTxn, REProcessedTxn txn, long stateVersion) {
		final long expectedVersion;
		try (var cursor = transactions.openCursor(dbTxn, null)) {
			var key = new DatabaseEntry();
			var status = cursor.getLast(key, null, DEFAULT);
			if (status == OperationStatus.NOTFOUND) {
				expectedVersion = 1;
			} else {
				expectedVersion = Longs.fromByteArray(key.getData()) + 1;
			}
		}
		if (stateVersion != expectedVersion) {
			throw new IllegalStateException("Expected version " + expectedVersion + " but is " + stateVersion);
		}


		var txnJson = new JSONObject();
		txnJson.put("state_version", stateVersion);
		txnJson.put("transaction_identifier", txn.getTxnId().toString());
		txnJson.put("transaction_size", txn.getTxn().getPayload().length);

		var eventsJson = new JSONArray();
		for (var event : txn.getEvents()) {
			if (event instanceof ResourceCreatedEvent) {
				var resourceCreated = (ResourceCreatedEvent) event;
				storeResourceSymbol(dbTxn, resourceCreated);
				var rri = addressing.forResources().of(resourceCreated.getSymbol(), resourceCreated.getTokenResource().getAddr());
				var eventJson = new JSONObject()
					.put("type", "token_created")
					.put("rri", rri);
				eventsJson.put(eventJson);
			}
		}

		txn.stateUpdates().filter(REStateUpdate::isBootUp).forEach(update -> {
			var substate = update.getParsed();
			if (substate instanceof RoundData) {
				var d = (RoundData) substate;
				var eventJson = new JSONObject()
					.put("type", "new_round")
					.put("timestamp", d.getTimestamp())
					.put("round", d.getView());
				eventsJson.put(eventJson);
			} else if (substate instanceof EpochData) {
				var d = (EpochData) substate;
				var eventJson = new JSONObject()
					.put("type", "new_epoch")
					.put("epoch", d.getEpoch());
				eventsJson.put(eventJson);
			}
		});
		txnJson.put("events", eventsJson);

		var accounting = REResourceAccounting.compute(txn.stateUpdates());
		txnJson.put("fee_paid", txn.getFeePaid());
		var bucketAccounting = new JSONArray();
		for (var e : accounting.bucketAccounting().entrySet()) {
			var b = e.getKey();
			var i = e.getValue();

			var bucketJson = new JSONObject();
			if (b.getOwner() != null) {
				bucketJson.put("owner", addressing.forAccounts().of(b.getOwner()));
			}
			if (b.getValidatorKey() != null) {
				bucketJson.put("validator", addressing.forValidators().of(b.getValidatorKey()));
			}
			bucketJson.put("delta", i.toString());
			if (b.resourceAddr() == null) {
				// Don't keep track of stakeOwnership
				continue;
			}
			bucketJson.put("asset", getRri(dbTxn, b.resourceAddr()));
			bucketAccounting.put(bucketJson);
		}
		txnJson.put("accounting_entries", bucketAccounting);

		var key = new DatabaseEntry(Longs.toByteArray(stateVersion));
		var value = new DatabaseEntry(txnJson.toString().getBytes(StandardCharsets.UTF_8));
		transactions.putNoOverwrite(dbTxn, key, value);
	}
}
