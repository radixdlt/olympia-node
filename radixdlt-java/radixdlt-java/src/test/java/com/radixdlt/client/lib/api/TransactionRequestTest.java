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
package com.radixdlt.client.lib.api;

import org.junit.Test;

import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.UInt256;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TransactionRequestTest {
	@Test
	public void transferCanBeBuilt() throws DeserializeException {
		var from = AccountAddress.create("brx1qspll7tm6464am4yypzn59p42g6a8qhkguhc269p3vhs27s5vq5h24sh5s4yh");
		var to = AccountAddress.create("brx1qspa0q22j6kwjdmmax5yvc046t575xfve6lgarl2ja5hhlwprmcvlcg8k98kp");
		var rri = "emunie_rb1q0amnvsa09rxfz83xny849cyg39v3qu9taxcra5p7hxqnn6afk";

		var request = TransactionRequest.createBuilder()
			.transfer(from, to, UInt256.EIGHT, rri).build();

		assertEquals(1, request.getActions().size());

		var action = request.getActions().get(0);

		assertEquals(ActionType.TRANSFER, action.getType());

		assertTrue(action.getAmount().isPresent());
		assertTrue(action.getFrom().isPresent());
		assertTrue(action.getTo().isPresent());
		assertTrue(action.getRri().isPresent());

		assertEquals(UInt256.EIGHT, action.getAmount().get());
		assertEquals(from, action.getFrom().get());
		assertEquals(to, action.getTo().get());
		assertEquals(rri, action.getRri().get());
	}

	@Test
	public void stakeCanBeBuilt() throws DeserializeException {
		var from = AccountAddress.create("brx1qspll7tm6464am4yypzn59p42g6a8qhkguhc269p3vhs27s5vq5h24sh5s4yh");
		var validator = ValidatorAddress.create("vb1q27acjcz0vs0dg9mwv7nwyxfxu28rcvu35zwcnn9ulul25ss3kfgkue7d6p");

		var request = TransactionRequest.createBuilder()
			.stake(from, validator, UInt256.SEVEN).build();

		assertEquals(1, request.getActions().size());

		var action = request.getActions().get(0);

		assertEquals(ActionType.STAKE, action.getType());

		assertTrue(action.getAmount().isPresent());
		assertTrue(action.getFrom().isPresent());
		assertTrue(action.getValidator().isPresent());

		assertEquals(UInt256.SEVEN, action.getAmount().get());
		assertEquals(from, action.getFrom().get());
		assertEquals(validator, action.getValidator().get());
	}

	@Test
	public void unStakeCanBeBuilt() throws DeserializeException {
		var from = AccountAddress.create("brx1qspll7tm6464am4yypzn59p42g6a8qhkguhc269p3vhs27s5vq5h24sh5s4yh");
		var validator = ValidatorAddress.create("vb1q27acjcz0vs0dg9mwv7nwyxfxu28rcvu35zwcnn9ulul25ss3kfgkue7d6p");

		var request = TransactionRequest.createBuilder()
			.unstake(from, validator, UInt256.SIX).build();

		assertEquals(1, request.getActions().size());

		var action = request.getActions().get(0);

		assertEquals(ActionType.UNSTAKE, action.getType());

		assertTrue(action.getAmount().isPresent());
		assertTrue(action.getFrom().isPresent());
		assertTrue(action.getValidator().isPresent());

		assertEquals(UInt256.SIX, action.getAmount().get());
		assertEquals(from, action.getFrom().get());
		assertEquals(validator, action.getValidator().get());
	}

	@Test
	public void actionMayHaveMessage() throws DeserializeException {
		var from = AccountAddress.create("brx1qspll7tm6464am4yypzn59p42g6a8qhkguhc269p3vhs27s5vq5h24sh5s4yh");
		var to = AccountAddress.create("brx1qspa0q22j6kwjdmmax5yvc046t575xfve6lgarl2ja5hhlwprmcvlcg8k98kp");
		var rri = "emunie_rb1q0amnvsa09rxfz83xny849cyg39v3qu9taxcra5p7hxqnn6afk";

		var request = TransactionRequest.createBuilder()
			.transfer(from, to, UInt256.EIGHT, rri)
			.message("Text")
			.build();

		assertEquals("Text", request.getMessage());
		assertEquals(1, request.getActions().size());

		var action = request.getActions().get(0);

		assertEquals(ActionType.TRANSFER, action.getType());

		assertTrue(action.getAmount().isPresent());
		assertTrue(action.getFrom().isPresent());
		assertTrue(action.getTo().isPresent());
		assertTrue(action.getRri().isPresent());

		assertEquals(UInt256.EIGHT, action.getAmount().get());
		assertEquals(from, action.getFrom().get());
		assertEquals(to, action.getTo().get());
		assertEquals(rri, action.getRri().get());
	}
}