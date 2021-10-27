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

package com.radixdlt.api.service.transactions;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.radixdlt.accounting.REResourceAccounting;
import com.radixdlt.accounting.TwoActorEntry;
import com.radixdlt.api.archive.construction.ActionType;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.RoundData;
import com.radixdlt.application.system.state.StakeOwnershipBucket;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.ResourceCreatedEvent;
import com.radixdlt.application.tokens.state.AccountBucket;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.berkeley.BerkeleyAdditionalStore;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Transaction;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;
import static com.sleepycat.je.LockMode.DEFAULT;
import static com.sleepycat.je.OperationStatus.SUCCESS;

public final class BerkeleyTransactionsByIdStore implements BerkeleyAdditionalStore {
	private static final String TXN_ID_DB_NAME = "radix.transactions";
	private Database txnIdDatabase; // Txns by AID; Append-only
	private final AtomicReference<Instant> timestamp = new AtomicReference<>();
	private final Addressing addressing;
	private final Provider<RadixEngine<LedgerAndBFTProof>> radixEngineProvider;

	@Inject
	public BerkeleyTransactionsByIdStore(Provider<RadixEngine<LedgerAndBFTProof>> radixEngineProvider, Addressing addressing) {
		// TODO: Fix this when we move AdditionalStore to be a RadixEngine construct rather than Berkeley construct
		this.radixEngineProvider = radixEngineProvider;
		this.addressing = addressing;
	}

	@Override
	public void open(DatabaseEnvironment dbEnv) {
		txnIdDatabase = dbEnv.getEnvironment().openDatabase(null, TXN_ID_DB_NAME, new DatabaseConfig()
			.setAllowCreate(true)
			.setTransactional(true)
			.setKeyPrefixing(true)
			.setBtreeComparator(lexicographicalComparator())
		);
	}

	public boolean contains(AID aid) {
		var key = new DatabaseEntry(aid.getBytes());
		return SUCCESS == txnIdDatabase.get(null, key, null, DEFAULT);
	}

	public Optional<JSONObject> getTransactionJSON(AID aid) {
		var key = new DatabaseEntry(aid.getBytes());
		var value = new DatabaseEntry();

		if (txnIdDatabase.get(null, key, value, DEFAULT) == SUCCESS) {
			return Optional.of(new JSONObject(new String(value.getData(), StandardCharsets.UTF_8)));
		}

		return Optional.empty();
	}

	@Override
	public void close() {
		if (txnIdDatabase != null) {
			txnIdDatabase.close();
		}
	}

	private JSONArray getOperations(REResourceAccounting accounting, Function<SystemMapKey, Optional<RawSubstateBytes>> mapper) {
		var bucketAccounting = new JSONArray();
		for (var e : accounting.bucketAccounting().entrySet()) {
			var b = e.getKey();
			var i = e.getValue();

			var bucketJson = new JSONObject();

			if (b.resourceAddr() == null || (b.getOwner() != null && b.getValidatorKey() == null)) {
				bucketJson.put("vaultIdentifier", new JSONObject()
					.put("type", "account")
					.put("address", addressing.forAccounts().of(b.getOwner()))
				);
			} else if (b.getOwner() != null && b.getValidatorKey() != null) {
				bucketJson.put("vaultIdentifier", new JSONObject()
					.put("type", "prepared_stake")
					.put("validator", addressing.forValidators().of(b.getValidatorKey()))
					.put("ownerAddress", addressing.forAccounts().of(b.getOwner()))
				);
			} else if (b.getOwner() == null && b.getValidatorKey() != null) {
				bucketJson.put("vaultIdentifier", new JSONObject()
					.put("type", "stake")
					.put("validator", addressing.forValidators().of(b.getValidatorKey()))
				);
			}

			var resource = new JSONObject();
			if (b.resourceAddr() == null) {
				resource
					.put("type", "stake_ownership")
					.put("validator", addressing.forValidators().of(b.getValidatorKey()));
			} else {
				var mapKey = SystemMapKey.ofResourceData(b.resourceAddr(), SubstateTypeId.TOKEN_RESOURCE_METADATA.id());
				var data = mapper.apply(mapKey).orElseThrow().getData();
				// TODO: This is a bit of a hack to require deserialization, figure out correct abstraction
				var metadata = (TokenResourceMetadata) deserialize(data);
				var rri = addressing.forResources().of(metadata.getSymbol(), b.resourceAddr());
				resource
					.put("type", "token")
					.put("rri", rri);
			}

			bucketJson.put("amount", new JSONObject()
				.put("value", i.toString())
				.put("resource", resource)
			);

			bucketAccounting.put(bucketJson);
		}
		return bucketAccounting;
	}

	private JSONArray getOperationGroups(REProcessedTxn txn, Function<SystemMapKey, Optional<RawSubstateBytes>> mapper) {
		var operationGroups = new JSONArray();
		for (var stateUpdates : txn.getGroupedStateUpdates()) {
			var accounting = REResourceAccounting.compute(stateUpdates.stream());
			var operations = getOperations(accounting, mapper);
			var entry = TwoActorEntry.parse(accounting.bucketAccounting());
			var mappedAction = entry.map(e -> mapToJSON(
				e,
				addr -> {
					var mapKey = SystemMapKey.ofResourceData(addr, SubstateTypeId.TOKEN_RESOURCE_METADATA.id());
					var data = mapper.apply(mapKey).orElseThrow().getData();
					// TODO: This is a bit of a hack to require deserialization, figure out correct abstraction
					var metadata = (TokenResourceMetadata) deserialize(data);
					return addressing.forResources().of(metadata.getSymbol(), addr);
				},
				(k, ownership) -> {
					var validatorDataKey = SystemMapKey.ofSystem(SubstateTypeId.VALIDATOR_STAKE_DATA.id(), k.getCompressedBytes());
					var data = mapper.apply(validatorDataKey).orElseThrow().getData();
					var stakeData = (ValidatorStakeData) deserialize(data);
					return ownership.multiply(stakeData.getTotalStake()).divide(stakeData.getTotalOwnership());
				}
			));

			var operationGroup = new JSONObject()
				.put("operations", operations);
			mappedAction.ifPresent(jsonObject -> operationGroup.put("metadata", new JSONObject()
				.put("inferredAction", jsonObject)
			));

			operationGroups.put(operationGroup);
		}

		return operationGroups;
	}


	private JSONArray getEventsJson(REProcessedTxn txn) {
		var eventsJson = new JSONArray();
		for (var event : txn.getEvents()) {
			if (event instanceof ResourceCreatedEvent) {
				var resourceCreated = (ResourceCreatedEvent) event;
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
		return eventsJson;
	}

	private JSONObject mapToJSON(
		TwoActorEntry entry,
		Function<REAddr, String> addrToRri,
		BiFunction<ECPublicKey, UInt384, UInt384> computeStakeFromOwnership
	) {
		var amtByteArray = entry.amount().toByteArray();
		var amt = UInt256.from(amtByteArray);
		var from = entry.from();
		var to = entry.to();
		var result = new JSONObject();
		if (from.isEmpty()) {
			var toBucket = to.orElseThrow();
			if (!(toBucket instanceof AccountBucket)) {
				return new JSONObject()
					.put("type", ActionType.UNKNOWN.toString());
			}
			result.put("to", addressing.forAccounts().of(toBucket.getOwner()));
			result.put("type", ActionType.MINT.toString());
		} else if (to.isEmpty()) {
			result.put("from", addressing.forAccounts().of(from.get().getOwner()));
			result.put("type", ActionType.BURN.toString());
		} else {
			var fromBucket = from.get();
			var toBucket = to.get();
			if (fromBucket instanceof AccountBucket) {
				if (toBucket instanceof AccountBucket) {
					result
						.put("from", addressing.forAccounts().of(fromBucket.getOwner()))
						.put("to", addressing.forAccounts().of(toBucket.getOwner()))
						.put("type", ActionType.TRANSFER.toString());
				} else {
					result
						.put("from", addressing.forAccounts().of(fromBucket.getOwner()))
						.put("validator", addressing.forValidators().of(toBucket.getValidatorKey()))
						.put("type", ActionType.STAKE.toString());
				}
			} else if (fromBucket instanceof StakeOwnershipBucket) {
				amt = computeStakeFromOwnership.apply(fromBucket.getValidatorKey(), UInt384.from(amt)).getLow();
				result
					.put("from", addressing.forAccounts().of(toBucket.getOwner())) // FIXME: badness in API spec
					.put("validator", addressing.forValidators().of(fromBucket.getValidatorKey()))
					.put("type", ActionType.UNSTAKE.toString());
			} else {
				return new JSONObject()
					.put("type", ActionType.UNKNOWN.toString());
			}
		}

		return result
			.put("amount", amt)
			.put("rri", addrToRri.apply(entry.resourceAddr().orElse(REAddr.ofNativeToken())));
	}

	private Particle deserialize(byte[] data) {
		var deserialization = radixEngineProvider.get().getSubstateDeserialization();
		try {
			return deserialization.deserialize(data);
		} catch (DeserializeException e) {
			throw new IllegalStateException();
		}
	}

	@Override
	public void process(Transaction dbTxn, REProcessedTxn txn, long stateVersion, Function<SystemMapKey, Optional<RawSubstateBytes>> mapper) {
		txn.stateUpdates()
			.filter(u -> u.getParsed() instanceof RoundData)
			.map(u -> (RoundData) u.getParsed())
			.filter(r -> r.getTimestamp() > 0)
			.map(RoundData::asInstant)
			.forEach(timestamp::set);

		var txnId = txn.getTxnId();
		var key = new DatabaseEntry(txnId.getBytes());
		var eventsJson = getEventsJson(txn);
		var operationGroups = getOperationGroups(txn, mapper);
		var fee = txn.getFeePaid();
		var message = txn.getMsg()
			.map(bytes -> new String(bytes, RadixConstants.STANDARD_CHARSET));
		var jsonString = new JSONObject()
			.put("committedTransactionIdentifier", new JSONObject()
				.put("transactionIdentifier", txn.getTxnId())
				.put("stateVersion", stateVersion)
			)
			.put("metadata", new JSONObject()
				.put("hex", Bytes.toHexString(txn.getTxn().getPayload()))
				.put("fee", fee)
				.put("size", txn.getTxn().getPayload().length)
				.put("timestamp", timestamp.get().toEpochMilli())
				.putOpt("message", message.orElse(null))
			)
			.put("operationGroups", operationGroups)
			.put("events", eventsJson)
			.toString();

		var value = new DatabaseEntry(jsonString.getBytes(StandardCharsets.UTF_8));
		var result = txnIdDatabase.putNoOverwrite(dbTxn, key, value);
		if (result != SUCCESS) {
			throw new IllegalStateException("Unexpected operation status " + result);
		}
	}
}
