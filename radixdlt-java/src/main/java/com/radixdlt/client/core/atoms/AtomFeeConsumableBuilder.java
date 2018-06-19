package com.radixdlt.client.core.atoms;

import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.pow.ProofOfWork;
import com.radixdlt.client.core.pow.ProofOfWorkBuilder;
import java.util.Collections;
import java.util.Objects;
import org.bouncycastle.util.encoders.Hex;

public class AtomFeeConsumableBuilder {
	private ECPublicKey owner;
	private int magic;
	private int leading;
	private UnsignedAtom unsignedAtom;

	public AtomFeeConsumableBuilder pow(int magic, int leading) {
		this.magic = magic;
		this.leading = leading;
		return this;
	}

	public AtomFeeConsumableBuilder atom(UnsignedAtom atom) {
		this.unsignedAtom = atom;
		return this;
	}

	public AtomFeeConsumableBuilder owner(ECPublicKey owner) {
		this.owner = owner;
		return this;
	}

	public AtomFeeConsumable build() {
		Objects.requireNonNull(unsignedAtom);
		Objects.requireNonNull(owner);

		final byte[] seed = unsignedAtom.getRawAtom().getHash().toByteArray();

		ProofOfWork pow = new ProofOfWorkBuilder().build(magic, seed, leading);

		return new AtomFeeConsumable(
			pow.getNonce(),
			Collections.singleton(owner.toECKeyPair()),
			System.nanoTime(),
			Asset.POW.getId()
		);
	}
}
