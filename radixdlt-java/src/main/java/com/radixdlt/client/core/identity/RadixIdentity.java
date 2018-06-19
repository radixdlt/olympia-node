package com.radixdlt.client.core.identity;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.EncryptedPayload;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.crypto.ECPublicKey;
import io.reactivex.Single;

public interface RadixIdentity {
	Single<Atom> sign(UnsignedAtom atom);
	Single<byte[]> decrypt(EncryptedPayload data);
	ECPublicKey getPublicKey();
}
