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

package org.radix.api.services;

import com.google.inject.Inject;
import com.radixdlt.client.store.ClientApiStore;
import com.radixdlt.client.store.TokenBalance;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.functional.Result;

import java.util.List;

public class HighLevelApiService {
	private final Universe universe;
	private final RadixAddress radixAddress;
	private final ClientApiStore clientApiStore;

	@Inject
	public HighLevelApiService(Universe universe, @Self RadixAddress radixAddress, ClientApiStore clientApiStore) {
		this.universe = universe;
		this.radixAddress = radixAddress;
		this.clientApiStore = clientApiStore;
	}

	public int getUniverseMagic() {
		return universe.getMagic();
	}

	public RadixAddress getAddress() {
		return radixAddress;
	}

	public Result<List<TokenBalance>> getTokenBalances(RadixAddress radixAddress) {
		return clientApiStore.getTokenBalances(radixAddress);
	}
}
