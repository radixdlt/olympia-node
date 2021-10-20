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

package com.radixdlt.client.lib.addressing;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.radixdlt.api.addressing.AccountAddress;
import com.radixdlt.api.addressing.NodeAddress;
import com.radixdlt.api.addressing.ValidatorAddress;
import com.radixdlt.api.dto.response.NetworkPeer;
import com.radixdlt.api.serialization.serializer.AccountAddressDeserializer;
import com.radixdlt.api.serialization.serializer.AccountAddressSerializer;
import com.radixdlt.api.serialization.serializer.NodeAddressDeserializer;
import com.radixdlt.api.serialization.serializer.NodeAddressSerializer;
import com.radixdlt.api.serialization.serializer.ValidatorAddressDeserializer;
import com.radixdlt.api.serialization.serializer.ValidatorAddressSerializer;
import com.radixdlt.client.lib.api.action.StakeAction;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.networks.Addressing;
import com.radixdlt.utils.UInt256;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class AddressSerializationTest {
	@Test
	public void variousTypesOfAddressesSerializedAndDeserializedCorrectly() throws JsonProcessingException {
		var keyPair1 = ECKeyPair.generateNew();
		var keyPair2 = ECKeyPair.generateNew();
		var keyPair3 = ECKeyPair.generateNew();

		var accountAddress = AccountAddress.create(keyPair1.getPublicKey());
		var validatorAddress = ValidatorAddress.of(keyPair2.getPublicKey());
		var nodeAddress = NodeAddress.of(keyPair3.getPublicKey());

		var action = new StakeAction(accountAddress, validatorAddress, UInt256.EIGHT);
		var peerDetails = NetworkPeer.create(nodeAddress, List.of());

		var module = new SimpleModule();
		int networkId = 1;
		var networkAddressing = Addressing.ofNetworkId(networkId);

		module.addSerializer(AccountAddress.class, new AccountAddressSerializer(networkAddressing));
		module.addSerializer(ValidatorAddress.class, new ValidatorAddressSerializer(networkAddressing));
		module.addSerializer(NodeAddress.class, new NodeAddressSerializer(networkAddressing));
		module.addDeserializer(AccountAddress.class, new AccountAddressDeserializer(networkAddressing));
		module.addDeserializer(ValidatorAddress.class, new ValidatorAddressDeserializer(networkAddressing));
		module.addDeserializer(NodeAddress.class, new NodeAddressDeserializer(networkAddressing));

		var objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
			.registerModule(module);

		var result = objectMapper.writeValueAsString(action);
		assertEquals(action.toJSON(networkId), result);

		var restored = objectMapper.readValue(result, StakeAction.class);
		assertEquals(action, restored);

		var peerResult = objectMapper.writeValueAsString(peerDetails);
		assertEquals(String.format("{\"address\":\"%s\",\"channels\":[]}", nodeAddress.toString(networkId)), peerResult);

		var restoredPeer = objectMapper.readValue(peerResult, NetworkPeer.class);
		assertEquals(nodeAddress, restoredPeer.getAddress());
	}
}
