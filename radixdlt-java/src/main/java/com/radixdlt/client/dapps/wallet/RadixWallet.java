package com.radixdlt.client.dapps.wallet;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.actions.TokenTransfer;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.application.objects.Data.DataBuilder;
import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import io.reactivex.Observable;
import io.reactivex.observables.ConnectableObservable;

public class RadixWallet {
	public static class TransferResult {
		private final Observable<AtomSubmissionUpdate> updates;

		private TransferResult(Observable<AtomSubmissionUpdate> updates) {
			this.updates = updates;
		}

		public Observable<AtomSubmissionUpdate> toObservable() {
			return updates;
		}
	}

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

	public TransferResult transferXRD(long amountInSubUnits, RadixAddress toAddress) {
		ConnectableObservable<AtomSubmissionUpdate> updates =
			api.transferTokens(api.getAddress(), toAddress, Asset.XRD, amountInSubUnits).toObservable().replay();
		updates.connect();
		return new TransferResult(updates);
	}


	public TransferResult transferXRDWhenAvailable(long amountInSubUnits, RadixAddress toAddress) {
		return this.transferXRDWhenAvailable(amountInSubUnits, toAddress, null);
	}

	public TransferResult transferXRDWhenAvailable(long amountInSubUnits, RadixAddress toAddress, String message) {
		final Data attachment;
		if (message != null) {
			attachment = new DataBuilder()
				.addReader(toAddress.getPublicKey())
				.addReader(api.getAddress().getPublicKey())
				.bytes(message.getBytes()).build();
		} else {
			attachment = null;
		}

		ConnectableObservable<AtomSubmissionUpdate> updates = api.getSubUnitBalance(api.getAddress(), Asset.XRD)
			.filter(balance -> balance > amountInSubUnits)
			.firstOrError()
			.map(balance -> api.transferTokens(api.getAddress(), toAddress, Asset.XRD, amountInSubUnits, attachment))
			.flatMapObservable(Result::toObservable)
			.replay();

		updates.connect();

		return new TransferResult(updates);
	}
}
