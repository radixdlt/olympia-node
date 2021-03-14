/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.atom.SpunParticle;

import java.util.Map;
import org.junit.Test;

import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.test.util.TypedMocks;
import com.radixdlt.client.core.atoms.Atom;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.observers.TestObserver;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.utils.UInt256;

public class AtomToTokenTransferActionsMapperTest {
	@Test
	public void testSendToSelfTest() {
		RadixAddress myAddress = mock(RadixAddress.class);
		when(myAddress.euid()).thenReturn(EUID.ONE);
		RRI tokenDefinitionReference = mock(RRI.class);
		when(tokenDefinitionReference.getName()).thenReturn("JOSH");
		when(tokenDefinitionReference.getAddress()).thenReturn(RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor"));

		TransferrableTokensParticle ttp = new TransferrableTokensParticle(
			UInt256.ONE, UInt256.ONE, myAddress, 0, tokenDefinitionReference, TypedMocks.rmock(Map.class)
		);

		ParticleGroup pg = ParticleGroup.of(SpunParticle.down(ttp), SpunParticle.up(ttp));
		Atom atom = Atom.create(pg);

		AtomToTokenTransfersMapper tokenTransferTranslator = new AtomToTokenTransfersMapper();
		TestObserver<TokenTransfer> testObserver = TestObserver.create();
		tokenTransferTranslator.map(atom, mock(RadixIdentity.class)).subscribe(testObserver);
		testObserver.assertValue(transfer -> myAddress.equals(transfer.getFrom()) && myAddress.equals(transfer.getTo()));
	}
}
