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

package com.radixdlt.api.core.developer;

import org.json.JSONObject;

import com.google.inject.Inject;
import com.radixdlt.application.tokens.Bucket;
import com.radixdlt.application.tokens.ResourceInBucket;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt384;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.radixdlt.api.util.JsonRpcUtil.fromCollection;
import static com.radixdlt.api.util.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.util.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.util.JsonRpcUtil.withRequiredParameters;

//FIXME: rework to avoid throwing exceptions and provide meaningful error codes/messages.
public final class DeveloperHandler {
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final BerkeleyLedgerEntryStore engineStore;
	private final Addressing addressing;

	@Inject
	public DeveloperHandler(
		RadixEngine<LedgerAndBFTProof> radixEngine,
		BerkeleyLedgerEntryStore engineStore,
		Addressing addressing
	) {
		this.radixEngine = radixEngine;
		this.addressing = addressing;
		this.engineStore = engineStore;
	}

	private Function<Bucket, String> getKeyMapper(String groupBy) {
		switch (groupBy) {
			case "resource":
				return b -> {
					if (b.resourceAddr() == null) {
						return "stake-ownership";
					}

					var key = SystemMapKey.ofResourceData(b.resourceAddr(), SubstateTypeId.TOKEN_RESOURCE_METADATA.id());
					var meta = (TokenResourceMetadata) radixEngine.get(key).orElseThrow();
					return addressing.forResources().of(meta.getSymbol(), b.resourceAddr());
				};
			case "owner":
				return b -> b.getOwner() == null ? "null" : addressing.forAccounts().of(b.getOwner());
			case "validator":
				return b -> b.getValidatorKey() == null ? "null" : addressing.forValidators().of(b.getValidatorKey());
			default:
				throw new IllegalArgumentException("Invalid groupBy: " + groupBy);
		}
	}

	private Predicate<Bucket> getBucketPredicate(String type, byte[] value) {
		switch (type) {
			case "resource":
				return b -> Arrays.equals(b.resourceAddr().getBytes(), value);
			case "owner":
				return b -> Arrays.equals(b.getOwner().getBytes(), value);
			case "validator":
				return b -> Arrays.equals(b.getValidatorKey().getCompressedBytes(), value);
			default:
				throw new IllegalArgumentException("Invalid value type: " + type);
		}
	}

	public JSONObject handleQueryResourceState(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("prefix"),
			params -> {
				var keyMapper = getKeyMapper(params.has("groupBy") ? params.getString("groupBy") : "resource");
				final Predicate<Bucket> bucketPredicate;
				if (params.has("query")) {
					var query = params.getJSONObject("query");
					var type = query.getString("type");
					var value = Bytes.fromHexString(query.getString("value"));
					bucketPredicate = getBucketPredicate(type, value);
				} else {
					bucketPredicate = b -> true;
				}

				var hex = params.getString("prefix");
				var prefix = Bytes.fromHexString(hex);
				var index = SubstateIndex.create(prefix);
				if (!ResourceInBucket.class.isAssignableFrom(index.getSubstateClass())) {
					throw new IllegalArgumentException("Invalid resource index " + index.getSubstateClass());
				}
				var resultJson = jsonArray();
				@SuppressWarnings("unchecked")
				var map = radixEngine.reduceResourcesWithSubstateCount(
					(SubstateIndex<ResourceInBucket>) index,
					r -> keyMapper.apply(r.bucket()),
					r -> bucketPredicate.test(r.bucket())
				);
				map.entrySet().stream()
					.sorted(Comparator.<Map.Entry<String, Pair<UInt384, Long>>, UInt384>comparing(e -> e.getValue().getFirst()).reversed())
					.forEach(e -> resultJson.put(
						jsonObject()
							.put("key", e.getKey())
							.put("amount", e.getValue().getFirst())
							.put("substateCount", e.getValue().getSecond()))
					);
				var totalSubstateCount = map.values().stream().mapToLong(Pair::getSecond).sum();
				var totalAmount = map.values().stream().map(Pair::getFirst).reduce(UInt384::add).orElse(UInt384.ZERO);
				return Result.ok(
					jsonObject()
						.put("entries", resultJson)
						.put("entryCount", map.size())
						.put("totalSubstateCount", totalSubstateCount)
						.put("totalAmount", totalAmount)
				);
			}
		);
	}

	public JSONObject handleLookupMappedSubstate(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("key"),
			params -> {
				var key = Bytes.fromHexString(params.getString("key"));
				var systemMapKey = SystemMapKey.create(key);
				var bytes = engineStore.get(systemMapKey).orElseThrow();
				return Result.ok(
					jsonObject()
						.put("id", Bytes.toHexString(bytes.getId()))
						.put("data", Bytes.toHexString(bytes.getData()))
				);
			}
		);
	}

	public JSONObject handleScanSubstates(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("keyHexRegex", "valueHexRegex"),
			params -> {
				var keyHexRegex = params.getString("keyHexRegex");
				var keyPattern = Pattern.compile(keyHexRegex).asMatchPredicate();
				var valueHexRegex = params.getString("valueHexRegex");
				var limit = params.has("loadLimit") ? params.getLong("loadLimit") : 8;
				var valuePattern = Pattern.compile(valueHexRegex).asMatchPredicate();
				var found = jsonArray();
				var totalKeySize = new AtomicLong();
				var totalValueSize = new AtomicLong();
				var stream = engineStore.scanner()
					.map(r -> Pair.of(Bytes.toHexString(r.getId()), Bytes.toHexString(r.getData())))
					.filter(p -> keyPattern.test(p.getFirst()) && valuePattern.test(p.getSecond()))
					.peek(p -> {
						if (found.length() < limit) {
							found.put(p.getFirst() + ":" + p.getSecond());
						}
						totalKeySize.getAndAdd(p.getFirst().length() / 2);
						totalValueSize.getAndAdd(p.getSecond().length() / 2);
					});
				var countByGroup = stream
					.collect(
						Collectors.groupingBy(
							p -> p.getSecond().length() > 0 ? p.getSecond().substring(0, 2) : "virtual-down",
							Collectors.counting()
						)
					);
				stream.close();

				var countByGroupJson = jsonObject();
				countByGroup.forEach(countByGroupJson::put);

				return Result.ok(
					jsonObject()
						.put("loaded", found)
						.put("countBySubstateType", countByGroupJson)
						.put("totalIdSize", totalKeySize.get())
						.put("totalDataSize", totalValueSize.get())
						.put("totalSize", totalKeySize.get() + totalValueSize.get())
						.put("totalCount", countByGroup.values().stream().mapToLong(l -> l).sum())
				);
			}
		);
	}

	public JSONObject handleParseTxn(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("txn"),
			params -> Result.wrap(
				DeveloperHandler::toFailure,
				() -> {
					var parser = radixEngine.getParser();
					var txnHex = params.getString("txn");
					var txn = Txn.create(Bytes.fromHexString(txnHex));
					var result = parser.parse(txn);

					var instructions = fromCollection(result.instructions(), i -> {
						var buf = i.getDataByteBuffer();

						return jsonObject()
							.put("op", i.getMicroOp().toString())
							.put("data", i.getData() == null ? null : i.getData().toString())
							.put("data_raw", Bytes.toHexString(buf.array(), buf.position(), i.getDataLength()))
							.put("data_size", i.getDataLength());
					});

					return jsonObject()
						.put("txId", txn.getId().toJson())
						.put("size", txn.getPayload().length)
						.put("instructions", instructions);
				}
			)
		);
	}

	public JSONObject handleParseSubstate(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("data"),
			params -> Result.wrap(
				DeveloperHandler::toFailure,
				() -> {
					var data = Bytes.fromHexString(params.getString("data"));
					var deserialization = radixEngine.getSubstateDeserialization();
					var substate = deserialization.deserialize(data);
					return jsonObject()
						.put("parsed", substate.toString());
				}
			)
		);
	}

	private static Failure toFailure(Throwable e) {
		return Failure.failure(-1, e.getMessage());
	}
}
