package com.radixdlt.client.dapps.wallet;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.actions.TokenTransfer;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.application.objects.Data.DataBuilder;
import com.radixdlt.client.application.objects.Amount;
import com.radixdlt.client.application.objects.Token;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * High Level API for Wallet type actions. Currently being used by the Radix Android Mobile Wallet.
 */
public class RadixWallet {
	// TODO: add cancel option
	public static class SendResult {
		private final Single<Result> result;

		private SendResult(Result result) {
			this.result = Single.just(result);
		}

		private SendResult(Single<Result> result) {
			this.result = result;
		}

		public Observable<AtomSubmissionUpdate> toObservable() {
			return result.flatMapObservable(Result::toObservable);
		}

		public Completable toCompletable() {
			return result.flatMapCompletable(Result::toCompletable);
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
	public Observable<Amount> getBalance() {
		return api.getMyBalance(Token.TEST);
	}

	/**
	 * Returns an unending stream of the latest balance of an account
	 * with a specified address.
	 *
 	 * @param address address to get balance from
	 * @return an unending Observable of balances
	 */
	public Observable<Amount> getBalance(@NonNull RadixAddress address) {
		Objects.requireNonNull(address, "address must be non-null");
		return api.getBalance(address, Token.TEST);
	}

	/**
	 * Returns an unending stream of transfers which have occurred and will
	 * occur with the user's address
	 *
	 * @return an unending Observable of transfers
	 */
	public Observable<TokenTransfer> getTransactions() {
		return api.getTokenTransfers(api.getMyAddress(), Token.TEST);
	}

	/**
	 * Returns an unending stream of transfers which have occurred and will
	 * occur with a specified address
	 *
	 * @param address address to get transfers from
	 * @return an unending Observable of transfers
	 */
	public Observable<TokenTransfer> getTransactions(
		@NonNull RadixAddress address
	) {
		Objects.requireNonNull(address, "address must be non-null");
		return api.getTokenTransfers(address, Token.TEST);
	}

	/**
	 * Immediately try and transfer TEST from user's account to another address. If there is
	 * not enough in the account TransferResult will specify so.
	 *
	 * @param amount The amount of TEST to transfer
	 * @param toAddress The address to send to.
	 * @return The result of the transaction.
	 */
	public SendResult send(
		BigDecimal amount,
		@NonNull RadixAddress toAddress
	) {
		Objects.requireNonNull(toAddress, "toAddress must be non-null");
		return this.send(amount, null, toAddress);
	}

	/**
	 * Immediately try and transfer TEST from user's account to another address with an encrypted message
	 * attachment (readable by sender and receiver). If there is not enough in the account TransferResult
	 * will specify so.
	 *
	 * @param amount The amount of TEST to transfer.
	 * @param message The message to send as an attachment.
	 * @param toAddress The address to send to.
	 * @return The result of the transaction.
	 */
	public SendResult send(
		BigDecimal amount,
		@Nullable String message,
		@NonNull RadixAddress toAddress
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

		Result result = api.sendTokens(toAddress, Amount.of(amount, Token.TEST), attachment);
		return new SendResult(result);
	}

	/**
	 * Block indefinitely until there are enough funds in the account, then immediately transfer
	 * amount to a specified account.
	 *
	 * @param amount The amount of TEST to transfer.
	 * @param toAddress The address to send to.
	 * @return The result of the transaction.
	 */
	public SendResult sendWhenAvailable(
		BigDecimal amount,
		@NonNull RadixAddress toAddress
	) {
		Objects.requireNonNull(toAddress, "toAddress must be non-null");
		return this.sendWhenAvailable(amount, null, toAddress, null);
	}

	/**
	 * Block indefinitely until there are enough funds in the account, then immediately transfer
	 * amount with an encrypted message (readable by sender and receiver) to a specified account.
	 *
	 * @param amount The amount of TEST to transfer.
	 * @param message The message to send as an attachment.
	 * @param toAddress The address to send to.
	 * @return The result of the transaction.
	 */
	public SendResult sendWhenAvailable(
		BigDecimal amount,
		@Nullable String message,
		@NonNull RadixAddress toAddress
	) {
		return sendWhenAvailable(amount, message, toAddress, null);
	}

	/**
	 * Block indefinitely until there are enough funds in the account, then immediately transfer
	 * amount with an encrypted message (readable by sender and receiver) to a specified account.
	 *
	 * @param amount The amount of TEST to transfer.
	 * @param message The message to send as an attachment.
	 * @param toAddress The address to send to.
	 * @param unique The unique id for this transaction.
	 * @return The result of the transaction.
	 */
	public SendResult sendWhenAvailable(
		BigDecimal amount,
		@Nullable String message,
		@NonNull RadixAddress toAddress,
		@Nullable String unique
	) {
		Objects.requireNonNull(amount, "amount must be non-null");
		Objects.requireNonNull(toAddress, "toAddress must be non-null");

		final Data attachment;
		if (message != null) {
			attachment = new DataBuilder()
				.addReader(toAddress.getPublicKey())
				.addReader(api.getMyPublicKey())
				.bytes(message.getBytes()).build();
		} else {
			attachment = null;
		}

		final byte[] uniqueBytes = unique != null ? unique.getBytes() : null;

		final Amount amountToSend = Amount.of(amount, Token.TEST);

		Single<Result> result = api.getMyBalance(Token.TEST)
			.filter(amountToSend::lte)
			.firstOrError()
			.map(balance -> api.sendTokens(toAddress, amountToSend, attachment, uniqueBytes))
			.cache();
		result.subscribe();

		return new SendResult(result);
	}
}
