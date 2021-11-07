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
import com.radixdlt.accounting.REResourceAccounting;
import com.radixdlt.accounting.TwoActorEntry;
import com.radixdlt.api.archive.construction.ActionType;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.RoundData;
import com.radixdlt.application.system.state.StakeOwnershipBucket;
import com.radixdlt.application.system.state.SystemData;
import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.ResourceInBucket;
import com.radixdlt.application.tokens.state.AccountBucket;
import com.radixdlt.application.tokens.state.ResourceData;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.application.validators.state.ValidatorData;
import com.radixdlt.application.validators.state.ValidatorFeeCopy;
import com.radixdlt.application.validators.state.ValidatorMetaData;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.application.validators.state.ValidatorSystemMetadata;
import com.radixdlt.application.validators.state.ValidatorUpdatingData;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.radixdlt.atom.SubstateTypeId.*;

public final class ProcessedTxnJsonConverter {
	private final Addressing addressing;
	private static final Map<SubstateTypeId, String> SUBSTATE_TYPE_ID_STRING_MAP;
	static {
		SUBSTATE_TYPE_ID_STRING_MAP = new EnumMap<>(SubstateTypeId.class);
		SUBSTATE_TYPE_ID_STRING_MAP.put(VIRTUAL_PARENT, "VirtualParent");
		SUBSTATE_TYPE_ID_STRING_MAP.put(UNCLAIMED_READDR, "UnclaimedRadixEngineAddress");
		SUBSTATE_TYPE_ID_STRING_MAP.put(ROUND_DATA, "RoundData");
		SUBSTATE_TYPE_ID_STRING_MAP.put(EPOCH_DATA, "EpochData");
		SUBSTATE_TYPE_ID_STRING_MAP.put(TOKEN_RESOURCE, "TokenData");
		SUBSTATE_TYPE_ID_STRING_MAP.put(TOKEN_RESOURCE_METADATA, "TokenMetadata");
		SUBSTATE_TYPE_ID_STRING_MAP.put(TOKENS, "Tokens");
		SUBSTATE_TYPE_ID_STRING_MAP.put(PREPARED_STAKE, "PreparedStake");
		SUBSTATE_TYPE_ID_STRING_MAP.put(STAKE_OWNERSHIP, "StakeOwnership");
		SUBSTATE_TYPE_ID_STRING_MAP.put(PREPARED_UNSTAKE, "PreparedUnstake");
		SUBSTATE_TYPE_ID_STRING_MAP.put(EXITING_STAKE, "ExitingStake");
		SUBSTATE_TYPE_ID_STRING_MAP.put(VALIDATOR_META_DATA, "ValidatorMetadata");
		SUBSTATE_TYPE_ID_STRING_MAP.put(VALIDATOR_STAKE_DATA, "ValidatorData");
		SUBSTATE_TYPE_ID_STRING_MAP.put(VALIDATOR_BFT_DATA, "ValidatorBFTData");
		SUBSTATE_TYPE_ID_STRING_MAP.put(VALIDATOR_ALLOW_DELEGATION_FLAG, "ValidatorAllowDelegation");
		SUBSTATE_TYPE_ID_STRING_MAP.put(VALIDATOR_REGISTERED_FLAG_COPY, "PreparedValidatorRegistered");
		SUBSTATE_TYPE_ID_STRING_MAP.put(VALIDATOR_RAKE_COPY, "PreparedValidatorFee");
		SUBSTATE_TYPE_ID_STRING_MAP.put(VALIDATOR_OWNER_COPY, "PreparedValidatorOwner");
		SUBSTATE_TYPE_ID_STRING_MAP.put(VALIDATOR_SYSTEM_META_DATA, "ValidatorSystemMetadata");

		for (var id : SubstateTypeId.values()) {
			if (!SUBSTATE_TYPE_ID_STRING_MAP.containsKey(id)) {
				throw new IllegalStateException("No string associated with substate type id " + id);
			}
		}
	}

	@Inject
	ProcessedTxnJsonConverter(Addressing addressing) {
		this.addressing = addressing;
	}

	public JSONArray getOperationGroups(
		REProcessedTxn txn,
		Function<REAddr, String> addressToRri,
		Function<ECPublicKey, ValidatorStakeData> getValidatorStake
	) {
		var operationGroups = new JSONArray();

		for (var stateUpdates : txn.getGroupedStateUpdates()) {
			var operations = getOperations(stateUpdates, addressToRri);
			var operationGroup = new JSONObject()
				.put("operations", operations);

			inferAction(stateUpdates, addressToRri, getValidatorStake)
				.ifPresent(jsonObject -> operationGroup.put("metadata", new JSONObject().put("action", jsonObject)));

			operationGroups.put(operationGroup);
		}

		return operationGroups;
	}

	private JSONArray getOperations(List<REStateUpdate> stateUpdates, Function<REAddr, String> addressToRri) {
		var operations = new JSONArray();
		for (var stateUpdate : stateUpdates) {
			var operation = getOperation(stateUpdate, addressToRri);
			operations.put(operation);
		}
		return operations;
	}

	private JSONObject getOperation(
		REStateUpdate update,
		Function<REAddr, String> tokenAddressToRri
	) {
		var substateId = update.getId();
		var operationJson = new JSONObject()
			.put("type", SUBSTATE_TYPE_ID_STRING_MAP.get(SubstateTypeId.valueOf(update.typeByte())))
			.put("substate", new JSONObject()
				.put("substate_identifier", Bytes.toHexString(substateId.asBytes()))
				.put("substate_operation", update.isBootUp() ? "BOOTUP" : "SHUTDOWN")
			)
			.putOpt("metadata", update.isShutDown() ? null : new JSONObject()
				.put("substate_data_hex", Bytes.toHexString(update.getRawSubstateBytes().getData()))
			);

		if (update.getParsed() instanceof ResourceInBucket) {
			var resourceInBucket = (ResourceInBucket) update.getParsed();
			var bucket = resourceInBucket.bucket();
			var amount = new BigInteger(update.isBootUp() ? 1 : -1, resourceInBucket.getAmount().toByteArray());
			var resourceIdentifier = new JSONObject();
			if (bucket.resourceAddr() == null) {
				resourceIdentifier
					.put("type", "StakeOwnership")
					.put("validator", addressing.forValidators().of(bucket.getValidatorKey()));
			} else {
				var rri = tokenAddressToRri.apply(bucket.resourceAddr());
				resourceIdentifier
					.put("type", "Token")
					.put("rri", rri);
			}

			final JSONObject addressIdentifier = new JSONObject();
			if (bucket.getOwner() != null) {
				addressIdentifier.put("address", addressing.forAccounts().of(bucket.getOwner()));
				if (bucket.getValidatorKey() != null) {
					var subAddressJson = new JSONObject()
						.put("metadata", new JSONObject()
							.put("validator", addressing.forValidators().of(bucket.getValidatorKey()))
						);
					addressIdentifier.put("sub_address", subAddressJson);
					if (bucket.getEpochUnlock() == null) {
						subAddressJson.put("address", "prepared_stakes");
					} else if (bucket.getEpochUnlock() == 0L) {
						subAddressJson.put("address", "prepared_unstakes");
					} else {
						subAddressJson.put("address", "exiting_unstakes");
					}
				}
			} else if (update.getParsed() instanceof ValidatorStakeData) {
				var validatorStakeData = (ValidatorStakeData) update.getParsed();
				addressIdentifier.put("address", addressing.forValidators().of(bucket.getValidatorKey()));
				operationJson
					.put("data", new JSONObject()
						.put("action", update.isBootUp() ? "CREATE" : "DELETE")
						.put("object", new JSONObject()
							.put("type", "ValidatorData")
							.put("owner", addressing.forAccounts().of(validatorStakeData.getOwnerAddr()))
							.put("registered", validatorStakeData.isRegistered())
							.put("fee", validatorStakeData.getRakePercentage())
						)
					);
			} else {
				throw new IllegalStateException("Unknown vault " + bucket);
			}

			if (!amount.equals(BigInteger.ZERO)) {
				operationJson.put("amount", new JSONObject()
					.put("value", amount.toString())
					.put("resource_identifier", resourceIdentifier)
				);
			}
			operationJson.put("address_identifier", addressIdentifier);
		} else {
			var objectJson = new JSONObject()
				.put("type", SUBSTATE_TYPE_ID_STRING_MAP.get(SubstateTypeId.valueOf(update.typeByte())));
			var dataJson = new JSONObject()
				.put("action", update.isBootUp() ? "CREATE" : "DELETE")
				.put("object", objectJson);
			operationJson.put("data", dataJson);

			if (update.getParsed() instanceof ResourceData) {
				var resourceData = (ResourceData) update.getParsed();

				// A bit of a super hack to get the rri
				var rri = tokenAddressToRri.apply(resourceData.getAddr());
				var addressIdentifierJson = new JSONObject().put("address", rri);
				operationJson.put("address_identifier", addressIdentifierJson);

				if (update.getParsed() instanceof TokenResource) {
					var tokenResource = (TokenResource) update.getParsed();
					objectJson
						.put("granularity", tokenResource.getGranularity().toString())
						.put("is_mutable", tokenResource.isMutable())
						.putOpt("owner", tokenResource.getOwner()
							.map(REAddr::ofPubKeyAccount)
							.map(addressing.forAccounts()::of)
							.orElse(null));
				} else if (update.getParsed() instanceof TokenResourceMetadata) {
					var metadata = (TokenResourceMetadata) update.getParsed();
					objectJson
						.put("symbol", metadata.getSymbol())
						.put("name", metadata.getName())
						.put("description", metadata.getDescription())
						.put("url", metadata.getUrl())
						.put("icon_url", metadata.getIconUrl());
				} else {
					throw new IllegalStateException("Unknown Resource Data " + update.getParsed());
				}
			} else if (update.getParsed() instanceof SystemData) {
				var addressIdentifierJson = new JSONObject().put("address", "system");
				operationJson.put("address_identifier", addressIdentifierJson);
				if (update.getParsed() instanceof EpochData) {
					var epochData = (EpochData) update.getParsed();
					objectJson.put("epoch", epochData.getEpoch());
				} else if (update.getParsed() instanceof RoundData) {
					var roundData = (RoundData) update.getParsed();
					objectJson
						.put("round", roundData.getView())
						.put("timestamp", roundData.getTimestamp());
				} else {
					throw new IllegalStateException("Unknown system data:  " + update.getParsed());
				}
			} else if (update.getParsed() instanceof ValidatorUpdatingData) {
				var validatorUpdatingData = (ValidatorUpdatingData) update.getParsed();
				var addressIdentifierJson = new JSONObject()
					.put("address", addressing.forValidators().of(validatorUpdatingData.getValidatorKey()));
				operationJson.put("address_identifier", addressIdentifierJson);
				validatorUpdatingData.getEpochUpdate().ifPresent(epochUpdate -> objectJson.put("epoch", epochUpdate));
				if (update.getParsed() instanceof ValidatorRegisteredCopy) {
					var preparedValidatorRegistered = (ValidatorRegisteredCopy) update.getParsed();
					objectJson.put("registered", preparedValidatorRegistered.isRegistered());
				} else if (update.getParsed() instanceof ValidatorOwnerCopy) {
					var preparedValidatorOwner = (ValidatorOwnerCopy) update.getParsed();
					objectJson.put("registered", addressing.forAccounts().of(preparedValidatorOwner.getOwner()));
				} else if (update.getParsed() instanceof ValidatorFeeCopy) {
					var preparedValidatorFee = (ValidatorFeeCopy) update.getParsed();
					objectJson.put("fee", preparedValidatorFee.getRakePercentage());
				} else {
					throw new IllegalStateException("Unknown validator updating data: " + update.getParsed());
				}
			} else if (update.getParsed() instanceof ValidatorData) {
				var validatorData = (ValidatorData) update.getParsed();
				var addressIdentifierJson = new JSONObject()
					.put("address", addressing.forValidators().of(validatorData.getValidatorKey()));
				operationJson.put("address_identifier", addressIdentifierJson);

				if (update.getParsed() instanceof ValidatorMetaData) {
					var validatorMetaData = (ValidatorMetaData) update.getParsed();
					objectJson
						.put("name", validatorMetaData.getName())
						.put("url", validatorMetaData.getUrl());
				} else if (update.getParsed() instanceof ValidatorBFTData) {
					var validatorBFTData = (ValidatorBFTData) update.getParsed();
					objectJson
						.put("proposals_completed", validatorBFTData.proposalsCompleted())
						.put("proposals_missed", validatorBFTData.proposalsMissed());
				} else if (update.getParsed() instanceof AllowDelegationFlag) {
					var allowDelegationFlag = (AllowDelegationFlag) update.getParsed();
					objectJson.put("allow_delegation", allowDelegationFlag.allowsDelegation());
				} else if (update.getParsed() instanceof ValidatorSystemMetadata) {
					var validatorSystemMetadata = (ValidatorSystemMetadata) update.getParsed();
					objectJson.put("data", Bytes.toHexString(validatorSystemMetadata.getData()));
				} else {
					throw new IllegalStateException("Unknown validator data " + update.getParsed());
				}
			} else {
				operationJson.put("address_identifier", new JSONObject()
					.put("address", "system")
				);
			}
		}

		return operationJson;
	}

	private Optional<JSONObject> inferAction(
		List<REStateUpdate> stateUpdates,
		Function<REAddr, String> addressToRri,
		Function<ECPublicKey, ValidatorStakeData> getValidatorStake
	) {
		var accounting = REResourceAccounting.compute(stateUpdates.stream());
		var entry = TwoActorEntry.parse(accounting.bucketAccounting());
		return entry.map(e -> mapToJSON(
			e,
			addressToRri,
			(k, ownership) -> {
				var stakeData = getValidatorStake.apply(k);
				return ownership.multiply(stakeData.getTotalStake()).divide(stakeData.getTotalOwnership());
			}
		));
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
					.put("to", addressing.forAccounts().of(toBucket.getOwner()))
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
}
