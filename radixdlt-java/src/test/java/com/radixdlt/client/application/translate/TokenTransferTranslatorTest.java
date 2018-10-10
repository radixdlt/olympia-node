package com.radixdlt.client.application.translate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomBuilder;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import java.util.List;
import org.junit.Test;
import com.radixdlt.client.application.actions.TokenTransfer;
import com.radixdlt.client.application.objects.Token;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.ledger.ParticleStore;
import java.util.Collections;

public class TokenTransferTranslatorTest {
	@Test
	public void testSendToSelfTest() {
		RadixUniverse universe = mock(RadixUniverse.class);
		ParticleStore particleStore = mock(ParticleStore.class);
		Atom atom = mock(Atom.class);
		ECPublicKey myKey = mock(ECPublicKey.class);
		RadixAddress myAddress = mock(RadixAddress.class);
		when(universe.getAddressFrom(myKey)).thenReturn(myAddress);
		when(atom.tokenSummary()).thenReturn(Collections.singletonMap(Token.TEST.getId(),
			Collections.singletonMap(myKey, 0L)
		));

		TokenTransferTranslator tokenTransferTranslator = new TokenTransferTranslator(universe, particleStore);
		List<TokenTransfer> tokenTransfers = tokenTransferTranslator.fromAtom(atom);
		assertEquals(myAddress, tokenTransfers.get(0).getFrom());
		assertEquals(myAddress, tokenTransfers.get(0).getTo());
	}

	@Test
	public void createTransactionWithNoFunds() {
		RadixUniverse universe = mock(RadixUniverse.class);
		RadixAddress address = mock(RadixAddress.class);

		TokenTransferTranslator transferTranslator = new TokenTransferTranslator(universe, addr -> Observable.never());
		TokenTransfer tokenTransfer = mock(TokenTransfer.class);
		when(tokenTransfer.getSubUnitAmount()).thenReturn(10L);
		when(tokenTransfer.getFrom()).thenReturn(address);
		when(tokenTransfer.getToken()).thenReturn(Token.TEST);

		TestObserver observer = TestObserver.create();
		transferTranslator.translate(tokenTransfer, new AtomBuilder()).subscribe(observer);
		observer.awaitTerminalEvent();
		observer.assertError(new InsufficientFundsException(Token.TEST, 0, 10));
	}

}