package com.radixdlt.client.application;

import com.google.gson.JsonObject;
import com.radixdlt.client.application.actions.DataStore;
import com.radixdlt.client.application.actions.TokenTransfer;
import com.radixdlt.client.application.actions.UniqueProperty;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.application.objects.Data.DataBuilder;
import com.radixdlt.client.application.translate.ApplicationStore;
import com.radixdlt.client.application.translate.TokenBalanceReducer;
import com.radixdlt.client.application.translate.TokenReducer;
import com.radixdlt.client.application.translate.TokenState;
import com.radixdlt.client.application.objects.UnencryptedData;
import com.radixdlt.client.application.translate.TokenBalanceState;
import com.radixdlt.client.application.translate.DataStoreTranslator;
import com.radixdlt.client.application.translate.TokenTransferTranslator;
import com.radixdlt.client.application.translate.UniquePropertyTranslator;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomFeeConsumableBuilder;
import com.radixdlt.client.core.atoms.TokenRef;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.RadixUniverse.Ledger;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AccountReference;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.core.atoms.particles.AtomFeeConsumable;
import com.radixdlt.client.core.atoms.particles.ChronoParticle;
import com.radixdlt.client.core.atoms.particles.Minted;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.TokenParticle;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.atoms.particles.TokenParticle.MintPermissions;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.core.serialization.RadixJson;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.annotations.Nullable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.observables.ConnectableObservable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	private final ApplicationStore<Map<TokenRef, TokenState>> tokenStore;
	private final ApplicationStore<TokenBalanceState> tokenBalanceStore;

	// TODO: Translator from particles to atom
	private final Supplier<AtomBuilder> atomBuilderSupplier;

	private final Ledger ledger;

	private RadixApplicationAPI(
		RadixIdentity identity,
		RadixUniverse universe,
		DataStoreTranslator dataStoreTranslator,
		Supplier<AtomBuilder> atomBuilderSupplier,
		Ledger ledger
	) {
		this.identity = identity;
		this.universe = universe;
		this.dataStoreTranslator = dataStoreTranslator;
		this.tokenTransferTranslator = new TokenTransferTranslator(universe);
		this.uniquePropertyTranslator = new UniquePropertyTranslator();

		this.tokenStore = new ApplicationStore<>(ledger.getParticleStore(), new TokenReducer());
		this.tokenBalanceStore = new ApplicationStore<>(ledger.getParticleStore(), new TokenBalanceReducer());

		this.atomBuilderSupplier = atomBuilderSupplier;
		this.ledger = ledger;
	}

	public static RadixApplicationAPI create(RadixIdentity identity) {
		Objects.requireNonNull(identity);
		return create(identity, RadixUniverse.getInstance(), DataStoreTranslator.getInstance(), AtomBuilder::new);
	}

	public static RadixApplicationAPI create(
		RadixIdentity identity,
		RadixUniverse universe,
		DataStoreTranslator dataStoreTranslator,
		Supplier<AtomBuilder> atomBuilderSupplier
	) {
		Objects.requireNonNull(identity);
		Objects.requireNonNull(universe);
		Objects.requireNonNull(atomBuilderSupplier);
		return new RadixApplicationAPI(identity, universe, dataStoreTranslator, atomBuilderSupplier, universe.getLedger());
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

	public TokenRef getNativeToken() {
		return universe.getNativeToken();
	}

	public Observable<TokenState> getNativeTokenState() {
		return getToken(getNativeToken());
	}

	public Observable<Map<TokenRef, TokenState>> getTokens(RadixAddress address) {
		pull(address);

		return tokenStore.getState(address);
	}

	public Observable<Map<TokenRef, TokenState>> getMyTokens() {
		return getTokens(getMyAddress());
	}

	public Observable<TokenState> getToken(TokenRef ref) {
		pull(universe.getAddressFrom(ref.getAddress().getKey()));

		return tokenStore.getState(universe.getAddressFrom(ref.getAddress().getKey()))
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
		DataStore dataStore = new DataStore(data, address);

		AtomBuilder atomBuilder = atomBuilderSupplier.get();
		ConnectableObservable<AtomSubmissionUpdate> updates = dataStoreTranslator.translate(dataStore, atomBuilder)
			.andThen(Single.fromCallable(
				() -> atomBuilder.buildWithPOWFee(universe.getMagic(), address.getPublicKey(), universe.getPOWToken())
			))
			.flatMap(identity::sign)
			.flatMapObservable(ledger.getAtomSubmitter()::submitAtom)
			.replay();

		updates.connect();

		return new Result(updates);
	}

	public Result storeData(Data data, RadixAddress address0, RadixAddress address1) {
		DataStore dataStore = new DataStore(data, address0, address1);

		AtomBuilder atomBuilder = atomBuilderSupplier.get();
		ConnectableObservable<AtomSubmissionUpdate> updates = dataStoreTranslator.translate(dataStore, atomBuilder)
			.andThen(Single.fromCallable(
				() -> atomBuilder.buildWithPOWFee(universe.getMagic(), address0.getPublicKey(), universe.getPOWToken())
			))
			.flatMap(identity::sign)
			.flatMapObservable(ledger.getAtomSubmitter()::submitAtom)
			.replay();

		updates.connect();

		return new Result(updates);
	}

	public Observable<TokenTransfer> getMyTokenTransfers() {
		return getTokenTransfers(getMyAddress());
	}

	public Observable<TokenTransfer> getTokenTransfers(RadixAddress address) {
		Objects.requireNonNull(address);

		pull(address);

		return ledger.getAtomStore().getAtoms(address)
			.flatMapIterable(tokenTransferTranslator::fromAtom);
	}

	public Observable<Map<TokenRef, BigDecimal>> getBalance(RadixAddress address) {
		Objects.requireNonNull(address);

		pull(address);

		return tokenBalanceStore.getState(address)
			.map(TokenBalanceState::getBalance)
			.map(map -> map.entrySet().stream().collect(
				Collectors.toMap(Entry::getKey, e -> e.getValue().getAmount())
			));
	}

	public Observable<BigDecimal> getMyBalance(TokenRef tokenRef) {
		return getBalance(getMyAddress(), tokenRef);
	}

	public Observable<BigDecimal> getBalance(RadixAddress address, TokenRef token) {
		Objects.requireNonNull(token);

		return getBalance(address)
			.map(balances -> Optional.ofNullable(balances.get(token)).orElse(BigDecimal.ZERO));
	}

	// TODO: refactor to access a TokenTranslator
	public Result createFixedSupplyToken(String name, String iso, String description, long fixedSupply) {
		AccountReference account = new AccountReference(getMyPublicKey());
		TokenParticle token = new TokenParticle(account, name, iso, description, MintPermissions.SAME_ATOM_ONLY, null);
		Minted minted = new Minted(
			fixedSupply * TokenRef.SUB_UNITS,
			account,
			System.currentTimeMillis(),
			token.getTokenRef(),
			System.currentTimeMillis() / 60000L + 60000
		);

		UnsignedAtom unsignedAtom = atomBuilderSupplier.get()
			.addParticle(token)
			.addParticle(minted)
			.buildWithPOWFee(universe.getMagic(), getMyPublicKey(), universe.getPOWToken());

		ConnectableObservable<AtomSubmissionUpdate> updates = identity.sign(unsignedAtom)
			.flatMapObservable(ledger.getAtomSubmitter()::submitAtom)
			.replay();

		updates.connect();

		return new Result(updates);
	}

	/**
	 * Sends an amount of a token to an address
	 *
	 * @param to the address to send tokens to
	 * @param amount the amount and token type
	 * @return result of the transaction
	 */
	public Result sendTokens(RadixAddress to, BigDecimal amount, TokenRef token) {
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
	public Result sendTokensWithMessage(RadixAddress to, BigDecimal amount, TokenRef token, @Nullable String message) {
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
		TokenRef token,
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
	public Result sendTokens(RadixAddress to, BigDecimal amount, TokenRef token, @Nullable Data attachment) {
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
		TokenRef token,
		@Nullable Data attachment,
		@Nullable byte[] unique
	) {
		return transferTokens(getMyAddress(), to, amount, token, attachment, unique);
	}

	public Result transferTokens(RadixAddress from, RadixAddress to, BigDecimal amount, TokenRef token) {
		return transferTokens(from, to, amount, token, null);
	}

	public Result transferTokens(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenRef token,
		@Nullable Data attachment
	) {
		return transferTokens(from, to, amount, token, attachment, null);
	}

	public Result transferTokens(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenRef token,
		@Nullable Data attachment,
		@Nullable byte[] unique // TODO: make unique immutable
	) {
		Objects.requireNonNull(from);
		Objects.requireNonNull(to);
		Objects.requireNonNull(amount);
		Objects.requireNonNull(token);

		final TokenTransfer tokenTransfer =
			TokenTransfer.create(from, to, amount, token, attachment);
		final UniqueProperty uniqueProperty;
		if (unique != null) {
			// Unique Property must be the from address so that all validation occurs in a single shard.
			// Once multi-shard validation is implemented this constraint can be removed.
			uniqueProperty = new UniqueProperty(unique, from);
		} else {
			uniqueProperty = null;
		}

		return executeTransaction(tokenTransfer, uniqueProperty);
	}

	// TODO: make this more generic
	private Result executeTransaction(TokenTransfer tokenTransfer, @Nullable UniqueProperty uniqueProperty) {
		Objects.requireNonNull(tokenTransfer);

		pull(tokenTransfer.getFrom());

		Observable<TokenBalanceState> tokenBalanceState = tokenBalanceStore.getState(tokenTransfer.getFrom());

		Single<List<Particle>> atomParticles =
			Observable.concat(
				Observable.just(uniquePropertyTranslator.map(uniqueProperty)),
				Observable.combineLatest(
					Observable.just(tokenTransfer),
					tokenBalanceState,
					tokenTransferTranslator::map
				).firstOrError().toObservable(),
				Observable.just(Collections.singletonList(new ChronoParticle(System.currentTimeMillis())))
			)
			.<List<Particle>>scanWith(ArrayList::new, (a, b) -> Stream.concat(a.stream(), b.stream()).collect(Collectors.toList()))
			.lastOrError()
			.map(particles -> {
				List<Particle> allParticles = new ArrayList<>(particles);
				Atom atom = new Atom(particles);
				AtomFeeConsumable fee = new AtomFeeConsumableBuilder()
					.powToken(universe.getPOWToken())
					.atom(atom)
					.owner(tokenTransfer.getFrom().getPublicKey())
					.pow(universe.getMagic(), 16)
					.build();
				allParticles.add(fee);
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
					String jsonPointer = data.getAsJsonPrimitive("pointerToConflict").getAsString();
					LOGGER.info("ParticleConflict: pointer({}) cause({}) atom({})",
						jsonPointer,
						data.getAsJsonPrimitive("cause").getAsString(),
						RadixJson.getGson().toJson(update.getAtom())
					);
				}
			})
			.replay();

		updates.connect();

		return new Result(updates);
	}
}
