package com.radixdlt.client.core.pow;

import com.radixdlt.client.core.atoms.RadixHash;
import okio.ByteString;

import java.nio.ByteBuffer;
import org.bouncycastle.util.encoders.Base64;

public class ProofOfWork {
	private final long nonce;
	private final int magic;
	private final byte[] seed;
	private final byte[] target;

	public ProofOfWork(long nonce, int magic, byte[] seed, byte[] target) {
		this.nonce = nonce;
		this.magic = magic;
		this.seed = seed;
		this.target = target;
	}

	public String getTargetHex() {
		return ByteString.of(target).hex();
	}

	public long getNonce() {
		return nonce;
	}

	public void validate() throws ProofOfWorkException {
		String targetHex = getTargetHex();
		ByteBuffer byteBuffer = ByteBuffer.allocate(4 + 32 + Long.BYTES);
		byteBuffer.putInt(magic);
		byteBuffer.put(seed);
		byteBuffer.putLong(nonce);
		String hashHex = ByteString.of(RadixHash.of(byteBuffer.array()).toByteArray()).hex();
		if (hashHex.compareTo(targetHex) > 0) {
			throw new ProofOfWorkException(hashHex, targetHex);
		}
	}

	@Override
	public String toString() {
		return "POW: nonce(" + nonce + ") magic(" + magic + ") seed(" + Base64.toBase64String(seed) + ") target(" + getTargetHex() + ")";
	}
}
