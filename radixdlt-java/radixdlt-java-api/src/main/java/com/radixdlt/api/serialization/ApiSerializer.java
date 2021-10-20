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

package com.radixdlt.api.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.inject.Inject;
import com.radixdlt.api.addressing.AccountAddress;
import com.radixdlt.api.addressing.NodeAddress;
import com.radixdlt.api.addressing.ValidatorAddress;
import com.radixdlt.api.serialization.serializer.AccountAddressDeserializer;
import com.radixdlt.api.serialization.serializer.AccountAddressSerializer;
import com.radixdlt.api.serialization.serializer.ECPublicKeyDeserializer;
import com.radixdlt.api.serialization.serializer.ECPublicKeySerializer;
import com.radixdlt.api.serialization.serializer.NodeAddressDeserializer;
import com.radixdlt.api.serialization.serializer.NodeAddressSerializer;
import com.radixdlt.api.serialization.serializer.ValidatorAddressDeserializer;
import com.radixdlt.api.serialization.serializer.ValidatorAddressSerializer;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.networks.Addressing;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;

public class ApiSerializer {
	//TODO: move to appropriate location
	private static final Failure UNABLE_TO_DESERIALIZE = Failure.failure(600, "Unable to deserialize: {0}");
	private static final Failure UNABLE_TO_SERIALIZE = Failure.failure(601, "Unable to serialize: {0}");

	private final ObjectMapper objectMapper = defaultObjectMapper();
	private Addressing addressing;

	@Inject
	public ApiSerializer(Addressing addressing) {
		setAddressing(addressing);
	}

	public ApiSerializer() {
	}

	public void setAddressing(Addressing addressing) {
		if (this.addressing != null) {
			throw new IllegalStateException("Attempt to set addressing more than once");
		}

		this.addressing = addressing;

		var module = new SimpleModule()
			.addSerializer(ValidatorAddress.class, new ValidatorAddressSerializer(addressing))
			.addSerializer(AccountAddress.class, new AccountAddressSerializer(addressing))
			.addSerializer(NodeAddress.class, new NodeAddressSerializer(addressing))
			.addSerializer(ECPublicKey.class, new ECPublicKeySerializer())
			.addDeserializer(AccountAddress.class, new AccountAddressDeserializer(addressing))
			.addDeserializer(ValidatorAddress.class, new ValidatorAddressDeserializer(addressing))
			.addDeserializer(NodeAddress.class, new NodeAddressDeserializer(addressing))
			.addDeserializer(ECPublicKey.class, new ECPublicKeyDeserializer());
		objectMapper.registerModule(module);
	}

	private ObjectMapper defaultObjectMapper() {
		return new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_ABSENT);
	}

	public Addressing getAddressing() {
		return addressing;
	}

	public Result<String> serialize(Object value) {
		return Result.wrap(UNABLE_TO_SERIALIZE, () -> objectMapper.writeValueAsString(value));
	}

	public <T> Result<T> deserialize(String body, TypeReference<T> typeReference) {
		return Result.wrap(UNABLE_TO_DESERIALIZE, () -> objectMapper.readValue(body, typeReference));
	}

	public <T> Result<T> deserialize(String body, Class<T> clazz) {
		return Result.wrap(UNABLE_TO_DESERIALIZE, () -> objectMapper.readValue(body, clazz));
	}
}
