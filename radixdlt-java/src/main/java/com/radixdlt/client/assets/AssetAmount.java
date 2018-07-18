package com.radixdlt.client.assets;

import java.math.BigDecimal;

/**
 * Class mainly for formatting amounts in error messages and other English text.
 */
public class AssetAmount {
	private final Asset asset;
	private final long amountInSubunits;

	public AssetAmount(Asset asset, long amountInSubunits) {
		this.asset = asset;
		this.amountInSubunits = amountInSubunits;
	}

	@Override
	public String toString() {
		return formattedAmount() + " " + asset.getIso();
	}

	private String formattedAmount() {
		long remainder = amountInSubunits % asset.getSubUnits();

		if (remainder == 0) {
			// Whole number
			return String.valueOf(amountInSubunits / asset.getSubUnits());
		}

		if (isPowerOfTen(asset.getSubUnits())) {
			// Decimal format
			return BigDecimal.valueOf(amountInSubunits).divide(BigDecimal.valueOf(asset.getSubUnits())).toString();
		}

		// Fraction format
		long quotient = amountInSubunits / asset.getSubUnits();
		String fraction = remainder + "/" + asset.getSubUnits();
		if (quotient == 0) {
			return fraction;
		}
		return quotient + " and " + fraction;
	}

	private boolean isPowerOfTen(int value) {
		while (value > 9 && value % 10 == 0) {
			value /= 10;
		}
		return value == 1;
	}
}
