/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.client.lib.addressing;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.client.lib.api.action.StakeAction;
import com.radixdlt.client.lib.dto.serializer.AccountAddressDeserializer;
import com.radixdlt.client.lib.dto.serializer.AccountAddressSerializer;
import com.radixdlt.client.lib.dto.serializer.ValidatorAddressDeserializer;
import com.radixdlt.client.lib.dto.serializer.ValidatorAddressSerializer;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.utils.UInt256;

import static org.junit.Assert.assertEquals;

public class AddressSerializationTest {
	@Test
	public void name() throws JsonProcessingException {
		var keyPair1 = ECKeyPair.generateNew();
		var keyPair2 = ECKeyPair.generateNew();

		var accountAddress = AccountAddress.create(keyPair1.getPublicKey());
		var validatorAddress = ValidatorAddress.of(keyPair2.getPublicKey());

		var action = new StakeAction(accountAddress, validatorAddress, UInt256.EIGHT);

		var module = new SimpleModule();
		int networkId = 1;

		module.addSerializer(AccountAddress.class, new AccountAddressSerializer(networkId));
		module.addDeserializer(AccountAddress.class, new AccountAddressDeserializer(networkId));
		module.addSerializer(ValidatorAddress.class, new ValidatorAddressSerializer(networkId));
		module.addDeserializer(ValidatorAddress.class, new ValidatorAddressDeserializer(networkId));

		var objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
			.registerModule(module);

		var result = objectMapper.writeValueAsString(action);
		assertEquals(action.toJSON(networkId), result);

		var restored = objectMapper.readValue(result, StakeAction.class);
		assertEquals(action, restored);
	}
}
