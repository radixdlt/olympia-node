package com.radixdlt.consensus;

import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.network.addressbook.AddressBook;

import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;

public class BasicNextValidatorSetRxTest {
	@Test
	public void when_quorum_size_is_one__then_should_emit_self() {
		ECPublicKey self = mock(ECPublicKey.class);
		AddressBook addressBook = mock(AddressBook.class);
		BasicNextValidatorSetRx basicEpochRx = new BasicNextValidatorSetRx(self, addressBook, 1);
		TestObserver<ValidatorSet> testObserver = TestObserver.create();
		basicEpochRx.nextValidatorSet()
			.subscribe(testObserver);
		testObserver.awaitCount(1);
		testObserver.assertValueCount(1);
		testObserver.assertValue(v -> v.getValidators().size() == 1);
		testObserver.assertValue(v -> v.getValidators().asList().get(0).nodeKey().equals(self));
		testObserver.assertNotComplete();
	}
}