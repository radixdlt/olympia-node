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

package com.radixdlt.api.core.address;

import com.google.inject.Inject;
import com.radixdlt.api.archive.ApiHandler;
import com.radixdlt.api.archive.InvalidParametersException;
import com.radixdlt.api.archive.JsonObjectReader;
import com.radixdlt.api.core.construction.AddressIdentifier;
import com.radixdlt.api.core.construction.KeyQuery;
import com.radixdlt.api.core.construction.ResourceQuery;
import com.radixdlt.api.service.transactions.ProcessedTxnJsonConverter;
import com.radixdlt.application.tokens.Bucket;
import com.radixdlt.application.tokens.ResourceInBucket;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.function.Function;

public class AddressHandler implements ApiHandler<AddressIdentifier> {
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final Addressing addressing;
	private final ProcessedTxnJsonConverter converter;

	@Inject
	AddressHandler(
		RadixEngine<LedgerAndBFTProof> radixEngine,
		ProcessedTxnJsonConverter converter,
		Addressing addressing
	) {
		this.radixEngine = radixEngine;
		this.converter = converter;
		this.addressing = addressing;
	}

	@Override
	public Addressing addressing() {
		return addressing;
	}

	@Override
	public AddressIdentifier parseRequest(JsonObjectReader requestReader) throws InvalidParametersException {
		return requestReader.getJsonObject("address_identifier", AddressIdentifier::from);
	}

	private JSONObject bucketToResourceJson(Bucket bucket, Function<REAddr, String> addressToRri) {
		if (bucket.resourceAddr() != null) {
			return new JSONObject()
				.put("type", "token")
				.put("rri", addressToRri.apply(bucket.resourceAddr())
			);
		}

		return new JSONObject()
			.put("type", "stake_ownership")
			.put("validator", addressing.forValidators().of(bucket.getValidatorKey()));
	}

	private JSONArray getBalances(
		List<ResourceQuery> resourceQueries,
		Function<REAddr, String> addressToRri
	) {
		var balances = new JSONArray();
		for (var resourceQuery : resourceQueries) {
			var index = resourceQuery.getIndex();
			var bucketPredicate = resourceQuery.getPredicate();
			radixEngine.reduceResources(index, ResourceInBucket::bucket, bucketPredicate)
				.forEach((bucket, amount) -> {
					var json = new JSONObject()
						.put("resource_identifier", bucketToResourceJson(bucket, addressToRri))
						.put("value", amount.toString());
					balances.put(json);
				});
		}
		return balances;
	}

	private JSONArray getObjects(
		List<KeyQuery> keyQueries
	) {
		var objects = new JSONArray();
		for (var keyQuery : keyQueries) {
			var substate = radixEngine.get(keyQuery.getKey()).or(keyQuery.getVirtualSubstate());
			substate.map(s -> converter.getDataObject(keyQuery.getTypeId(), s)).ifPresent(objects::put);
		}
		return objects;
	}

	@Override
	public JSONObject handleRequest(AddressIdentifier addressIdentifier) throws Exception {
		Function<REAddr, String> addressToRri = addr -> {
			var mapKey = SystemMapKey.ofResourceData(addr, SubstateTypeId.TOKEN_RESOURCE_METADATA.id());
			var substate = radixEngine.get(mapKey).orElseThrow();
			var tokenResource = (TokenResourceMetadata) substate;
			return addressing.forResources().of(tokenResource.getSymbol(), addr);
		};

		// TODO: need to fetch these in a single database transaction and retrieve version as well
		var balances = getBalances(
			addressIdentifier.getResourceQueries(),
			addressToRri
		);
		var objects = getObjects(addressIdentifier.getKeyQueries());

		return new JSONObject()
			.put("balances", balances)
			.put("data_objects", objects);
	}
}
