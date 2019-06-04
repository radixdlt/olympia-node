package com.radixdlt.client.application.identity;

import java.util.Optional;

import org.junit.Test;
import org.radix.common.ID.EUID;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECSignature;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.observers.TestObserver;

public class LocalRadixIdentityTest {

	@Test
	public void signTest() {
		ECKeyPair keyPair = mock(ECKeyPair.class);
		ECSignature ecSignature = mock(ECSignature.class);
		EUID euid = mock(EUID.class);
		when(keyPair.sign(any())).thenReturn(ecSignature);
		when(keyPair.getUID()).thenReturn(euid);

		Atom signedAtom = mock(Atom.class);
		when(signedAtom.getSignature(any())).thenReturn(Optional.of(ecSignature));
		RadixHash hash = mock(RadixHash.class);
		UnsignedAtom atom = mock(UnsignedAtom.class);
		when(atom.sign(any(), any())).thenReturn(signedAtom);
		when(atom.getHash()).thenReturn(hash);
		LocalRadixIdentity identity = new LocalRadixIdentity(keyPair);
		TestObserver<Atom> testObserver = TestObserver.create();
		identity.sign(atom).subscribe(testObserver);

		verify(keyPair, never()).getPrivateKey();

		testObserver.assertValue(a -> a.getSignature(euid).get().equals(ecSignature));
	}
}