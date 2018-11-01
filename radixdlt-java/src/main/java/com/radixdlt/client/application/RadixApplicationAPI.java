package com.radixdlt.client.application;

import com.radixdlt.client.application.actions.BurnTokensAction;
import com.radixdlt.client.application.actions.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.actions.MintTokensAction;
import com.radixdlt.client.application.translate.BurnTokensActionMapper;
import com.radixdlt.client.application.translate.MintTokensActionMapper;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.radixdlt.client.atommodel.timestamp.TimestampParticle;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.client.Serialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.radixdlt.client.application.actions.CreateTokenAction;
import com.radixdlt.client.application.actions.StoreDataAction;
import com.radixdlt.client.application.actions.TransferTokensAction;
import com.radixdlt.client.application.actions.UniqueProperty;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.application.objects.Data.DataBuilder;
import com.radixdlt.client.application.objects.UnencryptedData;
import com.radixdlt.client.application.translate.ApplicationStore;
import com.radixdlt.client.application.translate.DataStoreTranslator;
import com.radixdlt.client.application.translate.FeeMapper;
import com.radixdlt.client.application.translate.PowFeeMapper;
import com.radixdlt.client.application.translate.TokenBalanceReducer;
import com.radixdlt.client.application.translate.TokenBalanceState;
import com.radixdlt.client.application.translate.TokenMapper;
import com.radixdlt.client.application.translate.TokenReducer;
import com.radixdlt.client.application.translate.TokenState;
import com.radixdlt.client.application.translate.TokenTransferTranslator;
import com.radixdlt.client.application.translate.UniquePropertyTranslator;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.RadixUniverse.Ledger;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.core.pow.ProofOfWorkBuilder;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.annotations.Nullable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.observables.ConnectableObservable;

/**
 * The Radix Dapp API, a high level api which dapps can utilize. The class hides
 * the complexity of Atoms and cryptography and exposes a simple high level interface.
 */
public class RadixApplicationAPI {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixApplicationAPI.class);

	public static class Result {
		private final Observable<AtomSubmissionUpdate> updates;
		private final Completable completable;

		private Result(Observable<AtomSubmissionUpdate> updates) {
			this.updates = updates;

			this.completable = updates.filter(AtomSubmissionUpdate::isComplete)
				.firstOrError()
				.flatMapCompletable(update -> {
					if (update.getState() == AtomSubmissionState.STORED) {
						return Completable.complete();
					} else {
						return Completable.error(new RuntimeException(update.getData().toString()));
					}
				});
		}

		public Observable<AtomSubmissionUpdate> toObservable() {
			return updates;
		}

		public Completable toCompletable() {
			return completable;
		}
	}

	private final RadixIdentity identity;
	private final RadixUniverse universe;

	// TODO: Translators from application to particles
	private final DataStoreTranslator dataStoreTranslator;
	private final TokenTransferTranslator tokenTransferTranslator;
	private final UniquePropertyTranslator uniquePropertyTranslator;
	private final MintTokensActionMapper mintTokensActionMapper;
	private final BurnTokensActionMapper burnTokensActionMapper;
	private final TokenMapper tokenMapper;

	private final ApplicationStore<Map<TokenClassReference, TokenState>> tokenStore;
	private final ApplicationStore<TokenBalanceState> tokenBalanceStore;

	// TODO: Translator from particles to atom
	private final FeeMapper feeMapper;

	private final Ledger ledger;

	private RadixApplicationAPI(
		RadixIdentity identity,
		RadixUniverse universe,
		DataStoreTranslator dataStoreTranslator,
		FeeMapper feeMapper,
		Ledger ledger
	) {
		this.identity = identity;
		this.universe = universe;
		this.dataStoreTranslator = dataStoreTranslator;
		this.tokenTransferTranslator = new TokenTransferTranslator(universe);
		this.uniquePropertyTranslator = new UniquePropertyTranslator();
		this.tokenMapper = new TokenMapper();
		this.mintTokensActionMapper = new MintTokensActionMapper();
		this.burnTokensActionMapper = new BurnTokensActionMapper(universe);

		this.tokenStore = new ApplicationStore<>(ledger.getParticleStore(), new TokenReducer());
		this.tokenBalanceStore = new ApplicationStore<>(ledger.getParticleStore(), new TokenBalanceReducer());

		this.feeMapper = feeMapper;
		this.ledger = ledger;
	}

	public static RadixApplicationAPI create(RadixIdentity identity) {
		Objects.requireNonNull(identity);
		return create(
			identity,
			RadixUniverse.getInstance(),
			DataStoreTranslator.getInstance(),
			new PowFeeMapper(p -> new Atom(p).getHash(), new ProofOfWorkBuilder())
		);
	}

	public static RadixApplicationAPI create(
		RadixIdentity identity,
		RadixUniverse universe,
		DataStoreTranslator dataStoreTranslator,
		FeeMapper feeMapper
	) {
		Objects.requireNonNull(identity);
		Objects.requireNonNull(universe);
		Objects.requireNonNull(feeMapper);
		return new RadixApplicationAPI(identity, universe, dataStoreTranslator, feeMapper, universe.getLedger());
	}

	/**
	 * Idempotent method which prefetches atoms in user's account
	 * TODO: what to do when no puller available
	 *
	 * @return Disposable to dispose to stop pulling
	 */
	public Disposable pull() {
		return pull(getMyAddress());
	}

	/**
	 * Idempotent method which prefetches atoms in an address
	 * TODO: what to do when no puller available
	 *
	 * @param address the address to pull atoms from
	 * @return Disposable to dispose to stop pulling
	 */
	public Disposable pull(RadixAddress address) {
		Objects.requireNonNull(address);

		if (ledger.getAtomPuller() != null) {
			return ledger.getAtomPuller().pull(address);
		} else {
			return Disposables.disposed();
		}
	}

	/**
	 * Returns the native Token Reference found in the genesis atom
	 *
	 * @return the native token reference
	 */
	public TokenClassReference getNativeTokenRef() {
		return universe.getNativeToken();
	}

	/**
	 * Returns a hot observable with the latest token state of the native token
	 *
	 * @return a hot observable of latest state of the native token
	 */
	public Observable<TokenState> getNativeTokenState() {
		return getToken(getNativeTokenRef());
	}

	/**
	 * Returns a hot observable of the latest state of token classes at a given
	 * address
	 *
	 * @param address the address of the account to check
	 * @return a hot observable of the latest state of token classes
	 */
	public Observable<Map<TokenClassReference, TokenState>> getTokens(RadixAddress address) {
		pull(address);

		return tokenStore.getState(address);
	}

	/**
	 * Returns a hot observable of the latest state of token classes at the user's
	 * address
	 *
	 * @return a hot observable of the latest state of token classes
	 */
	public Observable<Map<TokenClassReference, TokenState>> getMyTokens() {
		return getTokens(getMyAddress());
	}

	/**
	 * Returns a hot observable of the latest state of a given token
	 *
	 * @return a hot observable of the latest state of the token
	 */
	public Observable<TokenState> getToken(TokenClassReference ref) {
		pull(ref.getAddress());

		return tokenStore.getState(ref.getAddress())
			.flatMapMaybe(m -> Optional.ofNullable(m.get(ref)).map(Maybe::just).orElse(Maybe.empty()));
	}

	public ECPublicKey getMyPublicKey() {
		return identity.getPublicKey();
	}

	public RadixIdentity getMyIdentity() {
		return identity;
	}

	public RadixAddress getMyAddress() {
		return universe.getAddressFrom(identity.getPublicKey());
	}

	public Observable<Data> getData(RadixAddress address) {
		Objects.requireNonNull(address);

		pull(address);

		return ledger.getAtomStore().getAtoms(address)
			.filter(AtomObservation::isStore)
			.map(AtomObservation::getAtom)
			.map(dataStoreTranslator::fromAtom)
			.flatMapMaybe(data -> data.isPresent() ? Maybe.just(data.get()) : Maybe.empty());
	}

	public Observable<UnencryptedData> getReadableData(RadixAddress address) {
		return getData(address)
			.flatMapMaybe(data -> identity.decrypt(data).toMaybe().onErrorComplete());
	}

	public Result storeData(Data data) {
		return this.storeData(data, getMyAddress());
	}

	public Result storeData(Data data, RadixAddress address) {
		StoreDataAction storeDataAction = new StoreDataAction(data, address);

		return executeTransaction(null, storeDataAction, null, null, null, null);
	}

	public Result storeData(Data data, RadixAddress address0, RadixAddress address1) {
		StoreDataAction storeDataAction = new StoreDataAction(data, address0, address1);

		return executeTransaction(null, storeDataAction, null, null, null, null);
	}

	public Observable<TransferTokensAction> getMyTokenTransfers() {
		return getTokenTransfers(getMyAddress());
	}

	public Observable<TransferTokensAction> getTokenTransfers(RadixAddress address) {
		Objects.requireNonNull(address);

		pull(address);

		return ledger.getAtomStore().getAtoms(address)
			.filter(AtomObservation::isStore)
			.map(AtomObservation::getAtom)
			.flatMapIterable(tokenTransferTranslator::fromAtom);
	}

	public Observable<Map<TokenClassReference, BigDecimal>> getBalance(RadixAddress address) {
		Objects.requireNonNull(address);

		pull(address);

		return tokenBalanceStore.getState(address)
			.map(TokenBalanceState::getBalance)
			.map(map -> map.entrySet().stream().collect(
				Collectors.toMap(Entry::getKey, e -> e.getValue().getAmount())
			));
	}

	public Observable<BigDecimal> getMyBalance(TokenClassReference tokenClassReference) {
		return getBalance(getMyAddress(), tokenClassReference);
	}

	public Observable<BigDecimal> getBalance(RadixAddress address, TokenClassReference token) {
		Objects.requireNonNull(token);

		return getBalance(address)
			.map(balances -> Optional.ofNullable(balances.get(token)).orElse(BigDecimal.ZERO));
	}

	/**
	 * Creates a third party token into the user's account
	 *
	 * @param name The name of the token to create
	 * @param iso The symbol of the token to create
	 * @param description A description of the token
	 * @param initialSupply The initial amount of supply of this token
	 * @param tokenSupplyType The type of supply for this token: Fixed or Mutable
	 * @return result of the transaction
	 */
	public Result createToken(
		String name,
		String iso,
		String description,
		long initialSupply,
		TokenSupplyType tokenSupplyType
	) {
		CreateTokenAction tokenCreation = new CreateTokenAction(getMyAddress(), name, iso, description, initialSupply, tokenSupplyType);
		return executeTransaction(null, null, tokenCreation, null, null, null);
	}

	/**
	 * Mints an amount of new tokens into the user's account
	 *
	 * @param iso The symbol of the token to mint
	 * @param amount The amount to mint
	 * @return result of the transaction
	 */
	public Result mintTokens(String iso, long amount) {
		MintTokensAction mintTokensAction = new MintTokensAction(TokenClassReference.of(getMyAddress(), iso), amount);
		return executeTransaction(null, null, null, mintTokensAction, null, null);
	}


	/**
	 * Burns an amount of tokens in the user's account
	 *
	 * @param iso The symbol of the token to mint
	 * @param amount The amount to mint
	 * @return result of the transaction
	 */
	public Result burnTokens(String iso, long amount) {
		BurnTokensAction burnTokensAction = new BurnTokensAction(TokenClassReference.of(getMyAddress(), iso), amount);
		return executeTransaction(null, null, null, null, burnTokensAction, null);
	}

	/**
	 * Sends an amount of a token to an address
	 *
	 * @param to the address to send tokens to
	 * @param amount the amount and token type
	 * @return result of the transaction
	 */
	public Result sendTokens(RadixAddress to, BigDecimal amount, TokenClassReference token) {
		return transferTokens(getMyAddress(), to, amount, token);
	}


	/**
	 * Sends an amount of a token with a message attachment to an address
	 *
	 * @param to the address to send tokens to
	 * @param amount the amount and token type
	 * @param message message to be encrypted and attached to transfer
	 * @return result of the transaction
	 */
	public Result sendTokensWithMessage(RadixAddress to, BigDecimal amount, TokenClassReference token, @Nullable String message) {
		return sendTokensWithMessage(to, amount, token, message, null);
	}

	/**
	 * Sends an amount of a token with a message attachment to an address
	 *
	 * @param to the address to send tokens to
	 * @param amount the amount and token type
	 * @param message message to be encrypted and attached to transfer
	 * @return result of the transaction
	 */
	public Result sendTokensWithMessage(
		RadixAddress to,
		BigDecimal amount,
		TokenClassReference token,
		@Nullable String message,
		@Nullable byte[] unique
	) {
		final Data attachment;
		if (message != null) {
			attachment = new DataBuilder()
				.addReader(to.getPublicKey())
				.addReader(getMyPublicKey())
				.bytes(message.getBytes()).build();
		} else {
			attachment = null;
		}

		return transferTokens(getMyAddress(), to, amount, token, attachment, unique);
	}

	/**
	 * Sends an amount of a token with a data attachment to an address
	 *
	 * @param to the address to send tokens to
	 * @param amount the amount and token type
	 * @param attachment the data attached to the transaction
	 * @return result of the transaction
	 */
	public Result sendTokens(RadixAddress to, BigDecimal amount, TokenClassReference token, @Nullable Data attachment) {
		return transferTokens(getMyAddress(), to, amount, token, attachment);
	}

	/**
	 * Sends an amount of a token with a data attachment to an address with a unique property
	 * meaning that no other transaction can be executed with the same unique bytes
	 *
	 * @param to the address to send tokens to
	 * @param amount the amount and token type
	 * @param attachment the data attached to the transaction
	 * @param unique the bytes representing the unique id of this transaction
	 * @return result of the transaction
	 */
	public Result sendTokens(
		RadixAddress to,
		BigDecimal amount,
		TokenClassReference token,
		@Nullable Data attachment,
		@Nullable byte[] unique
	) {
		return transferTokens(getMyAddress(), to, amount, token, attachment, unique);
	}

	public Result transferTokens(RadixAddress from, RadixAddress to, BigDecimal amount, TokenClassReference token) {
		return transferTokens(from, to, amount, token, null);
	}

	public Result transferTokens(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenClassReference token,
		@Nullable Data attachment
	) {
		return transferTokens(from, to, amount, token, attachment, null);
	}

	public Result transferTokens(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenClassReference token,
		@Nullable Data attachment,
		@Nullable byte[] unique // TODO: make unique immutable
	) {
		Objects.requireNonNull(from);
		Objects.requireNonNull(to);
		Objects.requireNonNull(amount);
		Objects.requireNonNull(token);

		final TransferTokensAction transferTokensAction =
			TransferTokensAction.create(from, to, amount, token, attachment);
		final UniqueProperty uniqueProperty;
		if (unique != null) {
			// Unique Property must be the from address so that all validation occurs in a single shard.
			// Once multi-shard validation is implemented this constraint can be removed.
			uniqueProperty = new UniqueProperty(unique, from);
		} else {
			uniqueProperty = null;
		}

		return executeTransaction(transferTokensAction, null, null, null, null, uniqueProperty);
	}

	// TODO: make this more generic
	private Result executeTransaction(
		@Nullable TransferTokensAction transferTokensAction,
		@Nullable StoreDataAction storeDataAction,
		@Nullable CreateTokenAction tokenCreation,
		@Nullable MintTokensAction mintTokensAction,
		@Nullable BurnTokensAction burnTokensAction,
		@Nullable UniqueProperty uniqueProperty
	) {
		if (transferTokensAction != null) {
			pull(transferTokensAction.getFrom());
		}

		if (burnTokensAction != null) {
			pull(burnTokensAction.getTokenClassReference().getAddress());
		}

		Single<List<SpunParticle>> atomParticles =
			Observable.concatArray(
//				Observable.just(uniquePropertyTranslator.map(uniqueProperty)),
				transferTokensAction != null ? tokenBalanceStore.getState(transferTokensAction.getFrom())
					.firstOrError().toObservable()
					.map(s -> tokenTransferTranslator.map(transferTokensAction, s)) : Observable.empty(),
				Observable.just(dataStoreTranslator.map(storeDataAction)),
				Observable.just(tokenMapper.map(tokenCreation)),
				Observable.just(mintTokensActionMapper.map(mintTokensAction)),
				burnTokensAction != null ? tokenBalanceStore.getState(burnTokensAction.getTokenClassReference().getAddress())
					.firstOrError().toObservable()
					.map(s -> burnTokensActionMapper.map(burnTokensAction, s)) : Observable.empty(),
				Observable.just(
					Collections.singletonList(SpunParticle.up(new TimestampParticle(System.currentTimeMillis())))
				)
			)
			.<List<SpunParticle>>scanWith(
				ArrayList::new,
				(a, b) -> Stream.concat(a.stream(), b.stream()).collect(Collectors.toList())
			)
			.lastOrError()
			.map(particles -> {
				List<SpunParticle> allParticles = new ArrayList<>(particles);
				allParticles.addAll(feeMapper.map(particles, universe, getMyPublicKey()));
				return allParticles;
			});

		ConnectableObservable<AtomSubmissionUpdate> updates = atomParticles
			.map(list -> new UnsignedAtom(new Atom(list)))
			.flatMap(identity::sign)
			.flatMapObservable(ledger.getAtomSubmitter()::submitAtom)
			.doOnNext(update -> {
				//TODO: retry on collision
				if (update.getState() == AtomSubmissionState.COLLISION) {
					JsonObject data = update.getData().getAsJsonObject();
					String jsonPointer = data.getAsJsonPrimitive("pointerToIssue").getAsString();
					LOGGER.info("ParticleConflict: pointer({}) cause({}) atom({})",
						jsonPointer,
						data.getAsJsonPrimitive("message").getAsString(),
						Serialize.getInstance().toJson(update.getAtom(), Output.ALL)
					);
				}
			})
			.replay();

		updates.connect();

		return new Result(updates);
	}
}
