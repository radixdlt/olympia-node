package com.radix.acceptance.staking_queries;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.reactivex.observers.TestObserver;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * See <a href="https://radixdlt.atlassian.net/browse/RPNV1-379">RPNV1-379: Developer - Staking queries</a>.
 */
public class StakingQueries {
	private static final BigDecimal STAKING_AMOUNT = BigDecimal.valueOf(10000.0);
	private static final BigDecimal PARTIAL_UNSTAKING_AMOUNT = BigDecimal.valueOf(3000.0);
	private static final BigDecimal SUPPLY_AMOUNT = BigDecimal.valueOf(1000000.0);

	private final List<TestObserver<Object>> observers = Lists.newArrayList();

	private RadixApplicationAPI validator;
	private RadixApplicationAPI delegator1;
	private RadixApplicationAPI delegator2;

	private RRI token;

	@Given("^I have access to a suitable Radix network$")
	@Test
	public void i_have_access_to_a_suitable_Radix_network() {
		validator = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
		delegator1 = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
		delegator2 = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());

		validator.discoverNodes();
		delegator1.discoverNodes();
		delegator2.discoverNodes();

		TokenUtilities.requestTokensFor(validator);
		TokenUtilities.requestTokensFor(delegator1);
		TokenUtilities.requestTokensFor(delegator2);

		token = RRI.of(validator.getAddress(), "RPNV1");
		validator.createFixedSupplyToken(token, "RPNV1", "TEST", SUPPLY_AMOUNT).blockUntilComplete();

		validator.sendTokens(token, delegator1.getAddress(), STAKING_AMOUNT).blockUntilComplete();
		validator.sendTokens(token, delegator2.getAddress(), STAKING_AMOUNT).blockUntilComplete();

		register_validator_with_delegators("", "");
		stake_amount_by_delegator(1);
		request_validator_stake_balance();
		validate_stake_balance("zero");
	}

	@And("^I have registered validator with allowed (delegator1)(?: and)?( delegator2)?$")
	public void register_validator_with_delegators(final String name1, final String name2) {
		final var builder = ImmutableSet.<RadixAddress>builder();

		if (name1 != null) {
			builder.add(delegator1.getAddress());
		}

		if (name2 != null) {
			builder.add(delegator2.getAddress());
		}

		//TODO: for some reason plain .blockUntilComplete() does not work (race condition?)
		final ImmutableSet<RadixAddress> allowedDelegators = builder.build();
		System.err.println("Allowed delegators: " + allowedDelegators);
		//validator.registerValidator(validator.getAddress(), allowedDelegators).toObservable().blockingSubscribe();
		validator.registerValidator(validator.getAddress(), allowedDelegators).blockUntilComplete();
	}

	@And("^I stake some tokens by delegator(\\d+)$")
	public void stake_amount_by_delegator(final int index) {
		final var delegator = index == 1
				? delegator1
				: index == 2
				? delegator2
				: null;

		if (delegator == null) {
			throw new IllegalStateException("Invalid delegator index in request: " + index);
		}
		//delegator.pullOnce(validator.getAddress()).blockingAwait();
		System.err.println("Delegator: " + delegator.getAddress() + ", delegate: " + validator.getAddress());
		delegator.stakeTokens(STAKING_AMOUNT, token, validator.getAddress()).blockUntilComplete();
//		delegator.stakeTokens(STAKING_AMOUNT, token, validator.getAddress()).toObservable().doOnNext(System.err::println).blockingSubscribe();
		delegator.pullOnce(validator.getAddress()).blockingAwait();
	}

	@When("^I request validator stake balance$")
	public void request_validator_stake_balance() {
		final var observer = new TestObserver<>();
		validator.observeStake(validator.getAddress()).subscribe(observer);
		observers.add(observer);
		//TODO: fails with NoSuchElementException with single .pullOnce() call
		validator.pullOnce(validator.getAddress()).blockingAwait();
		validator.pullOnce(validator.getAddress()).blockingAwait();
	}

	@Then("^I can observe that validator has amount of tokens staked equal to (.*)$")
	public void validate_stake_balance(final String expectedBalanceString) {
		final BigDecimal expectedBalance = decodeBalanceString(expectedBalanceString);

		final var testObserver = observers.get(observers.size() - 1);
		testObserver.assertNoErrors();

		var iterator = testObserver.values().iterator();
		assertTrue(iterator.hasNext());

		BigDecimal actualBalance = extractBalance(iterator.next());

		System.err.println("Actual value: " + actualBalance);
		assertTrue(expectedBalance.compareTo(actualBalance) == 0);
	}

	private BigDecimal extractBalance(Object value) {
		assertTrue(value instanceof Map);
		@SuppressWarnings("unchecked")
		var map = (Map<RRI, BigDecimal>) value;
		return map.isEmpty() ? BigDecimal.ZERO : map.entrySet().iterator().next().getValue();
	}

	private static BigDecimal decodeBalanceString(final String balanceString) {
		switch (balanceString) {
			case "zero":
				return BigDecimal.ZERO;
			case "amount staked by delegator":
				return STAKING_AMOUNT;
			case "initial amount minus unstaked amount":
				return STAKING_AMOUNT.subtract(PARTIAL_UNSTAKING_AMOUNT);
			case "sum of stakes by delegator1 and delegator2":
				return STAKING_AMOUNT.add(STAKING_AMOUNT);
		}
		throw new IllegalStateException("Unknown balance string: [" + balanceString + "]");
	}
//
//	@When("^I submit atom which contains two transfers with the same token type "
//			+ "from (\\d+) source address(?:es)? "
//			+ "to (\\d+) destination address(?:es)?$")
//	public void i_submit_atom_with_one_transaction(final int sources, final int destinations) {
//		submitTransfersWithAmount(sources, destinations, SMALL_AMOUNT, SMALL_AMOUNT);
//	}
//
//	@When("^I submit atom which contains two transfers with the same token type "
//			+ "from 2 source addresses to 2 destination addresses "
//			+ "where one transfer exceeds amount of available funds$")
//	public void i_submit_atom_with_two_transfers_where_one_transfer_is_too_large() {
//		try {
//			submitTransfersWithAmount(2, 2, SMALL_AMOUNT, EXCEEDING_AMOUNT);
//			fail("Transfer with insufficient funds was unexpectedly accepted");
//		} catch (InsufficientFundsException e) {
//			deferErrorDelivery();
//		}
//	}
//
//	private void deferErrorDelivery() {
//		final var observer = new TestObserver<Object>();
//		Single.just(createValidationError()).subscribe(observer);
//		observers.add(observer);
//	}
//
//	private SubmitAtomStatusAction createValidationError() {
//		return SubmitAtomStatusAction.fromStatusNotification(
//			UUID.randomUUID().toString(),
//			Atom.create(List.of()),
//			nodeConnection,
//			new AtomStatusEvent(EVICTED_FAILED_CM_VERIFICATION)
//		);
//	}
//
//	@When("^I submit atom which contains two transfers with different token types RPNV1A and RPNV1B$")
//	public void i_submit_atom_with_different_token_types() {
//		final var observer = new TestObserver<Object>();
//		final var transaction = api.createTransaction();
//		final var from1 = sourceAddress1;
//		final var from2 = sourceAddress3;
//		final var to1 = destinationAddress1;
//		final var to2 = destinationAddress3;
//
//		transaction.stage(TransferTokensAction.create(tokenA, from1, to1, SMALL_AMOUNT));
//		transaction.stage(TransferTokensAction.create(tokenB, from2, to2, SMALL_AMOUNT));
//
//		final var initialAtom = transaction.buildAtom();
//		var atom = ownerIdentity.syncAddSignature(initialAtom);
//		atom = source1.syncAddSignature(atom);
//		atom = source3.syncAddSignature(atom);
//
//		System.out.println("Atom: " + atom);
//
//		api.submitAtom(atom, false, nodeConnection)
//				.toObservable()
//				.subscribe(observer);
//		observers.add(observer);
//	}
//
//	@Then("^I can observe the atom being accepted$")
//	public void i_can_observe_the_atom_being_accepted() {
//		awaitAtomStatus(observers.size(), AtomStatus.STORED);
//	}
//
//	@Then("^I can observe the atom being rejected with a validation error$")
//	public void i_can_observe_the_atom_being_rejected_with_a_validation_error() {
//		awaitAtomStatus(observers.size(), EVICTED_FAILED_CM_VERIFICATION);
//	}
//
//	private void awaitAtomStatus(final int atomNumber, final AtomStatus... finalStates) {
//		final var finalStatesSet = ImmutableSet.<AtomStatus>builder()
//				.addAll(Arrays.asList(finalStates))
//				.build();
//
//		final var testObserver = observers.get(atomNumber - 1);
//		testObserver.awaitTerminalEvent();
//		testObserver.assertNoErrors();
//		testObserver.assertNoTimeout();
//
//		final var events = testObserver.values();
//
//		events.forEach(System.out::println);
//
//		assertThat(events).last()
//				.isInstanceOf(SubmitAtomStatusAction.class)
//				.extracting(o -> ((SubmitAtomStatusAction) o).getStatusNotification().getAtomStatus())
//				.isIn(finalStatesSet);
//	}
//
//	private void submitTransfersWithAmount(
//			final int sources,
//			final int destinations,
//			final BigDecimal transferAmount1,
//			final BigDecimal transferAmount2
//	) {
//		final var observer = new TestObserver<Object>();
//		final var transaction = api.createTransaction();
//		final var from1 = sourceAddress1;
//		final var from2 = sources == 1 ? sourceAddress1 : sourceAddress2;
//		final var to1 = destinationAddress1;
//		final var to2 = destinations == 1 ? destinationAddress1 : destinationAddress2;
//		final var twoTransfers = !(from1.equals(from2) && to1.equals(to2));
//
//		transaction.stage(TransferTokensAction.create(tokenA, from1, to1, transferAmount1));
//
//		if (twoTransfers) {
//			transaction.stage(TransferTokensAction.create(tokenA, from2, to2, transferAmount2));
//		}
//
//		final var initialAtom = transaction.buildAtom();
//		var atom = ownerIdentity.syncAddSignature(initialAtom);
//		atom = source1.syncAddSignature(atom);
//
//		if (sources > 1) {
//			atom = source2.syncAddSignature(atom);
//		}
//
//		System.out.println("Atom: " + atom);
//
//		api.submitAtom(atom, false, nodeConnection)
//				.toObservable()
//				.subscribe(observer);
//		observers.add(observer);
//	}
}
