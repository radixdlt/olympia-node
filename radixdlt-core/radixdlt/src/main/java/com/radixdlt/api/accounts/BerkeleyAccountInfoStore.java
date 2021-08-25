/*
 * Copyright 2021 Radix DLT Ltd incorporated in England.
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

package com.radixdlt.api.accounts;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.radixdlt.accounting.REResourceAccounting;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.Bucket;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.berkeley.BerkeleyAdditionalStore;
import com.radixdlt.utils.Pair;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Transaction;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;
import static com.sleepycat.je.LockMode.DEFAULT;
import static com.sleepycat.je.OperationStatus.SUCCESS;

public final class BerkeleyAccountInfoStore implements BerkeleyAdditionalStore {
	private Database balancesDatabase;
	private Database exittingStakeDatabase;
	private Database preparedUnstakeOwnershipDatabase;
	private final Addressing addressing;
	private final Provider<RadixEngine<LedgerAndBFTProof>> radixEngineProvider;

	@Inject
	BerkeleyAccountInfoStore(Addressing addressing, Provider<RadixEngine<LedgerAndBFTProof>> radixEngineProvider) {
		this.addressing = addressing;
		this.radixEngineProvider = radixEngineProvider;
	}

	public JSONArray getAccountUnstakes(REAddr addr) {
		var key = new DatabaseEntry(addr.getBytes());
		var value = new DatabaseEntry();
		var jsonArray = new JSONArray();

		if (exittingStakeDatabase.get(null, key, value, DEFAULT) == SUCCESS) {
			var systemMapKey = SystemMapKey.ofSystem(SubstateTypeId.EPOCH_DATA.id());
			var epochData = (EpochData) radixEngineProvider.get().get(systemMapKey).orElseThrow();
			var curEpoch = epochData.getEpoch();
			jsonArray = new JSONArray(new String(value.getData(), StandardCharsets.UTF_8));
			for (int i = 0; i < jsonArray.length(); i++) {
				var json = jsonArray.getJSONObject(i);
				var epochUnlocked = json.getLong("epochUnlocked");
				json.remove("epochUnlocked");
				json.put("epochsUntil", epochUnlocked - curEpoch);
			}
		}

		if (preparedUnstakeOwnershipDatabase.get(null, key, value, DEFAULT) == SUCCESS) {
			var unstakeOwnershipJson = new JSONArray(new String(value.getData(), StandardCharsets.UTF_8));
			for (int i = 0; i < unstakeOwnershipJson.length(); i++) {
				var json = unstakeOwnershipJson.getJSONObject(i);
				var ownership = new BigInteger(json.getString("amount"), 10);
				var totalStake = new BigInteger(json.getString("validatorTotalStake"), 10);
				var totalOwnership = new BigInteger(json.getString("validatorTotalOwnership"), 10);
				var estimatedUnstake = ownership.multiply(totalStake).divide(totalOwnership);
				json.put("amount", estimatedUnstake.toString());
				json.put("epochsUntil", 500); // Hardcoded for now
				jsonArray.put(json);
			}
		}

		return jsonArray;
	}

	public JSONObject getAccountInfo(REAddr addr) {
		var key = new DatabaseEntry(addr.getBytes());
		var value = new DatabaseEntry();

		var json = new JSONObject()
			.put("owner", addressing.forAccounts().of(addr));

		if (balancesDatabase.get(null, key, value, DEFAULT) == SUCCESS) {
			var jsonArray = new JSONArray(new String(value.getData(), StandardCharsets.UTF_8));
			json.put("tokenBalances", jsonArray);
		} else {
			json.put("tokenBalances", new JSONArray());
		}

		return json;
	}

	@Override
	public void open(DatabaseEnvironment dbEnv) {
		var env = dbEnv.getEnvironment();
		balancesDatabase = env.openDatabase(null, "radix.account_info", new DatabaseConfig()
			.setAllowCreate(true)
			.setTransactional(true)
			.setKeyPrefixing(true)
			.setBtreeComparator(lexicographicalComparator())
		);
		exittingStakeDatabase = env.openDatabase(null, "radix.account_unstake_info", new DatabaseConfig()
			.setAllowCreate(true)
			.setTransactional(true)
			.setKeyPrefixing(true)
			.setBtreeComparator(lexicographicalComparator())
		);
		preparedUnstakeOwnershipDatabase = env.openDatabase(null, "radix.account_unstake_ownership", new DatabaseConfig()
			.setAllowCreate(true)
			.setTransactional(true)
			.setKeyPrefixing(true)
			.setBtreeComparator(lexicographicalComparator())
		);
	}

	@Override
	public void close() {
		if (balancesDatabase != null) {
			balancesDatabase.close();
		}
		if (exittingStakeDatabase != null) {
			exittingStakeDatabase.close();
		}
		if (preparedUnstakeOwnershipDatabase != null) {
			preparedUnstakeOwnershipDatabase.close();
		}
	}

	private Particle deserialize(byte[] data) {
		var deserialization = radixEngineProvider.get().getSubstateDeserialization();
		try {
			return deserialization.deserialize(data);
		} catch (DeserializeException e) {
			throw new IllegalStateException();
		}
	}

	private enum ResourceType {
		TOKEN_BALANCES {
			@Override
			boolean bucketCheck(Bucket bucket) {
				return bucket.resourceAddr() != null
					&& bucket.getValidatorKey() == null;
			}

			@Override
			Comparator<Object> comparator() {
				return Comparator.comparing(o -> (String) o);
			}

			@Override
			Object toKey(BerkeleyAccountInfoStore parent, Function<SystemMapKey, Optional<RawSubstateBytes>> mapper, Bucket bucket) {
				var resourceAddr = bucket.resourceAddr();
				var mapKey = SystemMapKey.ofResourceData(resourceAddr, SubstateTypeId.TOKEN_RESOURCE_METADATA.id());
				var data = mapper.apply(mapKey).orElseThrow().getData();
				// TODO: This is a bit of a hack to require deserialization, figure out correct abstraction
				var metadata = (TokenResourceMetadata) parent.deserialize(data);
				return parent.addressing.forResources().of(metadata.getSymbol(), resourceAddr);
			}

			@Override
			Object jsonToKey(JSONObject json) {
				return json.getString("rri");
			}

			@Override
			JSONObject toJSON(BerkeleyAccountInfoStore parent, Function<SystemMapKey, Optional<RawSubstateBytes>> mapper, Object o) {
				var rri = (String) o;
				return new JSONObject().put("rri", rri);
			}
		},
		PREPARED {
			@Override
			boolean bucketCheck(Bucket bucket) {
				return bucket.resourceAddr() == null
					&& bucket.getValidatorKey() != null
					&& bucket.getEpochUnlock() != null;
			}

			@Override
			Comparator<Object> comparator() {
				return Comparator.comparing(o -> (String) o);
			}

			@Override
			Object toKey(BerkeleyAccountInfoStore parent, Function<SystemMapKey, Optional<RawSubstateBytes>> mapper, Bucket bucket) {
				return parent.addressing.forValidators().of(bucket.getValidatorKey());
			}

			Object jsonToKey(JSONObject json) {
				return json.getString("validator");
			}

			@Override
			JSONObject toJSON(BerkeleyAccountInfoStore parent, Function<SystemMapKey, Optional<RawSubstateBytes>> mapper, Object o) {
				try {
					var validatorKey = parent.addressing.forValidators().parse((String) o);
					var validatorDataKey = SystemMapKey.ofSystem(
						SubstateTypeId.VALIDATOR_STAKE_DATA.id(),
						validatorKey.getCompressedBytes()
					);
					var stakeData = (ValidatorStakeData) parent.deserialize(mapper.apply(validatorDataKey).orElseThrow().getData());
					return new JSONObject()
						.put("validator", o)
						.put("validatorTotalStake", stakeData.getTotalStake())
						.put("validatorTotalOwnership", stakeData.getTotalOwnership());
				} catch (DeserializeException e) {
					throw new IllegalStateException();
				}
			}
		},
		EXITTING {
			@Override
			boolean bucketCheck(Bucket bucket) {
				return Objects.equals(bucket.resourceAddr(), REAddr.ofNativeToken())
					&& bucket.getValidatorKey() != null
					&& bucket.getEpochUnlock() != null;
			}

			@Override
			Comparator<Object> comparator() {
				return Comparator.comparing(o -> ((Pair<String, Long>) o).getFirst());
			}

			@Override
			Object toKey(BerkeleyAccountInfoStore parent, Function<SystemMapKey, Optional<RawSubstateBytes>> mapper, Bucket bucket) {
				return Pair.of(parent.addressing.forValidators().of(bucket.getValidatorKey()), bucket.getEpochUnlock());
			}

			@Override
			Object jsonToKey(JSONObject json) {
				return Pair.of(
					json.getString("validator"),
					json.getLong("epochUnlocked")
				);
			}

			@Override
			JSONObject toJSON(BerkeleyAccountInfoStore parent, Function<SystemMapKey, Optional<RawSubstateBytes>> mapper, Object o) {
				var validatorAndEpoch = (Pair<String, Long>) o;
				return new JSONObject()
					.put("validator", validatorAndEpoch.getFirst())
					.put("epochUnlocked", validatorAndEpoch.getSecond());
			}
		};

		abstract Comparator<Object> comparator();
		abstract boolean bucketCheck(Bucket bucket);
		abstract Object toKey(BerkeleyAccountInfoStore parent, Function<SystemMapKey, Optional<RawSubstateBytes>> mapper, Bucket bucket);
		abstract JSONObject toJSON(BerkeleyAccountInfoStore parent, Function<SystemMapKey, Optional<RawSubstateBytes>> mapper, Object o);
		abstract Object jsonToKey(JSONObject json);
	}

	private void storeAccounting(
		Transaction dbTxn,
		REResourceAccounting accounting,
		ResourceType resourceType,
		Database db,
		Function<SystemMapKey, Optional<RawSubstateBytes>> mapper
	) {
		var unstakeAccounting = accounting.bucketAccounting().entrySet().stream()
			.filter(e -> resourceType.bucketCheck(e.getKey()))
			.collect(Collectors.groupingBy(
				e -> e.getKey().getOwner(),
				Collectors.toMap(
					e -> resourceType.toKey(this, mapper, e.getKey()),
					Map.Entry::getValue
				)
			));
		unstakeAccounting.forEach((accountAddr, unstakes) -> {
			var key = new DatabaseEntry(accountAddr.getBytes());
			var value = new DatabaseEntry();
			var preparedMap = new TreeMap<Object, BigInteger>(resourceType.comparator());
			var status = db.get(dbTxn, key, value, null);
			if (status == SUCCESS) {
				var jsonString = new String(value.getData(), StandardCharsets.UTF_8);
				var jsonArray = new JSONArray(jsonString);
				for (int i = 0; i < jsonArray.length(); i++) {
					var json = jsonArray.getJSONObject(i);
					var k = resourceType.jsonToKey(json);
					var amount = new BigInteger(json.getString("amount"), 10);
					preparedMap.put(k, amount);
				}
			}
			unstakes.forEach((p, amt) ->
				preparedMap.compute(p, (pair, cur) -> {
					var curAmt = cur == null ? BigInteger.ZERO : cur;
					var nextAmt = curAmt.add(amt);
					return nextAmt.equals(BigInteger.ZERO) ? null : nextAmt;
				})
			);

			var nextUnstakes = new JSONArray();
			preparedMap.forEach((validator, amt) ->
				nextUnstakes.put(resourceType.toJSON(this, mapper, validator).put("amount", amt.toString()))
			);

			var nextValue = new DatabaseEntry(nextUnstakes.toString().getBytes(StandardCharsets.UTF_8));
			status = db.put(dbTxn, key, nextValue);
			if (status != SUCCESS) {
				throw new IllegalStateException();
			}
		});
	}


	@Override
	public void process(
		Transaction dbTxn,
		REProcessedTxn txn,
		long stateVersion,
		Function<SystemMapKey, Optional<RawSubstateBytes>> mapper
	) {
		var accounting = REResourceAccounting.compute(txn.stateUpdates());
		storeAccounting(dbTxn, accounting, ResourceType.PREPARED, preparedUnstakeOwnershipDatabase, mapper);
		storeAccounting(dbTxn, accounting, ResourceType.EXITTING, exittingStakeDatabase, mapper);
		storeAccounting(dbTxn, accounting, ResourceType.TOKEN_BALANCES, balancesDatabase, mapper);
	}
}
