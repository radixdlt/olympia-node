/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.application;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.radixdlt.atom.Atom;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ActionExecutionException.ActionExecutionExceptionBuilder;
import com.radixdlt.client.application.translate.data.AtomToPlaintextMessageMapper;
import com.radixdlt.client.application.translate.data.PlaintextMessage;
import com.radixdlt.client.application.translate.ActionExecutionExceptionReason;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.AtomErrorToExceptionReasonMapper;
import com.radixdlt.client.application.translate.AtomToExecutedActionsMapper;
import com.radixdlt.client.application.translate.FeeProcessor;
import com.radixdlt.client.application.translate.ParticleReducer;
import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.application.translate.StageActionException;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.StatelessActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.TokenFeeProcessor;
import com.radixdlt.client.application.translate.tokens.BurnTokensAction;
import com.radixdlt.client.application.translate.tokens.BurnTokensActionMapper;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.CreateTokenToParticleGroupsMapper;
import com.radixdlt.client.application.translate.tokens.DelegatedTokenBalanceState;
import com.radixdlt.client.application.translate.tokens.MintTokensAction;
import com.radixdlt.client.application.translate.tokens.MintTokensActionMapper;
import com.radixdlt.client.application.translate.tokens.StakeTokensAction;
import com.radixdlt.client.application.translate.tokens.StakeTokensMapper;
import com.radixdlt.client.application.translate.tokens.StakedTokenBalanceReducer;
import com.radixdlt.client.application.translate.tokens.StakedTokenBalanceState;
import com.radixdlt.client.application.translate.tokens.TokenBalanceReducer;
import com.radixdlt.client.application.translate.tokens.TokenBalanceState;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionsReducer;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionsState;
import com.radixdlt.client.application.translate.tokens.TokenState;
import com.radixdlt.client.application.translate.tokens.TokenTransfer;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
import com.radixdlt.client.application.translate.tokens.TransferTokensToParticleGroupsMapper;
import com.radixdlt.client.application.translate.tokens.UnstakeTokensAction;
import com.radixdlt.client.application.translate.tokens.UnstakeTokensMapper;
import com.radixdlt.client.application.translate.unique.PutUniqueIdAction;
import com.radixdlt.client.application.translate.unique.PutUniqueIdToParticleGroupsMapper;
import com.radixdlt.client.application.translate.validators.RegisterValidatorAction;
import com.radixdlt.client.application.translate.validators.UnregisterValidatorAction;
import com.radixdlt.client.application.translate.validators.RegisterValidatorActionMapper;
import com.radixdlt.client.application.translate.validators.UnregisterValidatorActionMapper;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.client.core.BootstrapConfig;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.atom.SpunParticle;
import com.radixdlt.client.core.ledger.AtomObservation;
import com.radixdlt.client.core.ledger.AtomStore;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.DiscoverMoreNodesAction;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomCompleteAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.annotations.Nullable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.observables.ConnectableObservable;

import com.radixdlt.utils.Pair;
import com.radixdlt.utils.RadixConstants;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The Radix Application API, a high level api which hides the complexity of atoms, cryptography, and
 * consensus. It exposes a simple high level interface for interaction with a Radix ledger.
 */
public class RadixApplicationAPI {
	/**
	 * Creates an API with the default actions and reducers
	 *
	 * @param bootstrap bootstrap configuration
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
			.defaultFeeProcessor()
			.addStatelessParticlesMapper(CreateTokenAction.class, new CreateTokenToParticleGroupsMapper())
			.addStatelessParticlesMapper(PutUniqueIdAction.class, new PutUniqueIdToParticleGroupsMapper())
			.addStatefulParticlesMapper(MintTokensAction.class, new MintTokensActionMapper())
			.addStatefulParticlesMapper(BurnTokensAction.class, new BurnTokensActionMapper())
			.addStatefulParticlesMapper(TransferTokensAction.class, new TransferTokensToParticleGroupsMapper())
			.addStatefulParticlesMapper(StakeTokensAction.class, new StakeTokensMapper())
			.addStatefulParticlesMapper(UnstakeTokensAction.class, new UnstakeTokensMapper())
			.addStatefulParticlesMapper(RegisterValidatorAction.class, new RegisterValidatorActionMapper())
			.addStatefulParticlesMapper(UnregisterValidatorAction.class, new UnregisterValidatorActionMapper())
			.addReducer(new TokenDefinitionsReducer())
			.addReducer(new TokenBalanceReducer())
			.addReducer(new StakedTokenBalanceReducer())
			.addAtomMapper(new AtomToPlaintextMessageMapper());
	}

	private final RadixIdentity identity;
	private final RadixUniverse universe;
	private final Map<Class<?>, AtomToExecutedActionsMapper<?>> actionStores;
	private final Map<Class<? extends ApplicationState>, ParticleReducer<?>> applicationStores;
	private final ImmutableMap<Class<? extends Action>, Function<Action, Set<ShardedParticleStateId>>> requiredStateMappers;
	private final ImmutableMap<Class<? extends Action>, BiFunction<Action, Stream<Particle>, List<ParticleGroup>>> actionMappers;
	/**
	 * Mapper of atom submission errors to application level errors
	 */
	private final List<AtomErrorToExceptionReasonMapper> atomErrorMappers;
	// TODO: Translator from particles to atom
	private final FeeProcessor feeProcessor;

	private RadixApplicationAPI(
		RadixIdentity identity,
		RadixUniverse universe,
		FeeProcessor feeProcessor,
		ImmutableMap<Class<? extends Action>, Function<Action, Set<ShardedParticleStateId>>> requiredStateMappers,
		ImmutableMap<Class<? extends Action>, BiFunction<Action, Stream<Particle>, List<ParticleGroup>>> actionMappers,
		List<ParticleReducer<? extends ApplicationState>> particleReducers,
		List<AtomToExecutedActionsMapper<? extends Object>> atomMappers,
		List<AtomErrorToExceptionReasonMapper> atomErrorMappers
	) {
		Objects.requireNonNull(identity);
		Objects.requireNonNull(universe);
		Objects.requireNonNull(feeProcessor);
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
		this.feeProcessor = feeProcessor;
	}

	private <T extends ApplicationState> ParticleReducer<T> getStateReducer(Class<T> storeClass) {
		// Type safety ensured by mapping from class to reducer with class type argument
		@SuppressWarnings("unchecked")
		ParticleReducer<T> store = (ParticleReducer<T>) this.applicationStores.get(storeClass);
		if (store == null) {
			throw new IllegalArgumentException("No store available for class: " + storeClass);
		}
		return store;
	}

	private <T> AtomToExecutedActionsMapper<T> getActionMapper(Class<T> actionClass) {
		// Type safety ensured by mapping from class to mapper with class type argument
		@SuppressWarnings("unchecked")
		AtomToExecutedActionsMapper<T> store = (AtomToExecutedActionsMapper<T>) actionStores.get(actionClass);
		if (store == null) {
			throw new IllegalArgumentException("No store available for class: " + actionClass);
		}
		return store;
	}

	/**
	 * Retrieve the user's public key
	 *
	 * @return the user's public key
	 */
	public ECPublicKey getPublicKey() {
		return identity.getPublicKey();
	}

	/**
	 * Retrieve the user's key identity
	 *
	 * @return the user's identity
	 */
	public RadixIdentity getIdentity() {
		return identity;
	}

	/**
	 * Retrieve the user's address
	 *
	 * @return the current user's address
	 */
	public RadixAddress getAddress() {
		return universe.getAddressFrom(identity.getPublicKey());
	}

	/**
	 * Retrieve the address for the current universe given a public key
	 *
	 * @param publicKey public key
	 * @return an address based on the current universe and a given public key
	 */
	public RadixAddress getAddress(ECPublicKey publicKey) {
		return universe.getAddressFrom(publicKey);
	}

	/**
	 * Idempotent method which prefetches atoms in user's account
	 * TODO: what to do when no puller available
	 *
	 * @return Disposable to dispose to stop pulling
	 */
	public Disposable pull() {
		return pull(getAddress());
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
	 *
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
	 * @param address     the address to retrieve the state of
	 * @param <T>         the Action class
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
	 * @param address    the address to retrieve the state of
	 * @param <T>        the ApplicationState class
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
		return observeTokenDefs(getAddress());
	}

	/**
	 * Returns a stream of the latest state of a given token
	 *
	 * @param tokenRRI The symbol of the token
	 * @return a cold observable of the latest state of the token
	 */
	public Observable<TokenState> observeTokenDef(RRI tokenRRI) {
		return this.observeTokenDefs(tokenRRI.getAddress())
			.flatMapMaybe(m -> Optional.ofNullable(m.getState().get(tokenRRI)).map(Maybe::just).orElse(Maybe.empty()));
	}

	/**
	 * Retrieve the token state of the given rri
	 *
	 * @param tokenRRI The symbol of the token
	 * @return the token state of the rri
	 */
	public TokenState getTokenDef(RRI tokenRRI) {
		final ParticleReducer<TokenDefinitionsState> reducer = this.getStateReducer(TokenDefinitionsState.class);
		return universe.getAtomStore().getUpParticles(getAddress(), null)
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
	public Observable<PlaintextMessage> observeMessages() {
		return observeMessages(this.getAddress());
	}

	/**
	 * Returns a never ending stream of messages stored at a given address.
	 * pull() must be called to continually retrieve the latest messages.
	 *
	 * @param address the address to retrieve the messages from
	 * @return a cold observable of the messages at the given address
	 */
	public Observable<PlaintextMessage> observeMessages(RadixAddress address) {
		Objects.requireNonNull(address);
		return observeActions(PlaintextMessage.class, address);
	}

	/**
	 * Returns a never ending stream of token transfers stored at the current address.
	 * pull() must be called to continually retrieve the latest transfers.
	 *
	 * @return a cold observable of the token transfers at the current address
	 */
	public Observable<TokenTransfer> observeTokenTransfers() {
		return observeTokenTransfers(getAddress());
	}

	/**
	 * Returns a never ending stream of token transfers stored at a given address.
	 * pull() must be called to continually retrieve the latest transfers.
	 *
	 * @param address The address to retrieve the token transfers from
	 * @return a cold observable of the token transfers at the given address
	 */
	public Observable<TokenTransfer> observeTokenTransfers(RadixAddress address) {
		Objects.requireNonNull(address);
		return observeActions(TokenTransfer.class, address);
	}

	/**
	 * Retrieve the balances of the current address from the current atom store.
	 *
	 * @return map of balances
	 */
	public Map<RRI, BigDecimal> getBalances() {
		final ParticleReducer<TokenBalanceState> reducer = this.getStateReducer(TokenBalanceState.class);
		return universe.getAtomStore().getUpParticles(getAddress(), null)
			.reduce(reducer.initialState(), reducer::reduce, reducer::combine)
			.getBalance();
	}

	/**
	 * Returns a stream of the latest balances at a given address.
	 * pull() must be called to continually retrieve the latest balances.
	 *
	 * @param address the address to observe balances of
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
	 * @param tokenRRI The symbol of the token
	 * @return a cold observable of the latest balances at the current address
	 */
	public Observable<BigDecimal> observeBalance(RRI tokenRRI) {
		return observeBalance(getAddress(), tokenRRI);
	}

	/**
	 * Returns a stream of the latest balance of a given token at a given address
	 * pull() must be called to continually retrieve the latest balance.
	 *
	 * @param address The address to observe balances of
	 * @param token The symbol of the token
	 * @return a cold observable of the latest balance of a token at a given address
	 */
	public Observable<BigDecimal> observeBalance(RadixAddress address, RRI token) {
		Objects.requireNonNull(token);

		return observeBalances(address)
			.map(balances -> Optional.ofNullable(balances.get(token)).orElse(BigDecimal.ZERO));
	}

	/**
	 * Returns a stream of the latest staked balances for the staker at the specified address.
	 * pull() must have previously been called to ensure balances are retrieved and updated.
	 *
	 * @param address the staker's address
	 * @return a cold observable of the latest staked amounts by validator and token RRI
	 */
	public Observable<Map<Pair<RadixAddress, RRI>, BigDecimal>> observeStakedBalances(RadixAddress address) {
		Objects.requireNonNull(address);
		return observeState(StakedTokenBalanceState.class, address)
			.map(StakedTokenBalanceState::getBalance);
	}

	private DelegatedTokenBalanceState accumulateTokens(
		DelegatedTokenBalanceState previous,
		RadixAddress validator,
		SpunParticle spunParticle
	) {
		final var particle = spunParticle.getParticle();
		if (particle instanceof StakedTokensParticle) {
			final var stp = (StakedTokensParticle) particle;
			if (validator.equals(stp.getDelegateAddress())) {
				final var baseAmount = TokenUnitConversions.subunitsToUnits(stp.getAmount());
				final var amount = Spin.UP.equals(spunParticle.getSpin()) ? baseAmount : baseAmount.negate();
				return DelegatedTokenBalanceState.merge(previous, stp.getTokDefRef(), amount);
			}
		}
		return previous;
	}

	/**
	 * Creates a multi-issuance token registered into the user's account with
	 * zero initial supply, 10^-18 granularity and no description.
	 *
	 * @param tokenRRI The symbol of the token to create
	 * @param name     The name of the token to create
	 * @return result of the transaction
	 */
	public Result createMultiIssuanceToken(RRI tokenRRI, String name) {
		return createMultiIssuanceToken(tokenRRI, name, null);
	}

	/**
	 * Creates a multi-issuance token registered into the user's account with
	 * zero initial supply and 10^-18 granularity
	 *
	 * @param tokenRRI    The symbol of the token to create
	 * @param name        The name of the token to create
	 * @param description A description of the token
	 * @return result of the transaction
	 */
	public Result createMultiIssuanceToken(
		RRI tokenRRI,
		String name,
		String description
	) {
		return createMultiIssuanceToken(tokenRRI, name, description, null, null);
	}

	/**
	 * Creates a multi-issuance token registered into the user's account with
	 * zero initial supply and 10^-18 granularity
	 *
	 * @param tokenRRI    The symbol of the token to create
	 * @param name        The name of the token to create
	 * @param description A description of the token
	 * @param iconUrl     The URL for the token's icon
	 * @param url         The URL for the token
	 * @return result of the transaction
	 */
	public Result createMultiIssuanceToken(
		RRI tokenRRI,
		String name,
		String description,
		String iconUrl,
		String url
	) {
		final CreateTokenAction tokenCreation = CreateTokenAction.create(
			tokenRRI,
			name,
			description,
			iconUrl,
			url,
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
	 * @param tokenRRI    The symbol of the token to create
	 * @param name        The name of the token to create
	 * @param description A description of the token
	 * @param supply      The supply of the created token
	 * @return result of the transaction
	 */
	public Result createFixedSupplyToken(
		RRI tokenRRI,
		String name,
		String description,
		BigDecimal supply
	) {
		return createFixedSupplyToken(tokenRRI, name, description, null, null, supply);
	}

	/**
	 * Creates a fixed-supply token registered into the user's account with
	 * 10^-18 granularity
	 *
	 * @param tokenRRI    The symbol of the token to create
	 * @param name        The name of the token to create
	 * @param description A description of the token
	 * @param iconUrl     The URL for the token's icon
	 * @param url         The URL for the token
	 * @param supply      The supply of the created token
	 * @return result of the transaction
	 */
	public Result createFixedSupplyToken(
		RRI tokenRRI,
		String name,
		String description,
		String iconUrl,
		String url,
		BigDecimal supply
	) {
		final CreateTokenAction tokenCreation = CreateTokenAction.create(
			tokenRRI,
			name,
			description,
			iconUrl,
			url,
			supply,
			TokenUnitConversions.getMinimumGranularity(),
			TokenSupplyType.FIXED
		);
		return execute(tokenCreation);
	}

	/**
	 * Creates a token registered into the user's account
	 *
	 * @param tokenRRI        The symbol of the token to create
	 * @param name            The name of the token to create
	 * @param description     A description of the token
	 * @param initialSupply   The initial amount of supply for this token
	 * @param granularity     The least multiple of subunits per transaction for this token
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
		return createToken(tokenRRI, name, description, null, null, initialSupply, granularity, tokenSupplyType);
	}

	/**
	 * Creates a token registered into the user's account
	 *
	 * @param tokenRRI        The symbol of the token to create
	 * @param name            The name of the token to create
	 * @param description     A description of the token
	 * @param iconUrl         The URL for the token's icon
	 * @param url             The URL For the otken
	 * @param initialSupply   The initial amount of supply for this token
	 * @param granularity     The least multiple of subunits per transaction for this token
	 * @param tokenSupplyType The type of supply for this token: Fixed or Mutable
	 * @return result of the transaction
	 */
	public Result createToken(
		RRI tokenRRI,
		String name,
		String description,
		String iconUrl,
		String url,
		BigDecimal initialSupply,
		BigDecimal granularity,
		TokenSupplyType tokenSupplyType
	) {
		final CreateTokenAction tokenCreation = CreateTokenAction.create(
			tokenRRI,
			name,
			description,
			iconUrl,
			url,
			initialSupply,
			granularity,
			tokenSupplyType
		);
		return execute(tokenCreation);
	}

	/**
	 * Mints an amount of new tokens into the user's account
	 *
	 * @param token  The symbol of the token to mint
	 * @param amount The amount to mint
	 * @return result of the transaction
	 */
	public Result mintTokens(RRI token, BigDecimal amount) {
		MintTokensAction mintTokensAction = MintTokensAction.create(token, getAddress(), amount);
		return execute(mintTokensAction);
	}

	/**
	 * Burns an amount of tokens in the user's account
	 *
	 * @param token  The symbol of the token to mint
	 * @param amount The amount to mint
	 * @return result of the transaction
	 */
	public Result burnTokens(RRI token, BigDecimal amount) {
		BurnTokensAction burnTokensAction = BurnTokensAction.create(token, getAddress(), amount);
		return execute(burnTokensAction);
	}

	/**
	 * Transfers an amount of a token to an address
	 *
	 * @param token  the symbol of the token
	 * @param to     the address to transfer tokens to
	 * @param amount the amount and token type
	 * @return result of the transaction
	 */
	public Result sendTokens(RRI token, RadixAddress to, BigDecimal amount) {
		return sendTokens(token, getAddress(), to, amount);
	}

	/**
	 * Transfers an amount of a token with a message attachment to an address
	 *
	 * @param token  the symbol of the token
	 * @param to      the address to transfer tokens to
	 * @param amount  the amount and token type
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

		return sendTokens(token, getAddress(), to, amount, attachment);
	}

	/**
	 * Transfers an amount of tokens with an attachment to an address
	 *
	 * @param token  the symbol of the token
	 * @param to         the address to send tokens to
	 * @param amount     the amount and token type
	 * @param attachment the data attached to the transaction
	 * @return result of the transaction
	 */
	public Result sendTokens(RRI token, RadixAddress to, BigDecimal amount, @Nullable byte[] attachment) {
		return sendTokens(token, getAddress(), to, amount, attachment);
	}

	/**
	 * Transfers an amount of tokens to an address
	 *
	 * @param token  the symbol of the token
	 * @param from   the address to send tokens from
	 * @param to     the address to send tokens to
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
	 * @param token      the symbol of the token
	 * @param from       the address to send tokens from
	 * @param to         the address to send tokens to
	 * @param amount     the amount and token type
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
	 * Stakes a certain amount of a token from this address to a delegate.
	 *
	 * @param amount     the amount of the token type
	 * @param token      the token type
	 * @param delegate   the address to delegate the staked tokens to
	 * @return result of the transaction
	 */
	public Result stakeTokens(
		BigDecimal amount,
		RRI token,
		RadixAddress delegate
	) {
		return stakeTokens(amount, token, getAddress(), delegate);
	}

	/**
	 * Stakes a certain amount of a token from an address to a delegate.
	 *
	 * @param amount     the amount of the token type
	 * @param token      the token type
	 * @param from       the address to stake tokens from
	 * @param delegate   the address to delegate the staked tokens to
	 * @return result of the transaction
	 */
	public Result stakeTokens(
		BigDecimal amount,
		RRI token,
		RadixAddress from,
		RadixAddress delegate
	) {
		Objects.requireNonNull(amount);
		Objects.requireNonNull(token);
		Objects.requireNonNull(from);
		Objects.requireNonNull(delegate);

		return this.execute(StakeTokensAction.create(amount, token, from, delegate));
	}

	/**
	 * Unstakes a certain amount of a token from this address to a delegate.
	 *
	 * @param amount     the amount of the token type
	 * @param token      the token type
	 * @param delegate   the address to delegate the staked tokens to
	 * @return result of the transaction
	 */
	public Result unstakeTokens(
		BigDecimal amount,
		RRI token,
		RadixAddress delegate
	) {
		return unstakeTokens(amount, token, getAddress(), delegate);
	}

	/**
	 * Unstakes a certain amount of a token from an address to a delegate.
	 *
	 * @param from       the address to stake tokens from
	 * @param delegate   the address to delegate the staked tokens to
	 * @param amount     the amount of the token type
	 * @param token      the token type
	 * @return result of the transaction
	 */
	public Result unstakeTokens(
		BigDecimal amount,
		RRI token,
		RadixAddress from,
		RadixAddress delegate
	) {
		Objects.requireNonNull(amount);
		Objects.requireNonNull(token);
		Objects.requireNonNull(from);
		Objects.requireNonNull(delegate);

		return this.execute(UnstakeTokensAction.create(amount, token, from, delegate));
	}

	/**
	 * Registers the given address as a validator.
	 *
	 * @param validator the validator address to be registered
     * @param allowedDelegators the allowed delegators, or empty if everyone is allowed
	 * @return result of the transaction
	 */
	public Result registerValidator(
		RadixAddress validator,
		Set<RadixAddress> allowedDelegators
	) {
		return registerValidator(validator, allowedDelegators, null);
	}

	/**
	 * Registers the given address as a validator.
	 *
	 * @param validator the validator address to be registered
	 * @param allowedDelegators the allowed delegators, or empty if everyone is allowed
	 * @param url the optional URL for extra information about the validator
	 * @return result of the transaction
	 */
	public Result registerValidator(
		RadixAddress validator,
		Set<RadixAddress> allowedDelegators,
		String url
	) {
		final RegisterValidatorAction registerValidatorAction = new RegisterValidatorAction(validator, allowedDelegators, url);

		return this.execute(registerValidatorAction);
	}

	/**
	 * Unregisters the given address as a validator.
	 *
	 * @param validator the validator address to be unregistered
	 * @return result of the transaction
	 */
	public Result unregisterValidator(
		RadixAddress validator
	) {
		final UnregisterValidatorAction unregisterValidatorAction = new UnregisterValidatorAction(validator);

		return this.execute(unregisterValidatorAction);
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

	/**
	 * Immediately executes a user action onto the ledger. Note that this method is NOT
	 * idempotent.
	 *
	 * @param action     action to execute
	 * @param originNode node to submit action to
	 * @return results of the execution
	 */
	public Result execute(Action action, RadixNode originNode) {
		Transaction transaction = this.createTransaction();
		transaction.stage(action);
		return transaction.commitAndPush(originNode);
	}

	/**
	 * Returns an unsigned atom with the appropriate fees given a list of
	 * particle groups to compose the atom.
	 *
	 * @param particleGroups particle groups to include in atom
	 * @return unsigned atom with appropriate fees
	 */
	public TxLowLevelBuilder buildAtomWithFee(List<ParticleGroup> particleGroups) {
		Transaction t = createTransaction();
		particleGroups.forEach(t::stage);
		return t.buildAtom();
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
	 *
	 * @param atom                atom to submit
	 * @param completeOnStoreOnly if true, result will only complete on a store event
	 * @param originNode          the origin node
	 * @return the result of the submission
	 */
	public Result submitAtom(Atom atom, boolean completeOnStoreOnly, RadixNode originNode) {
		return createAtomSubmission(Single.just(atom), completeOnStoreOnly, originNode).connect();
	}

	/**
	 * Low level call to submit an atom into the network.
	 *
	 * @param atom                atom to submit
	 * @param completeOnStoreOnly if true, result will only complete on a store event
	 * @return the result of the submission
	 */
	public Result submitAtom(Atom atom, boolean completeOnStoreOnly) {
		return createAtomSubmission(Single.just(atom), completeOnStoreOnly, null).connect();
	}

	/**
	 * Low level call to submit an atom into the network. Result will complete
	 * on the first STORED event.
	 *
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

	public BigDecimal getMinimumRequiredFee(TxLowLevelBuilder atomWithoutFees) {
		return TokenUnitConversions.subunitsToUnits(this.universe.feeTable().feeFor(atomWithoutFees));
	}

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
						final Consumer<ActionExecutionExceptionReason> addReason = exceptionBuilder::addReason;
						atomErrorMappers.stream()
							.flatMap(
								errorMapper -> errorMapper.mapAtomErrorToExceptionReasons(status.getAtom(), errorData)
							)
							.forEach(addReason);
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
		 *
		 * @return the atom which was sent
		 */
		public Atom getAtom() {
			return cachedAtom.blockingGet();
		}

		/**
		 * A low level interface, returns an a observable of the status of an atom submission as it occurs.
		 *
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

	public static class RadixApplicationAPIBuilder {
		private RadixIdentity identity;
		private RadixUniverse universe;
		private Function<RadixUniverse, FeeProcessor> feeProcessorBuilder;
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
			Class<? extends T> actionClass,
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

		public RadixApplicationAPIBuilder feeProcessor(FeeProcessor feeProcessor) {
			this.feeProcessorBuilder = radixUniverse -> feeProcessor;
			return this;
		}

		public RadixApplicationAPIBuilder defaultFeeProcessor() {
			this.feeProcessorBuilder = u -> new TokenFeeProcessor(u.getNativeToken(), u.feeTable());
			return this;
		}

		public RadixApplicationAPIBuilder tokenFeeProcessor(RRI tokenRri) {
			this.feeProcessorBuilder = radixUniverse -> new TokenFeeProcessor(tokenRri, radixUniverse.feeTable());
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
			Objects.requireNonNull(this.feeProcessorBuilder, "Fee Mapper must be specified");
			Objects.requireNonNull(this.universe, "Universe must be specified");

			final FeeProcessor feeProcessor = this.feeProcessorBuilder.apply(this.universe);
			final RadixIdentity identity = this.identity;
			final List<ParticleReducer<? extends ApplicationState>> reducers = this.reducers;

			return new RadixApplicationAPI(
				identity,
				universe,
				feeProcessor,
				requiredStateMappers.build(),
				actionMappers.build(),
				reducers,
				atomMappers,
				atomErrorMappers
			);
		}
	}

	/**
	 * Represents an atomic transaction to be committed to the ledger
	 */
	public final class Transaction {
		private final String uuid;
		private List<Action> workingArea = new ArrayList<>();
		private String message = null;

		private Transaction() {
			this.uuid = UUID.randomUUID().toString();
		}

		/**
		 * Sets the atom's message to the specified message.
		 *
		 * @param message The message to use for the atom
		 */
		public void setMessage(String message) {
			this.message = message;
		}

		/**
		 * Add an action to the working area
		 *
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
				throw new IllegalArgumentException(
						String.format("Unknown action class: %s. Available: %s", action.getClass(), actionMappers.keySet())
				);
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
		 * Add a particle group to staging area in preparation for commitAndPush.
		 *
		 * @param particleGroup Particle group to add to staging area.
		 */
		public void stage(ParticleGroup particleGroup) {
			universe.getAtomStore().stageParticleGroup(uuid, particleGroup);
		}

		/**
		 * Creates an atom composed of all of the currently staged particles.
		 * If the specified fee is non-null, a fee of that amount will be included in
		 * the built atom, otherwise the fee will be computed based on the atom properties.
		 *
		 * @param fee the fee to include in the atom, or {@code null} if the fee should be computed
		 * @return an unsigned atom
		 */
		public TxLowLevelBuilder buildAtomWithFee(@Nullable BigDecimal fee) {
			var feelessBuilder = universe.getAtomStore().getStaged(this.uuid);
			feelessBuilder.message(this.message);
			feeProcessor.process(this::actionProcessor, getAddress(), feelessBuilder, Optional.ofNullable(fee));

			var builder = universe.getAtomStore().getStagedAndClear(this.uuid);
			builder.message(this.message);
			this.message = null;

			return builder;
		}

		/**
		 * Creates an atom composed of all of the currently staged particles.
		 *
		 * @return an unsigned atom
		 */
		public TxLowLevelBuilder buildAtom() {
			return buildAtomWithFee(null);
		}

		/**
		 * Commit the transaction onto the ledger.
		 * If the specified fee is non-null, a fee of that amount will be included in
		 * the built atom, otherwise the fee will be computed based on the atom properties.
		 *
		 * @param fee the fee to include in the atom, or {@code null} if the fee should be computed
		 * @return the results of committing
		 */
		public Result commitAndPushWithFee(@Nullable BigDecimal fee) {
			final TxLowLevelBuilder unsignedAtom = buildAtomWithFee(fee);
			final Single<Atom> atom = identity.addSignature(unsignedAtom);
			return createAtomSubmission(atom, false, null).connect();
		}

		/**
		 * Commit the transaction onto the ledger. Fee particles will be added to the atom.
		 *
		 * @return the results of committing
		 */
		public Result commitAndPush() {
			return commitAndPushWithFee(null);
		}

		/**
		 * Commit the transaction onto the ledger. No fee particles will be added.
		 *
		 * @return the results of committing
		 */
		public Result commitAndPushWithoutFee() {
			return commitAndPushWithFee(BigDecimal.ZERO);
		}

		/**
		 * Commit the transaction onto the ledger via the specified node.
		 * If the specified fee is non-null, a fee of that amount will be included in
		 * the built atom, otherwise the fee will be computed based on the atom properties.
		 *
		 * @param originNode the originNode to push to
		 * @param fee the fee to include in the atom, or {@code null} if the fee should be computed
		 * @return the results of committing
		 */
		public Result commitAndPushWithFee(RadixNode originNode, @Nullable BigDecimal fee) {
			final TxLowLevelBuilder unsignedAtom = buildAtomWithFee(fee);
			final Single<Atom> atom = identity.addSignature(unsignedAtom);
			return createAtomSubmission(atom, false, originNode).connect();
		}

		/**
		 * Commit the transaction onto the ledger
		 *
		 * @param originNode the originNode to push to
		 * @return the results of committing
		 */
		public Result commitAndPush(RadixNode originNode) {
			return commitAndPushWithFee(originNode, null);
		}

		/**
		 * Gets the unique identifier of this transaction
		 *
		 * @return the unique identifier of this transaction
		 */
		public String getUuid() {
			return uuid;
		}

		private void actionProcessor(Action action) {
			stage(action);
		}
	}
}
