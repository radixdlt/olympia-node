package com.radixdlt.client.application.translate.tokens;

import java.math.BigInteger;
import java.util.Collections;

import org.junit.Test;

import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.crypto.ECPublicKey;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.observers.TestObserver;

public class AtomToTokenTransferActionsMapperTest {
	@Test
	public void testSendToSelfTest() {
		RadixUniverse universe = mock(RadixUniverse.class);
		Atom atom = mock(Atom.class);
		ECPublicKey myKey = mock(ECPublicKey.class);
		RadixAddress myAddress = mock(RadixAddress.class);
		when(universe.getAddressFrom(myKey)).thenReturn(myAddress);
		TokenClassReference tokenClassReference = mock(TokenClassReference.class);
		when(atom.tokenSummary()).thenReturn(Collections.singletonMap(tokenClassReference,
			Collections.singletonMap(myKey, BigInteger.ZERO)
		));

		AtomToTokenTransfersMapper tokenTransferTranslator = new AtomToTokenTransfersMapper(universe);
		TestObserver<TokenTransfer> testObserver = TestObserver.create();
		tokenTransferTranslator.map(atom, mock(RadixIdentity.class)).subscribe(testObserver);
		testObserver.assertValue(transfer -> myAddress.equals(transfer.getFrom()) && myAddress.equals(transfer.getTo()));
	}
}