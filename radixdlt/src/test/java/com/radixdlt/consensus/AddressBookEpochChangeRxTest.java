package com.radixdlt.consensus;

import static org.mockito.Mockito.mock;

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.network.addressbook.AddressBook;

import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;

public class AddressBookEpochChangeRxTest {
	@Test
	public void when_quorum_size_is_one__then_should_emit_self() {
		ECPublicKey self = mock(ECPublicKey.class);
		AddressBook addressBook = mock(AddressBook.class);
		AddressBookEpochChangeRx basicEpochRx = new AddressBookEpochChangeRx(self, addressBook, 1, mock(VertexMetadata.class));
		TestObserver<EpochChange> testObserver = basicEpochRx.epochChanges().test();
		testObserver.awaitCount(1);
		testObserver.assertValueCount(1);
		testObserver.assertValue(e -> e.getValidatorSet().getValidators().size() == 1);
		testObserver.assertValue(e -> e.getValidatorSet().getValidators().asList().get(0).nodeKey().equals(self));
		testObserver.assertNotComplete();
	}
}