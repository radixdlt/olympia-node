package com.radixdlt.client.application.translate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.objects.TokenTransfer;
import com.radixdlt.client.core.atoms.AtomBuilder;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import com.radixdlt.client.application.actions.TransferTokensAction;
import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.TransactionAtom;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.ledger.ParticleStore;
import java.util.Collections;

public class TransferTokensActionTranslatorTest {
	@Test
	public void testSendToSelfTest() {
		RadixUniverse universe = mock(RadixUniverse.class);
		ParticleStore particleStore = mock(ParticleStore.class);
		TransactionAtom atom = mock(TransactionAtom.class);
		ECPublicKey myKey = mock(ECPublicKey.class);
		RadixAddress myAddress = mock(RadixAddress.class);
		when(universe.getAddressFrom(myKey)).thenReturn(myAddress);
		when(atom.summary()).thenReturn(Collections.singletonMap(
			Collections.singleton(myKey), Collections.singletonMap(Asset.TEST.getId(), 0L)
		));

		TestObserver<TokenTransfer> testObserver = TestObserver.create();
		TokenTransferTranslator tokenTransferTranslator = new TokenTransferTranslator(universe, particleStore);
		tokenTransferTranslator.fromAtom(atom, mock(RadixIdentity.class)).subscribe(testObserver);
		testObserver.assertValue(transfer -> myAddress.equals(transfer.getFrom()) && myAddress.equals(transfer.getTo()));
	}

	@Test
	public void createTransactionWithNoFunds() {
		RadixUniverse universe = mock(RadixUniverse.class);
		RadixAddress address = mock(RadixAddress.class);

		TokenTransferTranslator transferTranslator = new TokenTransferTranslator(universe, addr -> Observable.never());
		TransferTokensAction transferTokensAction = mock(TransferTokensAction.class);
		when(transferTokensAction.getSubUnitAmount()).thenReturn(10L);
		when(transferTokensAction.getFrom()).thenReturn(address);
		when(transferTokensAction.getTokenClass()).thenReturn(Asset.TEST);

		TestObserver observer = TestObserver.create();
		transferTranslator.translate(transferTokensAction, new AtomBuilder()).subscribe(observer);
		observer.awaitTerminalEvent();
		observer.assertError(new InsufficientFundsException(Asset.TEST, 0, 10));
	}

}