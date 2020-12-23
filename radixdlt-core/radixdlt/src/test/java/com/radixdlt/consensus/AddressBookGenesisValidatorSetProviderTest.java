/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.consensus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Observable;

import com.google.common.collect.ImmutableList;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.PeerWithSystem;

import java.util.stream.Stream;
import org.assertj.core.api.Condition;
import org.junit.Test;
import org.radix.universe.system.RadixSystem;

public class AddressBookGenesisValidatorSetProviderTest {
	@Test
	public void when_quorum_size_is_one__then_should_emit_self() {
		ECPublicKey self = mock(ECPublicKey.class);
		when(self.euid()).thenReturn(EUID.ONE);
		AddressBook addressBook = mock(AddressBook.class);
		when(addressBook.peerUpdates()).thenReturn(Observable.just(() -> ImmutableList.of()));
		GenesisValidatorSetProvider validatorSetProvider = new GenesisValidatorSetFromAddressBook(
			self,
			addressBook,
			1
		);
		PeerWithSystem peer = mock(PeerWithSystem.class);
		RadixSystem system = mock(RadixSystem.class);
		ECPublicKey peerKey = mock(ECPublicKey.class);
		when(system.getKey()).thenReturn(peerKey);
		when(peer.getSystem()).thenReturn(system);
		when(addressBook.peers()).thenAnswer(inv -> Stream.of(peer));
		assertThat(validatorSetProvider.genesisValidatorSet().getValidators())
			.haveExactly(1, new Condition<>(v -> v.getNode().getKey().equals(self), "validator with self key %s", self));
	}
}