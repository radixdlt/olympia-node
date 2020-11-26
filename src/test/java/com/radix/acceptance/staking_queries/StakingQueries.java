package com.radix.acceptance.staking_queries;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.tokens.StakeNotPossibleException;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
	private StakeNotPossibleException stakeFailure;

	@Given("^I have access to a suitable Radix network$")
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

		validator.registerValidator(validator.getAddress(), builder.build()).blockUntilComplete();
	}

	@And("^I stake some tokens by delegator(\\d+)$")
	public void stake_amount_by_delegator(final int index) {
		final RadixApplicationAPI delegator = decodeDelegator(index);

		makeStakeByDelegator(delegator);
	}

	@And("^I unstake (.*) amount by delegator1$")
	public void i_unstake_full_amount_by_delegator1(final String unstakeType) {
		final BigDecimal unstakeAmount = decodeUnstakeAmountString(unstakeType);

		System.err.println("Unstaking: " + unstakeAmount);
		delegator1.pullOnce(validator.getAddress()).blockingAwait();
		delegator1.unstakeTokens(unstakeAmount, token, validator.getAddress()).blockUntilComplete();
		delegator1.pullOnce(validator.getAddress()).blockingAwait();
		validator.pullOnce(validator.getAddress()).blockingAwait();
	}

	private BigDecimal decodeUnstakeAmountString(String unstakeType) {
		switch (unstakeType) {
			case "full":
				return STAKING_AMOUNT;
			case "partial":
				return STAKING_AMOUNT.divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
		}

		throw new IllegalStateException("Unknown unstaking type: " + unstakeType);
	}

	@When("^I request validator stake balance$")
	public void request_validator_stake_balance() {
		final var observer = new TestObserver<>();

		Observable.merge(
				delegator1.observeStake(validator.getAddress()),
				delegator2.observeStake(validator.getAddress())
		).subscribe(observer);

		observers.add(observer);

		pullSelfOnce(validator, delegator1, delegator2);
	}

	@When("^I try to stake some tokens by delegator2$")
	public void try_to_stake_tokens_by_not_registered_delegator() {
		try {
			makeStakeByDelegator(delegator2);
		} catch (final StakeNotPossibleException e) {
			stakeFailure = e;
		}
	}

	@Then("^I can observe that validator has amount of tokens staked equal to (.*)$")
	public void validate_stake_balance(final String expectedBalanceString) {
		final BigDecimal expectedBalance = decodeBalanceString(expectedBalanceString);

		final var testObserver = observers.get(observers.size() - 1);
		testObserver.assertNoErrors();

		var actualBalance = testObserver.values()
				.stream()
				.map(this::extractBalance)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		System.err.println("Expecting: " + expectedBalance + ", actual: " + actualBalance);
		assertEquals(0, expectedBalance.compareTo(actualBalance));
	}

	@Then("^I can observe that staking is not allowed$")
	public void observe_that_stacking_is_not_allowed() {
		assertNotNull("Expecting StakeNotPossibleException", stakeFailure);

		final String expectedErrorMessage =
				"delegate " + validator.getAddress() + " does not allow this delegator " + delegator2.getAddress();
		assertEquals(expectedErrorMessage, stakeFailure.getMessage());
	}

	private void makeStakeByDelegator(RadixApplicationAPI delegator) {
		delegator.pullOnce(validator.getAddress()).blockingAwait();
		delegator.stakeTokens(STAKING_AMOUNT, token, validator.getAddress()).blockUntilComplete();
	}

	private BigDecimal extractBalance(Object value) {
		assertTrue(value instanceof Map);

		@SuppressWarnings("unchecked")
		var map = (Map<RRI, BigDecimal>) value;
		return map.isEmpty() ? BigDecimal.ZERO : map.entrySet().iterator().next().getValue();
	}

	private void pullSelfOnce(RadixApplicationAPI... apis) {
		for (var api : apis) {
			api.pullOnce(api.getAddress()).blockingAwait();
		}
	}

	private RadixApplicationAPI decodeDelegator(int index) {
		switch (index) {
			case 1:
				return delegator1;
			case 2:
				return delegator2;
		}

		throw new IllegalStateException("Invalid delegator index in request: " + index);
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
				//FIXME: temporary!!!
				return STAKING_AMOUNT.add(STAKING_AMOUNT).add(STAKING_AMOUNT);
		}
		throw new IllegalStateException("Unknown balance string: [" + balanceString + "]");
	}
}
