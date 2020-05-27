/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radix.regression;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.core.Bootstrap;
import org.junit.Test;

public class ValidatorRegistrationTest {
	@Test
	public void when_registering_unregistering_and_reregistering_validator__then_validator_is_registererd() {
		// create a new public key identity
		final RadixIdentity radixIdentity = RadixIdentities.createNew();

		// initialize api layer
		RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.LOCALHOST, radixIdentity);

		// register for the first time
		api.registerValidator(api.getAddress()).blockUntilComplete();

		// unregister
		api.unregisterValidator(api.getAddress()).blockUntilComplete();

		// and re-register
		api.registerValidator(api.getAddress()).blockUntilComplete();
	}
}
