package com.radixdlt.client.application.translate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;

import com.radixdlt.client.core.atoms.Atom;
import java.math.BigDecimal;
import java.util.List;
import org.junit.Test;
import com.radixdlt.client.application.actions.TokenTransfer;
import com.radixdlt.client.core.atoms.TokenRef;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.Collections;

public class TokenTransferTranslatorTest {
	@Test
	public void testSendToSelfTest() {
		RadixUniverse universe = mock(RadixUniverse.class);
		Atom atom = mock(Atom.class);
		ECPublicKey myKey = mock(ECPublicKey.class);
		RadixAddress myAddress = mock(RadixAddress.class);
		when(universe.getAddressFrom(myKey)).thenReturn(myAddress);
		TokenRef tokenRef = mock(TokenRef.class);
		when(atom.tokenSummary()).thenReturn(Collections.singletonMap(tokenRef,
			Collections.singletonMap(myKey, 0L)
		));

		TokenTransferTranslator tokenTransferTranslator = new TokenTransferTranslator(universe);
		List<TokenTransfer> tokenTransfers = tokenTransferTranslator.fromAtom(atom);
		assertEquals(myAddress, tokenTransfers.get(0).getFrom());
		assertEquals(myAddress, tokenTransfers.get(0).getTo());
	}

	@Test
	public void createTransactionWithNoFunds() {
		RadixUniverse universe = mock(RadixUniverse.class);
		RadixAddress address = mock(RadixAddress.class);

		TokenTransferTranslator transferTranslator = new TokenTransferTranslator(universe);

		TokenRef token = mock(TokenRef.class);
		when(token.getIso()).thenReturn("TEST");

		TokenTransfer tokenTransfer = mock(TokenTransfer.class);
		when(tokenTransfer.getAmount()).thenReturn(new BigDecimal("1.0"));
		when(tokenTransfer.getFrom()).thenReturn(address);
		when(tokenTransfer.getTokenRef()).thenReturn(token);

		TokenBalanceState state = mock(TokenBalanceState.class);
		when(state.getBalance()).thenReturn(Collections.emptyMap());

		assertThatThrownBy(() -> transferTranslator.map(tokenTransfer, state))
			.isEqualTo(new InsufficientFundsException(token, BigDecimal.ZERO, new BigDecimal("1.0")));
	}

}