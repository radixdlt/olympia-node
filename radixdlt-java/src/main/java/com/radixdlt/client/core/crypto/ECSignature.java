package com.radixdlt.client.core.crypto;

import java.math.BigInteger;
import org.bouncycastle.util.encoders.Base64;

public class ECSignature {
	private byte[] r;
	private byte[] s;

	public ECSignature(BigInteger r, BigInteger s) {
		this.r = r.toByteArray();
		this.s = s.toByteArray();
	}

	public String getRBase64() {
		return Base64.toBase64String(r);
	}

	public BigInteger getR() {
		return new BigInteger(r);
	}

	public BigInteger getS() {
		return new BigInteger(s);
	}
}
