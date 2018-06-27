package com.radixdlt.client.assets;

public class AssetAmount {
	private final Asset asset;
	private final long amountInSubunits;

	public AssetAmount(Asset asset, long amountInSubunits) {
		this.asset = asset;
		this.amountInSubunits = amountInSubunits;
	}

	@Override
	public String toString() {
		long change = amountInSubunits % asset.getSubUnits();

		if (change == 0) {
			return amountInSubunits / asset.getSubUnits() + " " + asset.getIso();
		} else {
			return amountInSubunits / asset.getSubUnits() + " and " + change + "/" + asset.getSubUnits() + " " + asset.getIso();
		}
	}
}
