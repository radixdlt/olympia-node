package com.radixdlt.client.application.translate.tokens;

import java.math.BigInteger;
import java.util.Collections;

import org.junit.Test;

import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.observers.TestObserver;

public class AtomToTokenTransferActionsMapperTest {
	@Test
	public void testSendToSelfTest() {
		Atom atom = mock(Atom.class);
		RadixAddress myAddress = mock(RadixAddress.class);
		TokenDefinitionReference tokenDefinitionReference = mock(TokenDefinitionReference.class);
		when(tokenDefinitionReference.getSymbol()).thenReturn("JOSH");
		when(atom.tokenSummary()).thenReturn(Collections.singletonMap(tokenDefinitionReference,
			Collections.singletonMap(myAddress, BigInteger.ZERO)
		));

		AtomToTokenTransfersMapper tokenTransferTranslator = new AtomToTokenTransfersMapper();
		TestObserver<TokenTransfer> testObserver = TestObserver.create();
		tokenTransferTranslator.map(atom, mock(RadixIdentity.class)).subscribe(testObserver);
		testObserver.assertValue(transfer -> myAddress.equals(transfer.getFrom()) && myAddress.equals(transfer.getTo()));
	}
}