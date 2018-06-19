package com.radixdlt.client.core.address;

import java.math.BigInteger;

public class EUID {
	private final BigInteger value;

	public EUID(byte[] value) {
		this.value = new BigInteger(value);
	}

	public EUID(BigInteger value) {
		this.value = value;
	}

	public BigInteger bigInteger() {
		return value;
	}

	public long getShard() {
		return value.longValue();
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}

	@Override
	public boolean equals(Object object) {
		EUID other = (EUID)object;
		return this.value.equals(other.value);
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
