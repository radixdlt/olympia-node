package com.radixdlt.client.dapps.wallet;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.actions.TokenTransfer;
import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.address.RadixAddress;
import io.reactivex.Completable;
import io.reactivex.Observable;

public class RadixWallet {
	private final RadixApplicationAPI api;

	public RadixWallet(RadixApplicationAPI api) {
		this.api = api;
	}

	public Observable<Long> getSubUnitBalance(RadixAddress address, Asset tokenClass) {
		return api.getSubUnitBalance(address, tokenClass);
	}

	public Observable<Long> getXRDSubUnitBalance() {
		return this.getSubUnitBalance(api.getAddress(), Asset.XRD);
	}

	public Observable<Long> getXRDSubUnitBalance(RadixAddress address) {
		return this.getSubUnitBalance(address, Asset.XRD);
	}

	public Observable<TokenTransfer> getXRDTransactions() {
		return api.getTokenTransfers(api.getAddress(), Asset.XRD);
	}

	public Observable<TokenTransfer> getXRDTransactions(RadixAddress address) {
		return api.getTokenTransfers(address, Asset.XRD);
	}

	public Completable transferXRD(long amountInSubUnits, RadixAddress toAddress) {
		Completable completable = api.transferTokens(api.getAddress(), toAddress, Asset.XRD, amountInSubUnits)
			.toCompletable().cache();
		completable.subscribe();
		return completable;
	}

	public Completable transferXRDWhenAvailable(long amountInSubUnits, RadixAddress toAddress) {
		Completable completable = api.getSubUnitBalance(api.getAddress(), Asset.XRD)
			.filter(balance -> balance > amountInSubUnits)
			.firstOrError()
			.map(balance -> api.transferTokens(api.getAddress(), toAddress, Asset.XRD, amountInSubUnits))
			.flatMapCompletable(Result::toCompletable)
			.cache();
		completable.subscribe();
		return completable;
	}
}
