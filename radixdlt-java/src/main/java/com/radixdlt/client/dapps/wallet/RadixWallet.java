package com.radixdlt.client.dapps.wallet;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.actions.TokenTransfer;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.application.objects.Data.DataBuilder;
import com.radixdlt.client.assets.Amount;
import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.observables.ConnectableObservable;
import java.util.Objects;

/**
 * High Level API for Wallet type actions. Currently being used by the Radix Android Mobile Wallet.
 */
public class RadixWallet {
	// TODO: add cancel option
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

	/**
	 * Returns an unending stream of the latest balance of an account
	 * with the user's address
	 *
	 * @return an unending Observable of balances
	 */
	public Observable<Amount> getXRDBalance() {
		return api.getBalance(api.getMyAddress(), Asset.XRD);
	}

	/**
	 * Returns an unending stream of the latest balance of an account
	 * with a specified address.
	 *
 	 * @param address address to get balance from
	 * @return an unending Observable of balances
	 */
	public Observable<Amount> getXRDBalance(@NonNull RadixAddress address) {
		Objects.requireNonNull(address, "address must be non-null");
		return api.getBalance(address, Asset.XRD);
	}

	/**
	 * Returns an unending stream of transfers which have occurred and will
	 * occur with the user's address
	 *
	 * @return an unending Observable of transfers
	 */
	public Observable<TokenTransfer> getXRDTransactions() {
		return api.getTokenTransfers(api.getMyAddress(), Asset.XRD);
	}

	/**
	 * Returns an unending stream of transfers which have occurred and will
	 * occur with a specified address
	 *
	 * @param address address to get transfers from
	 * @return an unending Observable of transfers
	 */
	public Observable<TokenTransfer> getXRDTransactions(
		@NonNull RadixAddress address
	) {
		Objects.requireNonNull(address, "address must be non-null");
		return api.getTokenTransfers(address, Asset.XRD);
	}

	/**
	 * Immediately try and transfer XRD from user's account to another address. If there is
	 * not enough in the account TransferResult will specify so.
	 *
	 * @param amountInSubUnits The amount of XRD to transfer
	 * @param toAddress The address to send to.
	 * @return The result of the transaction.
	 */
	public TransferResult transferXRD(
		long amountInSubUnits,
		@NonNull RadixAddress toAddress
	) {
		Objects.requireNonNull(toAddress, "toAddress must be non-null");
		return this.transferXRD(amountInSubUnits, toAddress, null);
	}

	/**
	 * Immediately try and transfer XRD from user's account to another address with an encrypted message
	 * attachment (readable by sender and receiver). If there is not enough in the account TransferResult
	 * will specify so.
	 *
	 * @param amountInSubUnits The amount of XRD to transfer.
	 * @param toAddress The address to send to.
	 * @param message The message to send as an attachment.
	 * @return The result of the transaction.
	 */
	public TransferResult transferXRD(
		long amountInSubUnits,
		@NonNull RadixAddress toAddress,
		@Nullable String message
	) {
		Objects.requireNonNull(toAddress, "toAddress must be non-null");

		final Data attachment;
		if (message != null) {
			attachment = new DataBuilder()
				.addReader(toAddress.getPublicKey())
				.addReader(api.getMyAddress().getPublicKey())
				.bytes(message.getBytes()).build();
		} else {
			attachment = null;
		}

		ConnectableObservable<AtomSubmissionUpdate> updates =
			api.transferTokens(api.getMyAddress(), toAddress, Amount.subUnitsOf(amountInSubUnits, Asset.XRD), attachment)
				.toObservable().replay();
		updates.connect();
		return new TransferResult(updates);
	}

	/**
	 * Block indefinitely until there are enough funds in the account, then immediately transfer
	 * amount to a specified account.
	 *
	 * @param amountInSubUnits The amount of XRD to transfer.
	 * @param toAddress The address to send to.
	 * @return The result of the transaction.
	 */
	public TransferResult transferXRDWhenAvailable(
		long amountInSubUnits,
		@NonNull RadixAddress toAddress
	) {
		Objects.requireNonNull(toAddress, "toAddress must be non-null");
		return this.transferXRDWhenAvailable(amountInSubUnits, toAddress, null);
	}

	/**
	 * Block indefinitely until there are enough funds in the account, then immediately transfer
	 * amount with an encrypted message (readable by sender and receiver) to a specified account.
	 *
	 * @param amountInSubUnits The amount of XRD to transfer.
	 * @param toAddress The address to send to.
	 * @param message The message to send as an attachment.
	 * @return The result of the transaction.
	 */
	public TransferResult transferXRDWhenAvailable(
		long amountInSubUnits,
		@NonNull RadixAddress toAddress,
		@Nullable String message
	) {
		Objects.requireNonNull(toAddress, "toAddress must be non-null");

		final Data attachment;
		if (message != null) {
			attachment = new DataBuilder()
				.addReader(toAddress.getPublicKey())
				.addReader(api.getMyAddress().getPublicKey())
				.bytes(message.getBytes()).build();
		} else {
			attachment = null;
		}

		ConnectableObservable<AtomSubmissionUpdate> updates = api.getBalance(api.getMyAddress(), Asset.XRD)
			.filter(amount -> amount.getAmountInSubunits() > amountInSubUnits)
			.firstOrError()
			.map(balance -> api.transferTokens(api.getMyAddress(), toAddress, Amount.subUnitsOf(amountInSubUnits, Asset.XRD), attachment))
			.flatMapObservable(Result::toObservable)
			.replay();

		updates.connect();

		return new TransferResult(updates);
	}
}
