/*
 * Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
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

package com.radixdlt.api.gateway.tokens;

import com.google.inject.Inject;
import com.radixdlt.accounting.REResourceAccounting;
import com.radixdlt.application.tokens.ResourceCreatedEvent;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.berkeley.BerkeleyAdditionalStore;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.Transaction;
import org.json.JSONObject;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Function;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;
import static com.sleepycat.je.LockMode.DEFAULT;
import static com.sleepycat.je.OperationStatus.SUCCESS;

public final class BerkeleyResourceInfoStore implements BerkeleyAdditionalStore {
	private static final String TRANSACTIONS_DB = "radix.resource_info";
	private Database resourceInfoDatabase;
	private final Addressing addressing;

	@Inject
	BerkeleyResourceInfoStore(Addressing addressing) {
		this.addressing = addressing;
	}

	public Optional<JSONObject> getResourceInfo(REAddr addr) {
		var key = new DatabaseEntry(addr.getBytes());
		var value = new DatabaseEntry();

		if (resourceInfoDatabase.get(null, key, value, DEFAULT) == SUCCESS) {
			return Optional.of(new JSONObject(new String(value.getData(), StandardCharsets.UTF_8)));
		}

		return Optional.empty();
	}

	@Override
	public void open(DatabaseEnvironment dbEnv) {
		var env = dbEnv.getEnvironment();
		resourceInfoDatabase = env.openDatabase(null, TRANSACTIONS_DB, new DatabaseConfig()
			.setAllowCreate(true)
			.setTransactional(true)
			.setKeyPrefixing(true)
			.setBtreeComparator(lexicographicalComparator())
		);
	}

	@Override
	public void close() {
		if (resourceInfoDatabase != null) {
			resourceInfoDatabase.close();
		}
	}

	@Override
	public void process(
		Transaction dbTxn,
		REProcessedTxn txn,
		long stateVersion,
		Function<SystemMapKey, Optional<RawSubstateBytes>> mapper
	) {
		for (var event : txn.getEvents()) {
			if (event instanceof ResourceCreatedEvent) {
				var resourceCreated = (ResourceCreatedEvent) event;
				var rri = addressing.forResources().of(resourceCreated.getSymbol(), resourceCreated.getTokenResource().getAddr());
				var properties = new JSONObject()
					.put("name", resourceCreated.getMetadata().getName())
					.put("symbol", resourceCreated.getSymbol())
					.put("description", resourceCreated.getMetadata().getDescription())
					.put("icon_url", resourceCreated.getMetadata().getIconUrl())
					.put("url", resourceCreated.getMetadata().getUrl())
					.put("granularity", resourceCreated.getTokenResource().getGranularity())
					.put("is_supply_mutable", resourceCreated.getTokenResource().isMutable());

				var info = new JSONObject()
					.put("total_burned", BigInteger.ZERO.toString())
					.put("total_minted", BigInteger.ZERO.toString());

				var json = new JSONObject()
					.put("rri", rri)
					.put("supply", new JSONObject()
						.put("rri", rri)
						.put("value", BigInteger.ZERO.toString())
					)
					.put("properties", properties)
					.put("info", info);

				var key = new DatabaseEntry(resourceCreated.getTokenResource().getAddr().getBytes());
				var value = new DatabaseEntry(json.toString().getBytes(StandardCharsets.UTF_8));
				var status = resourceInfoDatabase.putNoOverwrite(dbTxn, key, value);
				if (status != SUCCESS) {
					throw new IllegalStateException();
				}
			}
		}

		var accounting = REResourceAccounting.compute(txn.stateUpdates());
		var resourceAccounting = accounting.resourceAccounting();
		resourceAccounting.entrySet().stream()
			.filter(e -> !e.getValue().equals(BigInteger.ZERO))
			.forEach(e -> {
				var resourceAddr = e.getKey();
				var key = new DatabaseEntry(resourceAddr.getBytes());
				var value = new DatabaseEntry();
				var status = resourceInfoDatabase.get(dbTxn, key, value, LockMode.READ_UNCOMMITTED);
				if (status != SUCCESS) {
					throw new IllegalStateException();
				}
				var jsonString = new String(value.getData(), StandardCharsets.UTF_8);
				var json = new JSONObject(jsonString);
				var infoJson = json.getJSONObject("info");
				var supplyJson = json.getJSONObject("supply");
				var supply = new BigInteger(supplyJson.getString("value"), 10);
				var change = e.getValue();
				var newSupply = supply.add(change);
				supplyJson.put("value", newSupply.toString());
				if (change.signum() > 0) {
					var minted = new BigInteger(infoJson.getString("total_minted"), 10);
					var newMinted = minted.add(change);
					infoJson.put("total_minted", newMinted.toString());
				} else {
					var burned = new BigInteger(infoJson.getString("total_burned"), 10);
					var newBurned = burned.subtract(change);
					infoJson.put("total_burned", newBurned.toString());
				}
				var newVal = new DatabaseEntry(json.toString().getBytes(StandardCharsets.UTF_8));
				status = resourceInfoDatabase.put(dbTxn, key, newVal);
				if (status != SUCCESS) {
					throw new IllegalStateException();
				}
			});
	}
}
