package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.atoms.particles.AtomFeeConsumable;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.pow.ProofOfWork;
import com.radixdlt.client.core.pow.ProofOfWorkBuilder;
import java.util.Objects;

public class AtomFeeConsumableBuilder {
	private ECPublicKey owner;
	private int magic;
	private int leading;
	private UnsignedAtom unsignedAtom;
	private TokenRef powToken;

	public AtomFeeConsumableBuilder() {
	}

	public AtomFeeConsumableBuilder powToken(TokenRef powToken) {
		this.powToken = powToken;
		return this;
	}

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
			new AccountReference(owner),
			System.nanoTime(),
			powToken,
			System.currentTimeMillis() * 60000
		);
	}
}
