package com.radixdlt.client.assets;

import com.radixdlt.client.core.address.EUID;
import java.math.BigInteger;
import java.util.Objects;

public class Asset {

	/**
	 * Radix Token asset.
	 * TODO: Read from universe file. Hardcode for now.
	 */
	public static final Asset XRD = new Asset("TEST", 100000, new EUID(BigInteger.valueOf("TEST".hashCode())));
	public static final Asset POW = new Asset("POW", 1, new EUID(BigInteger.valueOf(79416)));

	private final String iso;
	private final int subUnits;
	private final EUID id;
	private final boolean powerOfTen;

	public Asset(String iso, int subUnits, EUID id) {
		Objects.requireNonNull(iso);
		Objects.requireNonNull(id);

		if (subUnits == 0) {
			throw new IllegalArgumentException("Integer assets should have subUnits set to 1 for mathematical reasons");
		}
		
		this.iso = iso;
		this.subUnits = subUnits;
		this.id = id;
		this.powerOfTen = isPowerOfTen(subUnits);
	}

	public String getIso() {
		return iso;
	}

	public int getSubUnits() {
		return subUnits;
	}

	public EUID getId() {
		return id;
	}	

	public boolean isPowerOfTen() {
		return powerOfTen;
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof Asset)) {
			return false;
		}

		Asset asset = (Asset) o;
		return this.iso.equals(asset.iso);
	}

	@Override
	public int hashCode() {
		return iso.hashCode();
	}

	private boolean isPowerOfTen(int value) {
		while (value > 9 && value % 10 == 0) 
			value /= 10;
		return value == 1;
	}
}
