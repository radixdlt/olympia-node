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

package com.radixdlt.client.application.identity;

import java.util.Optional;
import com.google.common.hash.HashCode;

import io.reactivex.observers.TestObserver;
import org.junit.Test;
import com.radixdlt.identifiers.EUID;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECDSASignature;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LocalRadixIdentityTest {

	@Test
	public void signTest() {
		ECKeyPair keyPair = mock(ECKeyPair.class);
		ECDSASignature ecSignature = mock(ECDSASignature.class);
		EUID euid = mock(EUID.class);
		when(keyPair.sign(any(HashCode.class))).thenReturn(ecSignature);
		when(keyPair.euid()).thenReturn(euid);

		Atom signedAtom = mock(Atom.class);
		when(signedAtom.getSignature(any())).thenReturn(Optional.of(ecSignature));
		HashCode hash = mock(HashCode.class);
		Atom atom = mock(Atom.class);
		when(atom.addSignature(any(), any())).thenReturn(signedAtom);
		when(atom.getHash()).thenReturn(hash);
		LocalRadixIdentity identity = new LocalRadixIdentity(keyPair);
		TestObserver<Atom> testObserver = TestObserver.create();
		identity.addSignature(atom).subscribe(testObserver);

		verify(keyPair, never()).getPrivateKey();

		testObserver.assertValue(a -> a.getSignature(euid).get().equals(ecSignature));
	}
}
