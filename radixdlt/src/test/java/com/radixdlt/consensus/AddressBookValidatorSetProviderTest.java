package com.radixdlt.consensus;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.network.addressbook.AddressBook;

import com.radixdlt.network.addressbook.Peer;
import java.util.stream.Stream;
import org.junit.Test;
import org.radix.universe.system.RadixSystem;

public class AddressBookValidatorSetProviderTest {
	@Test
	public void when_quorum_size_is_one__then_should_emit_self() {
		ECPublicKey self = mock(ECPublicKey.class);
		AddressBook addressBook = mock(AddressBook.class);
		AddressBookValidatorSetProvider validatorSetProvider = new AddressBookValidatorSetProvider(self, addressBook, 1);
		Peer peer = mock(Peer.class);
		RadixSystem system = mock(RadixSystem.class);
		ECPublicKey peerKey = mock(ECPublicKey.class);
		when(system.getKey()).thenReturn(peerKey);
		when(peer.getSystem()).thenReturn(system);
		when(addressBook.peers()).thenReturn(Stream.of(peer), Stream.of(peer));
		ValidatorSet validatorSet = validatorSetProvider.getValidatorSet(0);
		assertThat(validatorSet.getValidators()).hasSize(1);
		assertThat(validatorSet.getValidators()).allMatch(v -> v.nodeKey().equals(self));
	}
}