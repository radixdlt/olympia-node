package com.radixdlt.client.application;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ActionToParticlesMapper;
import com.radixdlt.client.application.translate.tokenclasses.BurnTokensAction;
import com.radixdlt.client.application.translate.tokenclasses.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokenclasses.MintTokensAction;
import com.radixdlt.client.application.translate.data.SendMessageAction;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
import com.radixdlt.client.application.translate.data.DecryptedMessage;
import com.radixdlt.client.application.translate.tokens.TokenTransfer;
import com.radixdlt.client.application.translate.ActionStore;
import com.radixdlt.client.application.translate.tokenclasses.BurnTokensActionMapper;
import com.radixdlt.client.application.translate.tokenclasses.MintTokensActionMapper;
import com.radixdlt.client.application.translate.data.AtomToDecryptedMessageMapper;
import com.radixdlt.client.application.translate.tokens.AtomToTokenTransfersMapper;
import com.radixdlt.client.atommodel.timestamp.TimestampParticle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.crypto.ECKeyPairGenerator;
import io.reactivex.observables.ConnectableObservable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.client.Serialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.radixdlt.client.application.translate.tokenclasses.CreateTokenAction;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.identity.Data;
import com.radixdlt.client.application.identity.Data.DataBuilder;
import com.radixdlt.client.application.translate.ApplicationStore;
import com.radixdlt.client.application.translate.data.SendMessageToParticlesMapper;
import com.radixdlt.client.application.translate.FeeMapper;
import com.radixdlt.client.application.translate.PowFeeMapper;
import com.radixdlt.client.application.translate.tokens.TokenBalanceReducer;
import com.radixdlt.client.application.translate.tokens.TokenBalanceState;
import com.radixdlt.client.application.translate.tokenclasses.CreateTokenToParticlesMapper;
import com.radixdlt.client.application.translate.tokenclasses.TokenReducer;
import com.radixdlt.client.application.translate.tokenclasses.TokenState;
import com.radixdlt.client.application.translate.tokens.TransferTokensToParticlesMapper;
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

/**
 * The Radix Dapp API, a high level api which dapps can utilize. The class hides
 * the complexity of Atoms and cryptography and exposes a simple high level interface.
 */
public class RadixApplicationAPI {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixApplicationAPI.class);

	public static class Result {
		private final ConnectableObservable<AtomSubmissionUpdate> updates;
		private final Completable completable;

		private Result(ConnectableObservable<AtomSubmissionUpdate> updates) {
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

		private Result connect() {
			this.updates.connect();
			return this;
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
	private final UniquePropertyTranslator uniquePropertyTranslator;

	private final ActionStore<DecryptedMessage> messageActionStore;
	private final ActionStore<TokenTransfer> tokenTransferActionStore;
	private final ApplicationStore<Map<TokenClassReference, TokenState>> tokenStore;
	private final ApplicationStore<TokenBalanceState> tokenBalanceStore;

	private final List<ActionToParticlesMapper> actionToParticlesMappers;

	// TODO: Translator from particles to atom
	private final FeeMapper feeMapper;

	private final Ledger ledger;

	private RadixApplicationAPI(
		RadixIdentity identity,
		RadixUniverse universe,
		FeeMapper feeMapper,
		Ledger ledger
	) {
		this.identity = identity;
		this.universe = universe;

		// TODO: Utilize class loader to discover and load these modules
		this.uniquePropertyTranslator = new UniquePropertyTranslator();
		this.messageActionStore = new ActionStore<>(ledger.getAtomStore(), new AtomToDecryptedMessageMapper(universe));
		this.tokenBalanceStore = new ApplicationStore<>(ledger.getParticleStore(), new TokenBalanceReducer());
		this.tokenTransferActionStore = new ActionStore<>(ledger.getAtomStore(), new AtomToTokenTransfersMapper(universe));
		this.tokenStore = new ApplicationStore<>(ledger.getParticleStore(), new TokenReducer());

		this.actionToParticlesMappers = Arrays.asList(
			new SendMessageToParticlesMapper(ECKeyPairGenerator.newInstance()::generateKeyPair),
			new CreateTokenToParticlesMapper(),
			new MintTokensActionMapper(),
			new BurnTokensActionMapper(universe, addr -> {
				pull(addr);
				return tokenBalanceStore.getState(addr);
			}),
			new TransferTokensToParticlesMapper(universe, addr -> {
				pull(addr);
				return tokenBalanceStore.getState(addr);
			})
		);

		this.feeMapper = feeMapper;
		this.ledger = ledger;
	}

	public static RadixApplicationAPI create(RadixIdentity identity) {
		Objects.requireNonNull(identity);
		return create(
			identity,
			RadixUniverse.getInstance(),
			new PowFeeMapper(p -> new Atom(p).getHash(), new ProofOfWorkBuilder())
		);
	}

	public static RadixApplicationAPI create(
		RadixIdentity identity,
		RadixUniverse universe,
		FeeMapper feeMapper
	) {
		Objects.requireNonNull(identity);
		Objects.requireNonNull(universe);
		Objects.requireNonNull(feeMapper);
		return new RadixApplicationAPI(identity, universe, feeMapper, universe.getLedger());
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
	public Observable<TokenState> getNativeTokenClass() {
		return getTokenClass(getNativeTokenRef());
	}

	/**
	 * Returns a hot observable of the latest state of token classes at a given
	 * address
	 *
	 * @param address the address of the account to check
	 * @return a hot observable of the latest state of token classes
	 */
	public Observable<Map<TokenClassReference, TokenState>> getTokenClasses(RadixAddress address) {
		pull(address);

		return tokenStore.getState(address);
	}

	/**
	 * Returns a hot observable of the latest state of token classes at the user's
	 * address
	 *
	 * @return a hot observable of the latest state of token classes
	 */
	public Observable<Map<TokenClassReference, TokenState>> getMyTokenClasses() {
		return getTokenClasses(getMyAddress());
	}

	/**
	 * Returns a hot observable of the latest state of a given token
	 *
	 * @return a hot observable of the latest state of the token
	 */
	public Observable<TokenState> getTokenClass(TokenClassReference ref) {
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

	public Observable<DecryptedMessage> getMessages() {
		return getMessages(this.getMyAddress());
	}

	public Observable<DecryptedMessage> getMessages(RadixAddress address) {
		Objects.requireNonNull(address);

		pull(address);

		return messageActionStore.getActions(address, identity);
	}

	public Result sendMessage(byte[] data, boolean encrypt) {
		return this.sendMessage(data, encrypt, getMyAddress());
	}

	public Result sendMessage(byte[] data, boolean encrypt, RadixAddress address) {
		SendMessageAction sendMessageAction = new SendMessageAction(data, getMyAddress(), address, encrypt);

		return execute(sendMessageAction);
	}

	public Observable<TokenTransfer> getMyTokenTransfers() {
		return getTokenTransfers(getMyAddress());
	}

	public Observable<TokenTransfer> getTokenTransfers(RadixAddress address) {
		Objects.requireNonNull(address);

		pull(address);

		return tokenTransferActionStore.getActions(address, this.getMyIdentity());
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
		return execute(tokenCreation);
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
		return execute(mintTokensAction);
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
		return execute(burnTokensAction);
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
	public Result sendTokens(
		RadixAddress to,
		BigDecimal amount,
		TokenClassReference token,
		@Nullable String message
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

		return transferTokens(getMyAddress(), to, amount, token, attachment);
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



	public Result transferTokens(RadixAddress from, RadixAddress to, BigDecimal amount, TokenClassReference token) {
		return transferTokens(from, to, amount, token, null);
	}

	/**
	 * Sends an amount of a token with a data attachment to an address with a unique property
	 * meaning that no other transaction can be executed with the same unique bytes
	 *
	 * @param to the address to send tokens to
	 * @param amount the amount and token type
	 * @param attachment the data attached to the transaction
	 * @return result of the transaction
	 */
	public Result transferTokens(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenClassReference token,
		@Nullable Data attachment
	) {
		Objects.requireNonNull(from);
		Objects.requireNonNull(to);
		Objects.requireNonNull(amount);
		Objects.requireNonNull(token);

		final TransferTokensAction transferTokensAction =
			TransferTokensAction.create(from, to, amount, token, attachment);

		return execute(transferTokensAction);
	}

	/**
	 * Builds an unsigned atom given a user action. Note that this is
	 * method will always return a unique atom even if given the equivalent actions
	 *
	 * @param action the user action to translate an atom from
	 * @return the constructed atom
	 */
	public Single<UnsignedAtom> buildAtom(Action action) {
		return Observable.concat(
			this.actionToParticlesMappers.stream()
				.map(m -> m.map(action))
				.collect(Collectors.toList())
		)
		.concatWith(Observable.just(SpunParticle.up(new TimestampParticle(System.currentTimeMillis()))))
		.<List<SpunParticle>>scanWith(
			ArrayList::new,
			(a, b) -> Stream.concat(a.stream(), Stream.of(b)).collect(Collectors.toList())
		)
		.lastOrError()
		.map(particles -> {
			List<SpunParticle> allParticles = new ArrayList<>(particles);
			allParticles.addAll(feeMapper.map(particles, universe, getMyPublicKey()));
			return allParticles;
		})
		.map(particles -> new UnsignedAtom(new Atom(particles)));
	}

	private Result buildDisconnectedResult(Action action) {
		ConnectableObservable<AtomSubmissionUpdate> updates = this.buildAtom(action)
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
			}).replay();

		return new Result(updates);
	}

	/**
	 * Immediately executes a user action onto the ledger. Note that this method is NOT
	 * idempotent.
	 *
	 * @param action action to execute
	 * @return results of the execution
	 */
	public Result execute(Action action) {
		return this.buildDisconnectedResult(action).connect();
	}

	/**
	 * Executes actions sequentially. If an action fails, then the completable this method
	 * returns will call onError immediately. Note that this method is NEITHER idempotent
	 * NOR atomic (i.e. if an action fails, all previous actions to that would still have occurred).
	 *
	 * @param actions the action to execute sequentially
	 * @return completion status of all of the actions
	 */
	public Completable executeSequentially(Action... actions) {
		Completable completable = Observable.fromIterable(Arrays.asList(actions))
			.concatMapCompletable(a -> buildDisconnectedResult(a).connect().toCompletable()).cache();
		completable.subscribe();
		return completable;
	}
}
