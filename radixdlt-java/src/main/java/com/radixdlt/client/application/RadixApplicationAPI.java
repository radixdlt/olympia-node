package com.radixdlt.client.application;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.application.translate.StageActionException;
import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.client.application.translate.unique.PutUniqueIdAction;
import com.radixdlt.client.core.BootstrapConfig;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.RRI;
import com.radixdlt.client.core.ledger.AtomStore;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.DiscoverMoreNodesAction;
import com.radixdlt.client.core.network.actions.SubmitAtomCompleteAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.radix.common.tuples.Pair;
import org.radix.utils.RadixConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ActionExecutionException.ActionExecutionExceptionBuilder;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.AtomErrorToExceptionReasonMapper;
import com.radixdlt.client.application.translate.AtomToExecutedActionsMapper;
import com.radixdlt.client.application.translate.FeeMapper;
import com.radixdlt.client.application.translate.ParticleReducer;
import com.radixdlt.client.application.translate.PowFeeMapper;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.StatelessActionToParticleGroupsMapper;
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
import com.radixdlt.client.application.translate.tokens.TokenTransfer;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
import com.radixdlt.client.application.translate.tokens.TransferTokensToParticleGroupsMapper;
import com.radixdlt.client.application.translate.unique.AlreadyUsedUniqueIdReasonMapper;
import com.radixdlt.client.application.translate.unique.PutUniqueIdToParticleGroupsMapper;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.ledger.AtomObservation;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.crypto.ECKeyPairGenerator;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
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
 * The Radix Application API, a high level api which hides the complexity of atoms, cryptography, and
 * consensus. It exposes a simple high level interface for interaction with a Radix ledger.
 */
public class RadixApplicationAPI {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixApplicationAPI.class);

	public static class Result {
		private final ConnectableObservable<SubmitAtomAction> updates;
		private final Completable completable;
		private final Single<Atom> cachedAtom;

		private Result(
			ConnectableObservable<SubmitAtomAction> updates,
			Single<Atom> cachedAtom,
			List<AtomErrorToExceptionReasonMapper> atomErrorMappers
		) {
			this.updates = updates;
			this.cachedAtom = cachedAtom;
			this.completable = updates
				.ofType(SubmitAtomStatusAction.class)
				.lastOrError()
				.flatMapCompletable(status -> {
					if (status.getStatusNotification().getAtomStatus() == AtomStatus.STORED) {
						return Completable.complete();
					} else {
						// TODO: Move jsonElement and error mapping logic somewhere else
						JsonElement data = status.getStatusNotification().getData();
						final JsonObject errorData = data == null ? null : data.getAsJsonObject();
						final ActionExecutionExceptionBuilder exceptionBuilder = new ActionExecutionExceptionBuilder()
							.errorData(errorData);
						atomErrorMappers.stream()
							.flatMap(errorMapper -> errorMapper.mapAtomErrorToExceptionReasons(status.getAtom(), errorData))
							.forEach(exceptionBuilder::addReason);
						return Completable.error(exceptionBuilder.build());
					}
				});
		}

		private Result connect() {
			this.updates.connect();
			return this;
		}

		/**
		 * Get the atom which was sent for submission
		 * @return the atom which was sent
		 */
		public Atom getAtom() {
			return cachedAtom.blockingGet();
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

		/**
		 * Block until the execution of the action is stored on the node ledger.
		 * Throws an exception if there are any issues.
		 */
		public void blockUntilComplete() {
			completable.blockingAwait();
		}
	}

	private final RadixIdentity identity;
	private final RadixUniverse universe;

	private final Map<Class<?>, AtomToExecutedActionsMapper> actionStores;

	private final Map<Class<? extends ApplicationState>, ParticleReducer> applicationStores;

	private final ImmutableMap<Class<? extends Action>, Function<Action, Set<ShardedParticleStateId>>> requiredStateMappers;
	private final ImmutableMap<Class<? extends Action>, BiFunction<Action, Stream<Particle>, List<ParticleGroup>>> actionMappers;

	/**
	 * Mapper of atom submission errors to application level errors
	 */
	private final List<AtomErrorToExceptionReasonMapper> atomErrorMappers;

	// TODO: Translator from particles to atom
	private final FeeMapper feeMapper;

	private RadixApplicationAPI(
		RadixIdentity identity,
		RadixUniverse universe,
		FeeMapper feeMapper,
		ImmutableMap<Class<? extends Action>, Function<Action, Set<ShardedParticleStateId>>> requiredStateMappers,
		ImmutableMap<Class<? extends Action>, BiFunction<Action, Stream<Particle>, List<ParticleGroup>>> actionMappers,
		List<ParticleReducer<? extends ApplicationState>> particleReducers,
		List<AtomToExecutedActionsMapper<? extends Object>> atomMappers,
		List<AtomErrorToExceptionReasonMapper> atomErrorMappers
	) {
		Objects.requireNonNull(identity);
		Objects.requireNonNull(universe);
		Objects.requireNonNull(feeMapper);
		Objects.requireNonNull(requiredStateMappers);
		Objects.requireNonNull(actionMappers);
		Objects.requireNonNull(particleReducers);
		Objects.requireNonNull(atomErrorMappers);

		this.identity = identity;
		this.universe = universe;
		this.actionStores = atomMappers.stream().collect(Collectors.toMap(
			AtomToExecutedActionsMapper::actionClass,
			m -> m
		));
		this.applicationStores = particleReducers.stream().collect(Collectors.toMap(ParticleReducer::stateClass, r -> r));
		this.actionMappers = actionMappers;
		this.requiredStateMappers = requiredStateMappers;
		this.atomErrorMappers = atomErrorMappers;
		this.feeMapper = feeMapper;
	}

	public static class RadixApplicationAPIBuilder {
		private RadixIdentity identity;
		private RadixUniverse universe;
		private FeeMapper feeMapper;
		private List<ParticleReducer<? extends ApplicationState>> reducers = new ArrayList<>();
		private ImmutableMap.Builder<Class<? extends Action>, Function<Action, Set<ShardedParticleStateId>>> requiredStateMappers
			= new ImmutableMap.Builder<>();
		private ImmutableMap.Builder<Class<? extends Action>, BiFunction<Action, Stream<Particle>, List<ParticleGroup>>> actionMappers
			= new ImmutableMap.Builder<>();
		private List<AtomToExecutedActionsMapper<? extends Object>> atomMappers = new ArrayList<>();
		private List<AtomErrorToExceptionReasonMapper> atomErrorMappers = new ArrayList<>();

		public RadixApplicationAPIBuilder() {
		}

		public RadixApplicationAPIBuilder addAtomMapper(AtomToExecutedActionsMapper<? extends Object> atomMapper) {
			this.atomMappers.add(atomMapper);
			return this;
		}

		public <T extends Action> RadixApplicationAPIBuilder addStatelessParticlesMapper(
			Class<T> actionClass,
			StatelessActionToParticleGroupsMapper<T> mapper
		) {
			this.requiredStateMappers.put(actionClass, a -> ImmutableSet.of());
			this.actionMappers.put(actionClass, (a, p) -> mapper.mapToParticleGroups(actionClass.cast(a)));
			return this;
		}

		public <T extends Action> RadixApplicationAPIBuilder addStatefulParticlesMapper(
			Class<T> actionClass,
			StatefulActionToParticleGroupsMapper<T> mapper
		) {
			this.requiredStateMappers.put(actionClass, a -> mapper.requiredState(actionClass.cast(a)));
			this.actionMappers.put(actionClass, (a, p) -> mapper.mapToParticleGroups(actionClass.cast(a), p));
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

		public RadixApplicationAPIBuilder bootstrap(BootstrapConfig bootstrapConfig) {
			this.universe = RadixUniverse.create(bootstrapConfig);
			return this;
		}

		public RadixApplicationAPIBuilder universe(RadixUniverse universe) {
			this.universe = universe;
			return this;
		}

		public RadixApplicationAPI build() {
			Objects.requireNonNull(this.identity, "Identity must be specified");
			Objects.requireNonNull(this.feeMapper, "Fee Mapper must be specified");
			Objects.requireNonNull(this.universe, "Universe must be specified");

			final FeeMapper feeMapper = this.feeMapper;
			final RadixIdentity identity = this.identity;
			final List<ParticleReducer<? extends ApplicationState>> reducers = this.reducers;

			return new RadixApplicationAPI(
				identity,
				universe,
				feeMapper,
				requiredStateMappers.build(),
				actionMappers.build(),
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
	public static RadixApplicationAPI create(BootstrapConfig bootstrap, RadixIdentity identity) {
		Objects.requireNonNull(identity);

		return defaultBuilder()
				.bootstrap(bootstrap)
				.identity(identity)
				.build();
	}

	/**
	 * Creates a default API builder with the default actions and reducers without an identity
	 *
	 * @return an api builder instance
	 */
	public static RadixApplicationAPIBuilder defaultBuilder() {
		return new RadixApplicationAPIBuilder()
			.defaultFeeMapper()
			.addStatelessParticlesMapper(
				SendMessageAction.class,
				new SendMessageToParticleGroupsMapper(ECKeyPairGenerator.newInstance()::generateKeyPair)
			)
			.addStatelessParticlesMapper(CreateTokenAction.class, new CreateTokenToParticleGroupsMapper())
			.addStatelessParticlesMapper(PutUniqueIdAction.class, new PutUniqueIdToParticleGroupsMapper())
			.addStatefulParticlesMapper(MintTokensAction.class, new MintTokensActionMapper())
			.addStatefulParticlesMapper(BurnTokensAction.class, new BurnTokensActionMapper())
			.addStatefulParticlesMapper(TransferTokensAction.class, new TransferTokensToParticleGroupsMapper())
			.addReducer(new TokenDefinitionsReducer())
			.addReducer(new TokenBalanceReducer())
			.addAtomMapper(new AtomToDecryptedMessageMapper())
			.addAtomMapper(new AtomToTokenTransfersMapper())
			.addAtomErrorMapper(new AlreadyUsedUniqueIdReasonMapper());
	}


	private <T extends ApplicationState> ParticleReducer<T> getStateReducer(Class<T> storeClass) {
		ParticleReducer<T> store = this.applicationStores.get(storeClass);
		if (store == null) {
			throw new IllegalArgumentException("No store available for class: " + storeClass);
		}
		return store;
	}

	private <T> AtomToExecutedActionsMapper<T> getActionMapper(Class<T> actionClass) {
		AtomToExecutedActionsMapper<T> store = actionStores.get(actionClass);
		if (store == null) {
			throw new IllegalArgumentException("No store available for class: " + actionClass);
		}
		return store;
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

	public RadixAddress getAddressFromKey(ECPublicKey publicKey) {
		return universe.getAddressFrom(publicKey);
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

		if (universe.getAtomPuller() != null) {
			return universe.getAtomPuller().pull(address).subscribe();
		} else {
			return Disposables.disposed();
		}
	}

	/**
	 * Retrieves atoms until the node returns a synced message.
	 * @param address the address to pull atoms for
	 * @return a cold completable which on subscribe pulls atoms from a source
	 */
	public Completable pullOnce(RadixAddress address) {
		return Completable.create(emitter -> {
			Disposable d = universe.getAtomPuller()
				.pull(address).subscribe();

			emitter.setCancellable(d::dispose);

			universe.getAtomStore().onSync(address).firstOrError()
				.ignoreElement()
				.subscribe(emitter::onComplete, emitter::onError);
		});
	}

	/**
	 * Returns the native Token Reference found in the genesis atom
	 *
	 * @return the native token reference
	 */
	public RRI getNativeTokenRef() {
		return universe.getNativeToken();
	}

	/**
	 * Returns a never ending stream of actions performed at a given address with the
	 * given Atom Store. pull() must be called to continually retrieve the latest actions.
	 *
	 * @param actionClass the Action class
	 * @param address the address to retrieve the state of
	 * @param <T> the Action class
	 * @return a cold observable of the actions at the given address
	 */
	public <T> Observable<T> observeActions(Class<T> actionClass, RadixAddress address) {
		final AtomToExecutedActionsMapper<T> mapper = this.getActionMapper(actionClass);
		return universe.getAtomStore()
			.getAtomObservations(address)
			.filter(AtomObservation::isStore)
			.map(AtomObservation::getAtom)
			.flatMap(a -> mapper.map(a, identity));
	}

	/**
	 * Returns a never ending stream of a state of a given address with the
	 * given Atom store. pull() must be called to continually retrieve the latest state.
	 *
	 * @param stateClass the ApplicationState class
	 * @param address the address to retrieve the state of
	 * @param <T> the ApplicationState class
	 * @return a hot observable of a state of the given address
	 */
	public <T extends ApplicationState> Observable<T> observeState(Class<T> stateClass, RadixAddress address) {
		final ParticleReducer<T> reducer = this.getStateReducer(stateClass);
		return universe.getAtomStore().onSync(address)
				.map(a ->
					universe.getAtomStore().getUpParticles(address, null)
						.reduce(reducer.initialState(), reducer::reduce, reducer::combine)
				);
	}

	/**
	 * Returns a stream of the latest state of token definitions at a given
	 * address
	 *
	 * @param address the address of the account to check
	 * @return a cold observable of the latest state of token definitions
	 */
	public Observable<TokenDefinitionsState> observeTokenDefs(RadixAddress address) {
		return observeState(TokenDefinitionsState.class, address);
	}

	/**
	 * Returns a stream of the latest state of token definitions at the user's
	 * address
	 *
	 * @return a cold observable of the latest state of token definitions
	 */
	public Observable<TokenDefinitionsState> observeTokenDefs() {
		return observeTokenDefs(getMyAddress());
	}

	/**
	 * Returns a stream of the latest state of a given token
	 *
	 * @return a cold observable of the latest state of the token
	 */
	public Observable<TokenState> observeTokenDef(RRI tokenRRI) {
		return this.observeTokenDefs(tokenRRI.getAddress())
			.flatMapMaybe(m -> Optional.ofNullable(m.getState().get(tokenRRI)).map(Maybe::just).orElse(Maybe.empty()));
	}

	/**
	 * Retrieve the token state of the given rri
	 * @return the token state of the rri
	 */
	public TokenState getTokenDef(RRI tokenRRI) {
		final ParticleReducer<TokenDefinitionsState> reducer = this.getStateReducer(TokenDefinitionsState.class);
		return universe.getAtomStore().getUpParticles(getMyAddress(), null)
			.reduce(reducer.initialState(), reducer::reduce, reducer::combine)
			.getState()
			.get(tokenRRI);
	}

	/**
	 * Returns a never ending stream of messages stored at the current address.
	 * pull() must be called to continually retrieve the latest messages.
	 *
	 * @return a cold observable of the messages at the current address
	 */
	public Observable<DecryptedMessage> observeMessages() {
		return observeMessages(this.getMyAddress());
	}

	/**
	 * Returns a never ending stream of messages stored at a given address.
	 * pull() must be called to continually retrieve the latest messages.
	 *
	 * @param address the address to retrieve the messages from
	 * @return a cold observable of the messages at the given address
	 */
	public Observable<DecryptedMessage> observeMessages(RadixAddress address) {
		Objects.requireNonNull(address);
		return observeActions(DecryptedMessage.class, address);
	}

	public Result sendMessage(byte[] data, boolean encrypt) {
		return this.sendMessage(data, encrypt, getMyAddress());
	}

	public Result sendMessage(byte[] data, boolean encrypt, RadixAddress address) {
		SendMessageAction sendMessageAction = SendMessageAction.create(data, getMyAddress(), address, encrypt);

		return execute(sendMessageAction);
	}

	/**
	 * Returns a never ending stream of token transfers stored at the current address.
	 * pull() must be called to continually retrieve the latest transfers.
	 *
	 * @return a cold observable of the token transfers at the current address
	 */
	public Observable<TokenTransfer> observeTokenTransfers() {
		return observeTokenTransfers(getMyAddress());
	}

	/**
	 * Returns a never ending stream of token transfers stored at a given address.
	 * pull() must be called to continually retrieve the latest transfers.
	 *
	 * @param address the address to retrieve the token transfers from
	 * @return a cold observable of the token transfers at the given address
	 */
	public Observable<TokenTransfer> observeTokenTransfers(RadixAddress address) {
		Objects.requireNonNull(address);
		return observeActions(TokenTransfer.class, address);
	}

	/**
	 * Retrieve the balances of the current address from the current atom store.
	 * @return map of balances
	 */
	public Map<RRI, BigDecimal> getBalances() {
		final ParticleReducer<TokenBalanceState> reducer = this.getStateReducer(TokenBalanceState.class);
		return universe.getAtomStore().getUpParticles(getMyAddress(), null)
			.reduce(reducer.initialState(), reducer::reduce, reducer::combine)
			.getBalance();
	}

	/**
	 * Returns a stream of the latest balances at a given address.
	 * pull() must be called to continually retrieve the latest balances.
	 *
	 * @return a cold observable of the latest balances at an address
	 */
	public Observable<Map<RRI, BigDecimal>> observeBalances(RadixAddress address) {
		Objects.requireNonNull(address);
		return observeState(TokenBalanceState.class, address)
			.map(TokenBalanceState::getBalance);
	}

	/**
	 * Returns a stream of the latest balances at the current address
	 * pull() must be called to continually retrieve the latest balances.
	 *
	 * @return a cold observable of the latest balances at the current address
	 */
	public Observable<BigDecimal> observeBalance(RRI tokenRRI) {
		return observeBalance(getMyAddress(), tokenRRI);
	}

	/**
	 * Returns a stream of the latest balance of a given token at a given address
	 * pull() must be called to continually retrieve the latest balance.
	 *
	 * @return a cold observable of the latest balance of a token at a given address
	 */
	public Observable<BigDecimal> observeBalance(RadixAddress address, RRI token) {
		Objects.requireNonNull(token);

		return observeBalances(address)
			.map(balances -> Optional.ofNullable(balances.get(token)).orElse(BigDecimal.ZERO));
	}

	/**
	 * Creates a multi-issuance token registered into the user's account with
	 * zero initial supply, 10^-18 granularity and no description.
	 *
	 * @param tokenRRI The symbol of the token to create
	 * @param name The name of the token to create
	 * @return result of the transaction
	 */
	public Result createMultiIssuanceToken(RRI tokenRRI, String name) {
		final CreateTokenAction tokenCreation = CreateTokenAction.create(
			tokenRRI,
			name,
			null,
			BigDecimal.ZERO,
			TokenUnitConversions.getMinimumGranularity(),
			TokenSupplyType.MUTABLE
		);
		return execute(tokenCreation);
	}

	/**
	 * Creates a multi-issuance token registered into the user's account with
	 * zero initial supply and 10^-18 granularity
	 *
	 * @param tokenRRI The symbol of the token to create
	 * @param name The name of the token to create
	 * @param description A description of the token
	 * @return result of the transaction
	 */
	public Result createMultiIssuanceToken(
		RRI tokenRRI,
		String name,
		String description
	) {
		final CreateTokenAction tokenCreation = CreateTokenAction.create(
			tokenRRI,
			name,
			description,
			BigDecimal.ZERO,
			TokenUnitConversions.getMinimumGranularity(),
			TokenSupplyType.MUTABLE
		);
		return execute(tokenCreation);
	}

	/**
	 * Creates a fixed-supply token registered into the user's account with
	 * 10^-18 granularity
	 *
	 * @param tokenRRI The symbol of the token to create
	 * @param name The name of the token to create
	 * @param description A description of the token
	 * @param supply The supply of the created token
	 * @return result of the transaction
	 */
	public Result createFixedSupplyToken(
		RRI tokenRRI,
		String name,
		String description,
		BigDecimal supply
	) {
		final CreateTokenAction tokenCreation = CreateTokenAction.create(
			tokenRRI,
			name,
			description,
			supply,
			TokenUnitConversions.getMinimumGranularity(),
			TokenSupplyType.FIXED
		);
		return execute(tokenCreation);
	}

	/**
	 * Creates a token registered into the user's account
	 *
	 * @param tokenRRI The symbol of the token to create
	 * @param name The name of the token to create
	 * @param description A description of the token
	 * @param initialSupply The initial amount of supply for this token
	 * @param granularity The least multiple of subunits per transaction for this token
	 * @param tokenSupplyType The type of supply for this token: Fixed or Mutable
	 * @return result of the transaction
	 */
	public Result createToken(
		RRI tokenRRI,
		String name,
		String description,
		BigDecimal initialSupply,
		BigDecimal granularity,
		TokenSupplyType tokenSupplyType
	) {
		CreateTokenAction tokenCreation = CreateTokenAction.create(tokenRRI,
				name, description, initialSupply, granularity, tokenSupplyType);
		return execute(tokenCreation);
	}

	/**
	 * Mints an amount of new tokens into the user's account
	 *
	 * @param token The symbol of the token to mint
	 * @param amount The amount to mint
	 * @return result of the transaction
	 */
	public Result mintTokens(RRI token, BigDecimal amount) {
		MintTokensAction mintTokensAction = MintTokensAction.create(token, getMyAddress(), amount);
		return execute(mintTokensAction);
	}

	/**
	 * Burns an amount of tokens in the user's account
	 *
	 * @param token The symbol of the token to mint
	 * @param amount The amount to mint
	 * @return result of the transaction
	 */
	public Result burnTokens(RRI token, BigDecimal amount) {
		BurnTokensAction burnTokensAction = BurnTokensAction.create(token, getMyAddress(), amount);
		return execute(burnTokensAction);
	}

	/**
	 * Transfers an amount of a token to an address
	 *
	 * @param to the address to transfer tokens to
	 * @param amount the amount and token type
	 * @return result of the transaction
	 */
	public Result sendTokens(RRI token, RadixAddress to, BigDecimal amount) {
		return sendTokens(token, getMyAddress(), to, amount);
	}

	/**
	 * Transfers an amount of a token with a message attachment to an address
	 *
	 * @param to the address to transfer tokens to
	 * @param amount the amount and token type
	 * @param message message to be encrypted and attached to transfer
	 * @return result of the transaction
	 */
	public Result sendTokens(
		RRI token,
		RadixAddress to,
		BigDecimal amount,
		@Nullable String message
	) {
		final byte[] attachment;
		if (message != null) {
			attachment = message.getBytes(RadixConstants.STANDARD_CHARSET);
		} else {
			attachment = null;
		}

		return sendTokens(token, getMyAddress(), to, amount, attachment);
	}

	/**
	 * Transfers an amount of tokens with an attachment to an address
	 *
	 * @param to the address to send tokens to
	 * @param amount the amount and token type
	 * @param attachment the data attached to the transaction
	 * @return result of the transaction
	 */
	public Result sendTokens(RRI token, RadixAddress to, BigDecimal amount, @Nullable byte[] attachment) {
		return sendTokens(token, getMyAddress(), to, amount, attachment);
	}

	/**
	 * Transfers an amount of tokens to an address
	 *
	 * @param to the address to send tokens to
	 * @param amount the amount and token type
	 * @return result of the transaction
	 */
	public Result sendTokens(RRI token, RadixAddress from, RadixAddress to, BigDecimal amount) {
		return sendTokens(token, from, to, amount, null);
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
	public Result sendTokens(
		RRI token,
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		@Nullable byte[] attachment
	) {
		Objects.requireNonNull(from);
		Objects.requireNonNull(to);
		Objects.requireNonNull(amount);
		Objects.requireNonNull(token);

		final TransferTokensAction transferTokensAction =
				TransferTokensAction.create(token, from, to, amount, attachment);

		return this.execute(transferTokensAction);
	}

	/**
	 * Immediately executes a user action onto the ledger. Note that this method is NOT
	 * idempotent.
	 *
	 * @param action action to execute
	 * @return results of the execution
	 */
	public Result execute(Action action) {
		Transaction transaction = this.createTransaction();
		transaction.stage(action);
		return transaction.commitAndPush();
	}

	private long generateTimestamp() {
		return System.currentTimeMillis();
	}

	/**
	 * Returns an unsigned atom with the appropriate fees given a list of
	 * particle groups to compose the atom.
	 *
	 * @param particleGroups particle groups to include in atom
	 * @return unsigned atom with appropriate fees
	 */
	public UnsignedAtom buildAtomWithFee(List<ParticleGroup> particleGroups) {
		List<ParticleGroup> allParticleGroups = new ArrayList<>(particleGroups);
		Map<String, String> metaData = new HashMap<>();
		metaData.put(Atom.METADATA_TIMESTAMP_KEY, String.valueOf(generateTimestamp()));
		Atom atom = new Atom(particleGroups, metaData);
		Pair<Map<String, String>, List<ParticleGroup>> fee = this.feeMapper.map(atom, this.universe, this.getMyPublicKey());
		allParticleGroups.addAll(fee.getSecond());
		metaData.putAll(fee.getFirst());

		return new UnsignedAtom(new Atom(allParticleGroups, metaData));
	}

	/**
	 * Represents an atomic transaction to be committed to the ledger
	 */
	public final class Transaction {
		private final String uuid;
		private List<Action> workingArea = new ArrayList<>();

		private Transaction() {
			this.uuid = UUID.randomUUID().toString();
		}

		/**
		 * Add an action to the working area
		 * @param action action to add to the working area
		 */
		public void addToWorkingArea(Action action) {
			workingArea.add(action);
		}

		/**
		 * Retrieves the shards and particle types required to execute the
		 * actions in the current working area.
		 *
		 * @return set of shard + particle types
		 */
		public Set<ShardedParticleStateId> getWorkingAreaRequirements() {
			return workingArea.stream()
				.filter(a -> requiredStateMappers.containsKey(a.getClass()))
				.flatMap(a -> requiredStateMappers.get(a.getClass()).apply(a).stream())
				.collect(Collectors.toSet());
		}

		/**
		 * Move all actions in the current working area to staging
		 */
		public void stageWorkingArea() throws StageActionException {
			for (Action action : workingArea) {
				stage(action);
			}
			workingArea.clear();
		}

		/**
		 * Add an action to staging area in preparation for commitAndPush.
		 * Collects the necessary particles to make the action happen.
		 *
		 * @param action action to add to staging area.
		 */
		public void stage(Action action) throws StageActionException {
			BiFunction<Action, Stream<Particle>, List<ParticleGroup>> statefulMapper = actionMappers.get(action.getClass());
			if (statefulMapper == null) {
				throw new IllegalArgumentException("Unknown action class: " + action.getClass() + ". Available: " + actionMappers.keySet());
			}

			Function<Action, Set<ShardedParticleStateId>> requiredStateMapper = requiredStateMappers.get(action.getClass());
			Set<ShardedParticleStateId> required = requiredStateMapper != null ? requiredStateMapper.apply(action) : ImmutableSet.of();
			Stream<Particle> particles = required.stream()
				.flatMap(ctx -> universe.getAtomStore().getUpParticles(ctx.address(), uuid).filter(ctx.particleClass()::isInstance));

			List<ParticleGroup> pgs = statefulMapper.apply(action, particles);
			for (ParticleGroup pg : pgs) {
				universe.getAtomStore().stageParticleGroup(uuid, pg);
			}
		}

		/**
		 * Creates an atom composed of all of the currently staged particles.
		 * @return an unsigned atom
		 */
		public UnsignedAtom buildAtom() {
			List<ParticleGroup> pgs = universe.getAtomStore().getStagedAndClear(uuid);
			return buildAtomWithFee(pgs);
		}

		/**
		 * Commit the transaction onto the ledger
		 * @return the results of committing
		 */
		public Result commitAndPush() {
			final UnsignedAtom unsignedAtom = buildAtom();
			final Single<Atom> atom = identity.sign(unsignedAtom);
			return createAtomSubmission(atom, false, null).connect();
		}

		/**
		 * Commit the transaction onto the ledger
		 * @param originNode the originNode to push to
		 * @return the results of committing
		 */
		public Result commitAndPush(RadixNode originNode) {
			final UnsignedAtom unsignedAtom = buildAtom();
			final Single<Atom> atom = identity.sign(unsignedAtom);
			return createAtomSubmission(atom, false, originNode).connect();
		}
	}

	/**
	 * Create a new transaction which is based off of the
	 * current data in the atom store.
	 *
	 * @return a new transaction
	 */
	public Transaction createTransaction() {
		return new Transaction();
	}

	/**
	 * Low level call to submit an atom into the network.
	 * @param atom atom to submit
	 * @param completeOnStoreOnly if true, result will only complete on a store event
	 * @return the result of the submission
	 */
	public Result submitAtom(Atom atom, boolean completeOnStoreOnly) {
		return createAtomSubmission(Single.just(atom), completeOnStoreOnly, null).connect();
	}

	/**
	 * Low level call to submit an atom into the network. Result will complete
	 * on the first STORED event.
	 * @param atom atom to submit
	 * @return the result of the submission
	 */
	public Result submitAtom(Atom atom) {
		return createAtomSubmission(Single.just(atom), false, null).connect();
	}

	private Result createAtomSubmission(Single<Atom> atom, boolean completeOnStoreOnly, RadixNode originNode) {
		Single<Atom> cachedAtom = atom.cache();
		final ConnectableObservable<SubmitAtomAction> updates = cachedAtom
			.flatMapObservable(a -> {
				final SubmitAtomAction initialAction;
				if (originNode == null) {
					initialAction = SubmitAtomRequestAction.newRequest(a, completeOnStoreOnly);
				} else {
					initialAction = SubmitAtomSendAction.of(UUID.randomUUID().toString(), a, originNode, completeOnStoreOnly);
				}
				Observable<SubmitAtomAction> status =
					this.universe.getNetworkController().getActions().ofType(SubmitAtomAction.class)
						.filter(u -> u.getUuid().equals(initialAction.getUuid()))
						.takeWhile(s -> !(s instanceof SubmitAtomCompleteAction));
				ConnectableObservable<SubmitAtomAction> replay = status.replay();
				replay.connect();

				this.universe.getNetworkController().dispatch(initialAction);

				return replay;
			})
			.replay();

		return new Result(updates, cachedAtom, atomErrorMappers);
	}

	/**
	 * Retrieve the atom store used by the API
	 *
	 * @return the atom store
	 */
	public AtomStore getAtomStore() {
		return this.universe.getAtomStore();
	}

	/**
	 * Dispatches a discovery request, the result of which would
	 * be viewable via getNetworkState()
	 */
	public void discoverNodes() {
		this.universe.getNetworkController().dispatch(DiscoverMoreNodesAction.instance());
	}

	/**
	 * Get a stream of updated network states as they occur.
	 *
	 * @return a hot observable of the current network state
	 */
	public Observable<RadixNetworkState> getNetworkState() {
		return this.universe.getNetworkController().getNetwork();
	}

	/**
	 * Low level call to retrieve the actions occurring at the network
	 * level.
	 *
	 * @return a hot observable of network actions as they occur
	 */
	public Observable<RadixNodeAction> getNetworkActions() {
		return this.universe.getNetworkController().getActions();
	}
}
