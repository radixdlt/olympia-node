package com.radixdlt.client.application.translate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;

import com.radixdlt.client.core.atoms.Atom;
import java.math.BigDecimal;
import java.util.List;
import org.junit.Test;
import com.radixdlt.client.application.actions.TransferTokensAction;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.Collections;

public class TransferTokensActionTranslatorTest {
	@Test
	public void testSendToSelfTest() {
		RadixUniverse universe = mock(RadixUniverse.class);
		Atom atom = mock(Atom.class);
		ECPublicKey myKey = mock(ECPublicKey.class);
		RadixAddress myAddress = mock(RadixAddress.class);
		when(universe.getAddressFrom(myKey)).thenReturn(myAddress);
		TokenClassReference tokenClassReference = mock(TokenClassReference.class);
		when(atom.tokenSummary()).thenReturn(Collections.singletonMap(tokenClassReference,
			Collections.singletonMap(myKey, 0L)
		));

		TokenTransferTranslator tokenTransferTranslator = new TokenTransferTranslator(universe);
		List<TransferTokensAction> transferTokenActions = tokenTransferTranslator.fromAtom(atom);
		assertEquals(myAddress, transferTokenActions.get(0).getFrom());
		assertEquals(myAddress, transferTokenActions.get(0).getTo());
	}

	@Test
	public void createTransactionWithNoFunds() {
		RadixUniverse universe = mock(RadixUniverse.class);
		RadixAddress address = mock(RadixAddress.class);

		TokenTransferTranslator transferTranslator = new TokenTransferTranslator(universe);

		TokenClassReference token = mock(TokenClassReference.class);
		when(token.getIso()).thenReturn("TEST");

		TransferTokensAction transferTokensAction = mock(TransferTokensAction.class);
		when(transferTokensAction.getAmount()).thenReturn(new BigDecimal("1.0"));
		when(transferTokensAction.getFrom()).thenReturn(address);
		when(transferTokensAction.getTokenClassReference()).thenReturn(token);

		TokenBalanceState state = mock(TokenBalanceState.class);
		when(state.getBalance()).thenReturn(Collections.emptyMap());

		assertThatThrownBy(() -> transferTranslator.map(transferTokensAction, state))
			.isEqualTo(new InsufficientFundsException(token, BigDecimal.ZERO, new BigDecimal("1.0")));
	}

}