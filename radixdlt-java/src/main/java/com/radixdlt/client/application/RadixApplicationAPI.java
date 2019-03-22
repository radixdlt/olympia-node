package com.radixdlt.client.application;

import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
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

import com.radixdlt.client.application.translate.tokens.MintAndTransferTokensAction;
import com.radixdlt.client.application.translate.tokens.MintAndTransferTokensActionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.radixdlt.client.application.identity.Data;
import com.radixdlt.client.application.identity.Data.DataBuilder;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ActionExecutionException.ActionExecutionExceptionBuilder;
import com.radixdlt.client.application.translate.ActionStore;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.ApplicationStore;
import com.radixdlt.client.application.translate.AtomErrorToExceptionReasonMapper;
import com.radixdlt.client.application.translate.AtomToExecutedActionsMapper;
import com.radixdlt.client.application.translate.FeeMapper;
import com.radixdlt.client.application.translate.ParticleReducer;
import com.radixdlt.client.application.translate.PowFeeMapper;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.StatelessActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.atomic.AtomicToParticleGroupsMapper;
import com.radixdlt.client.application.translate.data.AtomToDecryptedMessageMapper;
import com.radixdlt.client.application.translate.data.DecryptedMessage;
import com.radixdlt.client.application.translate.data.SendMessageAction;
import com.radixdlt.client.application.translate.data.SendMessageToParticleGroupsMapper;
import com.radixdlt.client.application.translate.tokens.BurnTokensAction;
import com.radixdlt.client.application.translate.tokens.BurnTokensActionMapper;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.CreateTokenToParticleGroupsMapper;
import com.radixdlt.client.application.translate.tokens.MintTokensAction;
import com.radixdlt.client.application.translate.tokens.MintTokensActionMapper;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionsReducer;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionsState;
import com.radixdlt.client.application.translate.tokens.TokenState;
import com.radixdlt.client.application.translate.tokens.AtomToTokenTransfersMapper;
import com.radixdlt.client.application.translate.tokens.TokenBalanceReducer;
import com.radixdlt.client.application.translate.tokens.TokenBalanceState;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.application.translate.tokens.TokenTransfer;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
import com.radixdlt.client.application.translate.tokens.TransferTokensToParticleGroupsMapper;
import com.radixdlt.client.application.translate.unique.AlreadyUsedUniqueIdReasonMapper;
import com.radixdlt.client.application.translate.unique.PutUniqueIdToParticleGroupsMapper;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.RadixUniverse.Ledger;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.ledger.AtomObservation;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.crypto.ECKeyPairGenerator;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType;
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
		private final ConnectableObservable<SubmitAtomAction> updates;
		private final Completable completable;

		private Result(ConnectableObservable<SubmitAtomAction> updates, Completable completable) {
			this.updates = updates;
			this.completable = completable;
		}

		private Result connect() {
			this.updates.connect();
			return this;
		}

		/**
		 * A low level interface, returns an a observable of the status of an atom submission as it occurs.
		 * @return observable of atom submission status
		 */
		public Observable<SubmitAtomAction> toObservable() {
			return updates;
		}

		/**
		 * A high level interface, returns completable of successful completion of action execution.
		 * If there is an with the ledger, the completable throws an ActionExecutionException.
		 *
		 * @return completable of successful execution of action onto ledger.
		 */
		public Completable toCompletable() {
			return completable;
		}
	}

	private final RadixIdentity identity;
	private final RadixUniverse universe;

	private final Map<Class<?>, ActionStore<?>> actionStores;

	private final Map<Class<? extends ApplicationState>, ApplicationStore<? extends ApplicationState>> applicationStores;

	/**
	 * Action to Particle Mappers which can mapToParticleGroups without any dependency on ledger state
	 */
	private final List<StatelessActionToParticleGroupsMapper> statelessActionToParticleGroupsMappers;

	/**
	 * Action to Particle Mappers which require dependencies on the ledger
	 */
	private final List<StatefulActionToParticleGroupsMapper> statefulActionToParticleGroupsMappers;

	/**
	 * Mapper of atom submission errors to application level errors
	 */
	private final List<AtomErrorToExceptionReasonMapper> atomErrorMappers;

	// TODO: Translator from particles to atom
	private final FeeMapper feeMapper;

	private final Ledger ledger;

	private RadixApplicationAPI(
		RadixIdentity identity,
		RadixUniverse universe,
		FeeMapper feeMapper,
		Ledger ledger,
		List<StatelessActionToParticleGroupsMapper> statelessActionToParticleGroupsMappers,
		List<StatefulActionToParticleGroupsMapper> statefulActionToParticleGroupsMappers,
		List<ParticleReducer<? extends ApplicationState>> particleReducers,
		List<AtomToExecutedActionsMapper<? extends Object>> atomMappers,
		List<AtomErrorToExceptionReasonMapper> atomErrorMappers
	) {
		Objects.requireNonNull(identity);
		Objects.requireNonNull(universe);
		Objects.requireNonNull(feeMapper);
		Objects.requireNonNull(ledger);
		Objects.requireNonNull(statelessActionToParticleGroupsMappers);
		Objects.requireNonNull(statefulActionToParticleGroupsMappers);
		Objects.requireNonNull(particleReducers);
		Objects.requireNonNull(atomErrorMappers);

		this.identity = identity;
		this.universe = universe;
		this.actionStores = atomMappers.stream().collect(Collectors.toMap(
			AtomToExecutedActionsMapper::actionClass,
			m -> new ActionStore<>(ledger.getAtomStore(), m)
		));
		this.applicationStores = particleReducers.stream().collect(Collectors.toMap(
			ParticleReducer::stateClass,
			r -> new ApplicationStore<>(ledger.getParticleStore(), r)
		));
		this.statefulActionToParticleGroupsMappers = statefulActionToParticleGroupsMappers;
		this.statelessActionToParticleGroupsMappers = statelessActionToParticleGroupsMappers;
		this.atomErrorMappers = atomErrorMappers;
		this.feeMapper = feeMapper;
		this.ledger = ledger;
	}

	public static class RadixApplicationAPIBuilder {
		private RadixIdentity identity;
		private RadixUniverse universe;
		private FeeMapper feeMapper;
		private List<ParticleReducer<? extends ApplicationState>> reducers = new ArrayList<>();
		private List<StatelessActionToParticleGroupsMapper> statelessActionToParticleGroupsMappers = new ArrayList<>();
		private List<StatefulActionToParticleGroupsMapper> statefulActionToParticleGroupsMappers = new ArrayList<>();
		private List<AtomToExecutedActionsMapper<? extends Object>> atomMappers = new ArrayList<>();
		private List<AtomErrorToExceptionReasonMapper> atomErrorMappers = new ArrayList<>();

		public RadixApplicationAPIBuilder() {
		}

		public RadixApplicationAPIBuilder addAtomMapper(AtomToExecutedActionsMapper<? extends Object> atomMapper) {
			this.atomMappers.add(atomMapper);
			return this;
		}

		public RadixApplicationAPIBuilder addStatelessParticlesMapper(StatelessActionToParticleGroupsMapper mapper) {
			this.statelessActionToParticleGroupsMappers.add(mapper);
			return this;
		}

		public RadixApplicationAPIBuilder addStatefulParticlesMapper(StatefulActionToParticleGroupsMapper mapper) {
			this.statefulActionToParticleGroupsMappers.add(mapper);
			return this;
		}

		public <T extends ApplicationState> RadixApplicationAPIBuilder addReducer(ParticleReducer<T> reducer) {
			this.reducers.add(reducer);
			return this;
		}

		public RadixApplicationAPIBuilder addAtomErrorMapper(AtomErrorToExceptionReasonMapper atomErrorMapper) {
			this.atomErrorMappers.add(atomErrorMapper);
			return this;
		}

		public RadixApplicationAPIBuilder feeMapper(FeeMapper feeMapper) {
			this.feeMapper = feeMapper;
			return this;
		}

		public RadixApplicationAPIBuilder defaultFeeMapper() {
			this.feeMapper = new PowFeeMapper(Atom::getHash, new ProofOfWorkBuilder());
			return this;
		}

		public RadixApplicationAPIBuilder identity(RadixIdentity identity) {
			this.identity = identity;
			return this;
		}

		public RadixApplicationAPIBuilder universe(RadixUniverse universe) {
			this.universe = universe;
			return this;
		}

		public RadixApplicationAPI build() {
			Objects.requireNonNull(this.identity, "Identity must be specified");
			Objects.requireNonNull(this.feeMapper, "Fee Mapper must be specified");

			if (this.universe == null && !RadixUniverse.isInstantiated() ) {
				throw new IllegalStateException("No universe available.");
			}

			final RadixUniverse universe = this.universe == null ? RadixUniverse.getInstance() : this.universe;
			final Ledger ledger = universe.getLedger();
			final FeeMapper feeMapper = this.feeMapper;
			final RadixIdentity identity = this.identity;
			final List<ParticleReducer<? extends ApplicationState>> reducers = this.reducers;

			return new RadixApplicationAPI(
				identity,
				universe,
				feeMapper,
				ledger,
				statelessActionToParticleGroupsMappers,
				statefulActionToParticleGroupsMappers,
				reducers,
				atomMappers,
				atomErrorMappers
			);
		}
	}

	/**
	 * Creates an API with the default actions and reducers
	 *
	 * @param identity the identity of user of API
	 * @return an api instance
	 */
	public static RadixApplicationAPI create(RadixIdentity identity) {
		Objects.requireNonNull(identity);

		return createDefaultBuilder()
				.identity(identity)
				.build();
	}

	/**
	 * Creates a default API builder with the default actions and reducers without an identity
	 *
	 * @return an api builder instance
	 */
	public static RadixApplicationAPIBuilder createDefaultBuilder() {
		return new RadixApplicationAPIBuilder()
			.defaultFeeMapper()
			.addStatelessParticlesMapper(new SendMessageToParticleGroupsMapper(ECKeyPairGenerator.newInstance()::generateKeyPair))
			.addStatelessParticlesMapper(new CreateTokenToParticleGroupsMapper())
			.addStatelessParticlesMapper(new PutUniqueIdToParticleGroupsMapper())
			.addStatelessParticlesMapper(new AtomicToParticleGroupsMapper())
			.addStatefulParticlesMapper(new MintTokensActionMapper())
			.addStatefulParticlesMapper(new MintAndTransferTokensActionMapper())
			.addStatefulParticlesMapper(new BurnTokensActionMapper())
			.addStatefulParticlesMapper(new TransferTokensToParticleGroupsMapper())
			.addReducer(new TokenDefinitionsReducer())
			.addReducer(new TokenBalanceReducer())
			.addAtomMapper(new AtomToDecryptedMessageMapper())
			.addAtomMapper(new AtomToTokenTransfersMapper())
			.addAtomErrorMapper(new AlreadyUsedUniqueIdReasonMapper());
	}

	public Observable<RadixNetworkState> getNetworkState() {
		return this.universe.getNetworkState();
	}

	private ApplicationStore<? extends ApplicationState> getStore(Class<? extends ApplicationState> storeClass) {
		ApplicationStore<? extends ApplicationState> store = this.applicationStores.get(storeClass);
		if (store == null) {
			throw new IllegalArgumentException("No store available for class: " + storeClass);
		}
		return store;
	}

	private ActionStore<?> getActionStore(Class<?> actionClass) {
		ActionStore<?> store = actionStores.get(actionClass);
		if (store == null) {
			throw new IllegalArgumentException("No store available for class: " + actionClass);
		}
		return store;
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
			return ledger.getAtomPuller().pull(address).subscribe();
		} else {
			return Disposables.disposed();
		}
	}

	/**
	 * Returns the native Token Reference found in the genesis atom
	 *
	 * @return the native token reference
	 */
	public TokenDefinitionReference getNativeTokenRef() {
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
	 * Returns a never ending hot observable of the actions performed at a given address.
	 * If the given address is not currently being pulled this will pull for atoms in that
	 * address automatically until the observable is disposed.
	 *
	 * @param actionClass the Action class
	 * @param address the address to retrieve the state of
	 * @param <T> the Action class
	 * @return a hot observable of the actions at the given address
	 */
	public <T> Observable<T> getActions(Class<T> actionClass, RadixAddress address) {
		final Observable<AtomObservation> atomsPulled = ledger.getAtomPuller() != null
			? ledger.getAtomPuller().pull(address)
			: Observable.never();
		Observable<AtomObservation> auto = atomsPulled.publish()
			.refCount(2);
		Disposable d = auto.subscribe();

		return this.getActionStore(actionClass).getActions(address, identity)
			.map(actionClass::cast)
			.publish()
			.refCount()
			.doOnSubscribe(disposable -> auto.subscribe().dispose())
			.doOnError(e -> d.dispose())
			.doOnDispose(d::dispose)
			.doOnComplete(d::dispose);
	}

	/**
	 * Returns a never ending hot observable of the state of a given address.
	 * If the given address is not currently being pulled this will pull for atoms in that
	 * address automatically until the observable is disposed.
	 *
	 * @param stateClass the ApplicationState class
	 * @param address the address to retrieve the state of
	 * @param <T> the ApplicationState class
	 * @return a hot observable of a state of the given address
	 */
	public <T extends ApplicationState> Observable<T> getState(Class<T> stateClass, RadixAddress address) {
		final Observable<AtomObservation> atomsPulled = ledger.getAtomPuller() != null
				? ledger.getAtomPuller().pull(address)
				: Observable.never();
		Observable<AtomObservation> auto = atomsPulled
			.publish()
			.refCount(2);
		Disposable d = auto.subscribe();

		return this.getStore(stateClass).getState(address)
			.map(stateClass::cast)
			.publish()
			.refCount()
			.doOnSubscribe(disposable -> auto.subscribe().dispose())
			.doOnError(e -> d.dispose())
			.doOnDispose(d::dispose)
			.doOnComplete(d::dispose);
	}

	/**
	 * Returns a hot observable of the latest state of token classes at a given
	 * address
	 *
	 * @param address the address of the account to check
	 * @return a hot observable of the latest state of token classes
	 */
	public Observable<TokenDefinitionsState> getTokenClasses(RadixAddress address) {
		return getState(TokenDefinitionsState.class, address);
	}

	/**
	 * Returns a hot observable of the latest state of token classes at the user's
	 * address
	 *
	 * @return a hot observable of the latest state of token classes
	 */
	public Observable<TokenDefinitionsState> getMyTokenClasses() {
		return getTokenClasses(getMyAddress());
	}

	/**
	 * Returns a hot observable of the latest state of a given token
	 *
	 * @return a hot observable of the latest state of the token
	 */
	public Observable<TokenState> getTokenClass(TokenDefinitionReference ref) {
		return this.getTokenClasses(ref.getAddress())
			.flatMapMaybe(m -> Optional.ofNullable(m.getState().get(ref)).map(Maybe::just).orElse(Maybe.empty()));
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
		return getActions(DecryptedMessage.class, address);
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
		return getActions(TokenTransfer.class, address);
	}

	public Observable<Map<TokenDefinitionReference, BigDecimal>> getBalance(RadixAddress address) {
		Objects.requireNonNull(address);
		return getState(TokenBalanceState.class, address)
			.map(TokenBalanceState::getBalance)
			.map(map -> map.entrySet().stream().collect(
				Collectors.toMap(Entry::getKey, e -> e.getValue().getAmount())
			));
	}

	public Observable<BigDecimal> getMyBalance(TokenDefinitionReference tokenDefinitionReference) {
		return getBalance(getMyAddress(), tokenDefinitionReference);
	}

	public Observable<BigDecimal> getBalance(RadixAddress address, TokenDefinitionReference token) {
		Objects.requireNonNull(token);

		return getBalance(address)
			.map(balances -> Optional.ofNullable(balances.get(token)).orElse(BigDecimal.ZERO));
	}

	/**
	 * Creates a third party multi-issuance token into the user's account with
	 * zero initial supply, 10^-18 granularity and no description.
	 *
	 * @param name The name of the token to create
	 * @param iso The symbol of the token to create
	 * @return result of the transaction
	 */
	public Result createMultiIssuanceToken(String name, String iso) {
		final CreateTokenAction tokenCreation = CreateTokenAction.create(
			getMyAddress(),
			name,
			iso,
			null,
			BigDecimal.ZERO,
			TokenUnitConversions.getMinimumGranularity(),
			TokenSupplyType.MUTABLE
		);
		return execute(tokenCreation);
	}

	/**
	 * Creates a third party multi-issuance token into the user's account with
	 * zero initial supply and 10^-18 granularity
	 *
	 * @param name The name of the token to create
	 * @param iso The symbol of the token to create
	 * @param description A description of the token
	 * @return result of the transaction
	 */
	public Result createMultiIssuanceToken(
		String name,
		String iso,
		String description
	) {
		final CreateTokenAction tokenCreation = CreateTokenAction.create(
			getMyAddress(),
			name,
			iso,
			description,
			BigDecimal.ZERO,
			TokenUnitConversions.getMinimumGranularity(),
			TokenSupplyType.MUTABLE
		);
		return execute(tokenCreation);
	}

	/**
	 * Creates a third party token into the user's account
	 *
	 * @param name The name of the token to create
	 * @param iso The symbol of the token to create
	 * @param description A description of the token
	 * @param initialSupply The initial amount in subunits of supply for this token
	 * @param granularity The least multiple of subunits per transaction for this token
	 * @param tokenSupplyType The type of supply for this token: Fixed or Mutable
	 * @return result of the transaction
	 */
	public Result createToken(
		String name,
		String iso,
		String description,
		BigDecimal initialSupply,
		BigDecimal granularity,
		TokenSupplyType tokenSupplyType
	) {
		CreateTokenAction tokenCreation = CreateTokenAction.create(
				getMyAddress(), name, iso, description, initialSupply, granularity, tokenSupplyType);
		return execute(tokenCreation);
	}

	/**
	 * Mints an amount of new tokens into the user's account
	 *
	 * @param iso The symbol of the token to mint
	 * @param amount The amount to mint
	 * @return result of the transaction
	 */
	public Result mintTokens(String iso, BigDecimal amount) {
		MintTokensAction mintTokensAction = MintTokensAction.create(TokenDefinitionReference.of(getMyAddress(), iso), amount);
		return execute(mintTokensAction);
	}

	/**
	 * Mints an amount of new tokens and transfers it to another account
	 *
	 * @param iso The symbol of the token to mint
	 * @param amount The amount in subunits to mint
	 * @param toAddress The address that the minted tokens should be sent to
	 * @return result of the transaction
	 */
	public Result mintAndTransferTokens(String iso, BigDecimal amount, RadixAddress toAddress) {
		MintAndTransferTokensAction mintTokensAction = new MintAndTransferTokensAction(
			TokenDefinitionReference.of(getMyAddress(), iso),
			amount,
			toAddress
		);
		return execute(mintTokensAction);
	}

	/**
	 * Burns an amount of tokens in the user's account
	 *
	 * @param iso The symbol of the token to mint
	 * @param amount The amount to mint
	 * @return result of the transaction
	 */
	public Result burnTokens(String iso, BigDecimal amount) {
		BurnTokensAction burnTokensAction = BurnTokensAction.create(getMyAddress(), TokenDefinitionReference.of(getMyAddress(), iso), amount);
		return execute(burnTokensAction);
	}

	/**
	 * Transfers an amount of a token to an address
	 *
	 * @param to the address to transfer tokens to
	 * @param amount the amount and token type
	 * @return result of the transaction
	 */
	public Result transferTokens(RadixAddress to, BigDecimal amount, TokenDefinitionReference token) {
		return transferTokens(getMyAddress(), to, amount, token);
	}

	/**
	 * Transfers an amount of a token with a message attachment to an address
	 *
	 * @param to the address to transfer tokens to
	 * @param amount the amount and token type
	 * @param message message to be encrypted and attached to transfer
	 * @return result of the transaction
	 */
	public Result transferTokens(
		RadixAddress to,
		BigDecimal amount,
		TokenDefinitionReference token,
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
	 * Transfers an amount of a token with a data attachment to an address
	 *
	 * @param to the address to send tokens to
	 * @param amount the amount and token type
	 * @param attachment the data attached to the transaction
	 * @return result of the transaction
	 */
	public Result transferTokens(RadixAddress to, BigDecimal amount, TokenDefinitionReference token, @Nullable Data attachment) {
		return transferTokens(getMyAddress(), to, amount, token, attachment);
	}


	public Result transferTokens(RadixAddress from, RadixAddress to, BigDecimal amount, TokenDefinitionReference token) {
		return transferTokens(from, to, amount, token, null);
	}

	/**
	 * Transfers an amount of a token with a data attachment to an address with a unique property
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
		TokenDefinitionReference token,
		@Nullable Data attachment
	) {
		Objects.requireNonNull(from);
		Objects.requireNonNull(to);
		Objects.requireNonNull(amount);
		Objects.requireNonNull(token);

		final TransferTokensAction transferTokensAction =
				TransferTokensAction.create(from, to, amount, token, attachment);

		return this.execute(transferTokensAction);
	}

	private Observable<ParticleGroup> statefulMappersToParticleGroups(Observable<Action> actions) {
		return actions.flatMap(action ->
			Observable
				.fromIterable(this.statefulActionToParticleGroupsMappers)
				.flatMap(mapper -> {
					final Observable<Observable<? extends ApplicationState>> context =
							mapper.requiredState(action).map(ctx -> this.getState(ctx.stateClass(), ctx.address()));
					return mapper.mapToParticleGroups(action, context);
				})
		);
	}

	private Observable<Action> statefulMappersToSideEffects(Action action) {
		return Observable
				.fromIterable(this.statefulActionToParticleGroupsMappers)
				.flatMap(mapper -> {
					final Observable<Observable<? extends ApplicationState>> context =
						mapper.requiredState(action).map(ctx -> this.getState(ctx.stateClass(), ctx.address()));
					return mapper.sideEffects(action, context);
				});
	}

	private Observable<Action> collectActionAndEffects(Action action) {
		Observable<Action> statelessEffects = Observable
			.fromIterable(this.statelessActionToParticleGroupsMappers)
			.flatMap(mapper -> mapper.sideEffects(action));
		Observable<Action> statefulEffects = this.statefulMappersToSideEffects(action);

		return Observable.concat(statelessEffects, statefulEffects)
			.flatMap(RadixApplicationAPI.this::collectActionAndEffects)
			.startWith(action);
	}

	/**
	 * Returns a cold single of an unsigned atom given a user action. Note that this is
	 * method will always return a unique atom even if given equivalent actions
	 *
	 * @param action action to build a single atom
	 * @return a cold single of an atom mapped from an action
	 */
	public Single<UnsignedAtom> buildAtom(Action action) {
		final Observable<Action> allActions = this.collectActionAndEffects(action);
		final Observable<ParticleGroup> statelessParticleGroups = allActions.flatMap(a ->
			Observable
				.fromIterable(this.statelessActionToParticleGroupsMappers)
				.flatMap(mapper -> mapper.mapToParticleGroups(a))
		);
		final Observable<ParticleGroup> statefulParticleGroups = this.statefulMappersToParticleGroups(allActions);

		return Observable.concat(statelessParticleGroups, statefulParticleGroups)
			.<List<ParticleGroup>>scanWith(
					ArrayList::new,
					(a, b) -> Stream.concat(a.stream(), Stream.of(b)).collect(Collectors.toList())
			)
			.lastOrError()
			.map(particleGroups -> {
				List<ParticleGroup> allParticleGroups = new ArrayList<>(particleGroups);
				ImmutableMap<String, String> metaData = ImmutableMap.of(
					Atom.METADATA_TIMESTAMP_KEY, String.valueOf(generateTimestamp()));
				Atom atom = new Atom(particleGroups, metaData);
				allParticleGroups.addAll(this.feeMapper.map(atom, this.universe, this.getMyPublicKey()));
				return new UnsignedAtom(new Atom(allParticleGroups, metaData));
			});
	}

	private long generateTimestamp() {
		return System.currentTimeMillis();
	}

	private Result buildDisconnectedResult(Action action) {
		ConnectableObservable<SubmitAtomAction> updates = this.buildAtom(action)
			.flatMap(this.identity::sign)
			.flatMapObservable(this.ledger.getAtomSubmitter()::submitAtom)
			.replay();

		Completable completable = updates
			.ofType(SubmitAtomResultAction.class)
			.firstOrError()
			.flatMapCompletable(update -> {
				if (update.getType() == SubmitAtomResultActionType.STORED) {
					return Completable.complete();
				} else {
					JsonElement data = update.getData();
					final JsonObject errorData = data == null ? null : data.getAsJsonObject();
					final ActionExecutionExceptionBuilder exceptionBuilder = new ActionExecutionExceptionBuilder()
						.errorData(errorData);
					atomErrorMappers.stream()
						.flatMap(errorMapper -> errorMapper.mapAtomErrorToExceptionReasons(update.getAtom(), errorData))
						.forEach(exceptionBuilder::addReason);
					return Completable.error(exceptionBuilder.build());
				}
			});

		return new Result(updates, completable);
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
