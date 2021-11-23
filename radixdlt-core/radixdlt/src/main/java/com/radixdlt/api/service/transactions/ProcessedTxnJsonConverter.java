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
import com.radixdlt.api.gateway.transaction.ActionType;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.RoundData;
import com.radixdlt.application.system.state.StakeOwnershipBucket;
import com.radixdlt.application.system.state.SystemData;
import com.radixdlt.application.system.state.UnclaimedREAddr;
import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.system.state.VirtualParent;
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
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.radixdlt.atom.SubstateTypeId.*;

public final class ProcessedTxnJsonConverter {
	private final Addressing addressing;
	private final Provider<RadixEngine<LedgerAndBFTProof>> radixEngineProvider;

	@Inject
	ProcessedTxnJsonConverter(
		Provider<RadixEngine<LedgerAndBFTProof>> radixEngineProvider,
		Addressing addressing
	) {
		this.radixEngineProvider = radixEngineProvider;
		this.addressing = addressing;
	}

	public JSONObject getTransaction(REProcessedTxn processed) {
		Function<REAddr, String> addressToRri = addr -> {
			var localMetadata = processed.getGroupedStateUpdates().stream()
				.flatMap(List::stream)
				.map(REStateUpdate::getParsed)
				.filter(TokenResourceMetadata.class::isInstance)
				.map(TokenResourceMetadata.class::cast)
				.filter(r -> r.getAddr().equals(addr))
				.findFirst();

			var tokenMetadata = localMetadata.orElseGet(() -> {
				var mapKey = SystemMapKey.ofResourceData(addr, SubstateTypeId.TOKEN_RESOURCE_METADATA.id());
				var substate = radixEngineProvider.get().get(mapKey).orElseThrow();
				// TODO: This is a bit of a hack to require deserialization, figure out correct abstraction
				return (TokenResourceMetadata) substate;
			});

			return addressing.forResources().of(tokenMetadata.getSymbol(), addr);
		};

		var operationGroups = this.getOperationGroups(processed, addressToRri, null);

		JSONObject metadata;
		if (!processed.getFeePaid().isZero()) {
			var rri = addressToRri.apply(REAddr.ofNativeToken());
			metadata = new JSONObject()
				.put("fee", new JSONObject()
					.put("resource_identifier", new JSONObject()
						.put("rri", rri)
						.put("type", "Token")
					)
					.put("value", processed.getFeePaid().toString())
				);
		} else {
			metadata = null;
		}

		return new JSONObject()
			.putOpt("metadata", metadata)
			.put("operation_groups", operationGroups);
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

	public JSONObject getDataObject(SubstateTypeId typeId, Particle substate) {
		var objectJson = new JSONObject()
			.put("type", SubstateTypeMapping.getName(typeId));
		if (substate instanceof ResourceData) {
			var resourceData = (ResourceData) substate;
			if (resourceData instanceof TokenResource) {
				var tokenResource = (TokenResource) resourceData;
				objectJson
					.put("granularity", tokenResource.getGranularity().toString())
					.put("is_mutable", tokenResource.isMutable())
					.putOpt("owner", tokenResource.getOwner()
						.map(REAddr::ofPubKeyAccount)
						.map(addressing.forAccounts()::of)
						.orElse(null));
			} else if (substate instanceof TokenResourceMetadata) {
				var metadata = (TokenResourceMetadata) substate;
				objectJson
					.put("symbol", metadata.getSymbol())
					.put("name", metadata.getName())
					.put("description", metadata.getDescription())
					.put("url", metadata.getUrl())
					.put("icon_url", metadata.getIconUrl());
			} else {
				throw new IllegalStateException("Unknown Resource Data " + substate);
			}
		} else if (substate instanceof SystemData) {
			if (substate instanceof EpochData) {
				var epochData = (EpochData) substate;
				objectJson.put("epoch", epochData.getEpoch());
			} else if (substate instanceof RoundData) {
				var roundData = (RoundData) substate;
				objectJson
					.put("round", roundData.getView())
					.put("timestamp", roundData.getTimestamp());
			} else {
				throw new IllegalStateException("Unknown system data:  " + substate);
			}
		} else if (substate instanceof ValidatorUpdatingData) {
			var validatorUpdatingData = (ValidatorUpdatingData) substate;
			validatorUpdatingData.getEpochUpdate().ifPresent(epochUpdate -> objectJson.put("epoch", epochUpdate));
			if (substate instanceof ValidatorRegisteredCopy) {
				var preparedValidatorRegistered = (ValidatorRegisteredCopy) substate;
				objectJson.put("registered", preparedValidatorRegistered.isRegistered());
			} else if (substate instanceof ValidatorOwnerCopy) {
				var preparedValidatorOwner = (ValidatorOwnerCopy) substate;
				objectJson.put("owner", addressing.forAccounts().of(preparedValidatorOwner.getOwner()));
			} else if (substate instanceof ValidatorFeeCopy) {
				var preparedValidatorFee = (ValidatorFeeCopy) substate;
				objectJson.put("fee", preparedValidatorFee.getRakePercentage());
			} else {
				throw new IllegalStateException("Unknown validator updating data: " + substate);
			}
		} else if (substate instanceof ValidatorData) {
			if (substate instanceof ValidatorMetaData) {
				var validatorMetaData = (ValidatorMetaData) substate;
				objectJson.put("name", validatorMetaData.getName());
				objectJson.put("url", validatorMetaData.getUrl());
			} else if (substate instanceof ValidatorBFTData) {
				var validatorBFTData = (ValidatorBFTData) substate;
				objectJson.put("proposals_completed", validatorBFTData.proposalsCompleted());
				objectJson.put("proposals_missed", validatorBFTData.proposalsMissed());
			} else if (substate instanceof AllowDelegationFlag) {
				var allowDelegationFlag = (AllowDelegationFlag) substate;
				objectJson.put("allow_delegation", allowDelegationFlag.allowsDelegation());
			} else if (substate instanceof ValidatorSystemMetadata) {
				var validatorSystemMetadata = (ValidatorSystemMetadata) substate;
				objectJson.put("data", Bytes.toHexString(validatorSystemMetadata.getData()));
			} else if (substate instanceof ValidatorStakeData) {
				var validatorStakeData = (ValidatorStakeData) substate;
				objectJson.put("owner", addressing.forAccounts().of(validatorStakeData.getOwnerAddr()));
				objectJson.put("registered", validatorStakeData.isRegistered());
				objectJson.put("fee", validatorStakeData.getRakePercentage());
			} else {
				throw new IllegalStateException("Unknown validator data " + substate);
			}
		} else if (substate instanceof VirtualParent) {
			var virtualParent = (VirtualParent) substate;
			var childType = SubstateTypeId.valueOf(virtualParent.getData()[0]);
			objectJson.put("child_type", SubstateTypeMapping.getName(childType));
		}

		return objectJson;
	}

	private JSONObject getOperation(
		REStateUpdate update,
		Function<REAddr, String> tokenAddressToRri
	) {
		var substateId = update.getId();
		var operationJson = new JSONObject()
			.put("type", SubstateTypeMapping.getType(SubstateTypeId.valueOf(update.typeByte())))
			.put("substate", new JSONObject()
				.put("substate_identifier", new JSONObject()
					.put("identifier", Bytes.toHexString(substateId.asBytes()))
				)
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

			final JSONObject entityIdentifier = new JSONObject();
			if (bucket.getOwner() != null) {
				entityIdentifier.put("address", addressing.forAccounts().of(bucket.getOwner()));
				if (bucket.getValidatorKey() != null) {
					if (bucket.resourceAddr() != null && bucket.getEpochUnlock() == null) {
						var subEntityJson = new JSONObject();
						entityIdentifier.put("sub_entity", subEntityJson);
						subEntityJson
							.put("address", "prepared_stakes")
							.put("metadata", new JSONObject()
								.put("validator", addressing.forValidators().of(bucket.getValidatorKey()))
							);
					} else if (bucket.resourceAddr() == null && Objects.equals(bucket.getEpochUnlock(), 0L)) {
						var subEntityJson = new JSONObject();
						entityIdentifier.put("sub_entity", subEntityJson);
						// Don't add validator as validator is already part of resource
						subEntityJson.put("address", "prepared_unstakes");
					} else if (bucket.resourceAddr() != null && bucket.getEpochUnlock() != null) {
						var subEntityJson = new JSONObject();
						entityIdentifier.put("sub_entity", subEntityJson);
						subEntityJson.put("address", "exiting_unstakes")
							.put("metadata", new JSONObject()
								.put("validator", addressing.forValidators().of(bucket.getValidatorKey()))
								.put("epoch_unlock", bucket.getEpochUnlock())
							);
					}
				}
			} else if (update.getParsed() instanceof ValidatorStakeData) {
				var validatorStakeData = (ValidatorStakeData) update.getParsed();
				entityIdentifier.put("address", addressing.forValidators().of(bucket.getValidatorKey()));
				entityIdentifier.put("sub_entity", new JSONObject()
					.put("address", "system")
				);

				operationJson
					.put("data", new JSONObject()
						.put("action", update.isBootUp() ? "CREATE" : "DELETE")
						.put("data_object", getDataObject(VALIDATOR_STAKE_DATA, validatorStakeData))
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
			operationJson.put("entity_identifier", entityIdentifier);
		} else {

			var dataObject = getDataObject(SubstateTypeId.valueOf(update.typeByte()), (Particle) update.getParsed());
			var dataJson = new JSONObject()
				.put("action", update.isBootUp() ? "CREATE" : "DELETE")
				.put("data_object", dataObject);
			operationJson.put("data", dataJson);

			if (update.getParsed() instanceof ResourceData) {
				// A bit of a super hack to get the rri
				var resourceData = (ResourceData) update.getParsed();
				var rri = tokenAddressToRri.apply(resourceData.getAddr());
				var entityIdentifierJson = new JSONObject().put("address", rri);
				operationJson.put("entity_identifier", entityIdentifierJson);
			} else if (update.getParsed() instanceof SystemData) {
				var entityIdentifierJson = new JSONObject().put("address", "system");
				operationJson.put("entity_identifier", entityIdentifierJson);
			} else if (update.getParsed() instanceof ValidatorUpdatingData) {
				var validatorUpdatingData = (ValidatorUpdatingData) update.getParsed();
				var entityIdentifierJson = new JSONObject()
					.put("address", addressing.forValidators().of(validatorUpdatingData.getValidatorKey()));
				operationJson.put("entity_identifier", entityIdentifierJson);
			} else if (update.getParsed() instanceof ValidatorData) {
				var validatorData = (ValidatorData) update.getParsed();
				var entityIdentifierJson = new JSONObject()
					.put("address", addressing.forValidators().of(validatorData.getValidatorKey()));
				if (validatorData instanceof ValidatorBFTData) {
					entityIdentifierJson.put("sub_entity", new JSONObject()
						.put("address", "system")
					);
				}
				operationJson.put("entity_identifier", entityIdentifierJson);
			} else if (update.getParsed() instanceof UnclaimedREAddr) {
				var unclaimedREAddr = (UnclaimedREAddr) update.getParsed();
				var addr = unclaimedREAddr.getAddr();
				final JSONObject entityIdentifierJson;
				if (!addr.isSystem()) {
					var rri = tokenAddressToRri.apply(unclaimedREAddr.getAddr());
					entityIdentifierJson = new JSONObject().put("address", rri);
				} else {
					entityIdentifierJson = new JSONObject().put("address", "system");
				}
				operationJson.put("entity_identifier", entityIdentifierJson);
			} else if (update.getParsed() instanceof VirtualParent) {
				operationJson.put("entity_identifier", new JSONObject()
					.put("address", "system")
				);
			} else {
				throw new IllegalStateException();
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
		var amount = UInt256.from(amtByteArray);
		var from = entry.from();
		var to = entry.to();
		var result = new JSONObject();
		if (from.isEmpty()) {
			var toBucket = to.orElseThrow();
			if (!(toBucket instanceof AccountBucket)) {
				throw new IllegalStateException();
			}
			result.put("to", new JSONObject()
				.put("address", addressing.forAccounts().of(toBucket.getOwner())));
			result.put("type", ActionType.MINT.toString());
		} else if (to.isEmpty()) {
			result.put("from", new JSONObject()
				.put("address", addressing.forAccounts().of(from.get().getOwner())));
			result.put("type", ActionType.BURN.toString());
		} else {
			var fromBucket = from.get();
			var toBucket = to.get();
			if (fromBucket instanceof AccountBucket) {
				if (toBucket instanceof AccountBucket) {
					result
						.put("from", new JSONObject()
							.put("address", addressing.forAccounts().of(fromBucket.getOwner())))
						.put("to", new JSONObject()
							.put("address", addressing.forAccounts().of(toBucket.getOwner())))
						.put("type", ActionType.TRANSFER.toString());
				} else {
					result
						.put("from", new JSONObject()
							.put("address", addressing.forAccounts().of(fromBucket.getOwner())))
						.put("to", new JSONObject()
							.put("address", addressing.forValidators().of(toBucket.getValidatorKey())))
						.put("type", ActionType.STAKE.toString());
				}
			} else if (fromBucket instanceof StakeOwnershipBucket) {
				amount = computeStakeFromOwnership.apply(fromBucket.getValidatorKey(), UInt384.from(amount)).getLow();
				result
					.put("to", new JSONObject()
						.put("address", addressing.forAccounts().of(toBucket.getOwner())))
					.put("from", new JSONObject()
						.put("address", addressing.forValidators().of(fromBucket.getValidatorKey())))
					.put("type", ActionType.UNSTAKE.toString());
			} else {
				throw new IllegalStateException();
			}
		}

		return result
			.put("amount", new JSONObject()
				.put("value", amount.toString())
				.put("rri", addrToRri.apply(entry.resourceAddr().orElse(REAddr.ofNativeToken())))
			);
	}
}
