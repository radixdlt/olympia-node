package com.radix.acceptance.create_single_issuance_token_class;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokenclasses.CreateTokenAction;
import com.radixdlt.client.application.translate.tokenclasses.TokenState;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;

import static com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState.COLLISION;
import static com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState.STORED;
import static com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState.SUBMITTED;
import static com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState.SUBMITTING;
import static com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState.VALIDATION_ERROR;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.reactivex.observers.BaseTestConsumer.TestWaitStrategy;
import io.reactivex.observers.TestObserver;

/**
 * See <a href="https://radixdlt.atlassian.net/browse/RLAU-40">RLAU-40</a>.
 */
public class CreateSingleIssuanceTokenClass {
	static {
		RadixUniverse.bootstrap(Bootstrap.BETANET);
	}

	private RadixApplicationAPI api;
	private RadixIdentity identity;
	private final Map<String, String> properties = Maps.newHashMap();
	private final List<TestObserver<Object>> observers = Lists.newArrayList();
	private final List<TokenState> tokenStates = Lists.newArrayList();

	@Given("^I have access to suitable development tools$")
	public void i_have_access_to_suitable_development_tools() {
		// Assumed correct, as this code needs to run somewhere
	}

	@Given("^I have included the radixdlt-java library$")
	public void i_have_included_the_radixdlt_java_library() {
		// Assumed correct, as compile time errors would have occurred
	}

	@Given("^I have access to a suitable Radix network$")
	public void i_have_access_to_a_suitable_Radix_network() {
		this.identity = RadixIdentities.createNew();
		this.api = RadixApplicationAPI.create(identity);
		api.pull();

		// Reset data
		resetProperties();
		this.observers.clear();
		this.tokenStates.clear();
	}

	@When("^property \"([^\"]*)\" = \"([^\"]*)\"$")
	public void property(String name, String value) {
		addProperty(name, value);
	}

	@When("^property \"([^\"]*)\" = (\\d+)$")
	public void property(String name, int value) {
		addProperty(name, Integer.toString(value));
	}

	@When("^I submit a token-creation request$")
	public void i_submit_a_token_creation_request() {
		TestObserver<Object> observer = new TestObserver<>();
		TokenState tokenState = new TokenState(
				this.properties.get("name"),
				this.properties.get("symbol"),
				this.properties.get("description"),
				BigDecimal.valueOf(Integer.valueOf(this.properties.get("totalSupply"))),
				TokenState.TokenSupplyType.FIXED);      // fixed supply token
		api.createToken(
				tokenState.getName(),
				tokenState.getIso(),
				tokenState.getDescription(),
				TokenClassReference.unitsToSubunits(tokenState.getTotalSupply()),
				CreateTokenAction.TokenSupplyType.FIXED)
			.toObservable()
			.map(Utils::print)
			.map(AtomSubmissionUpdate::getState)
			.subscribe(observer);
		this.observers.add(observer);
		this.tokenStates.add(tokenState);
	}

	@Then("^I can observe the atom being accepted$")
	public void i_can_observe_the_atom_being_accepted() {
		// "the atom" = atom 1
		i_can_observe_atom_being_accepted(1);
	}

	@Then("^I can observe atom (\\d+) being accepted$")
	public void i_can_observe_atom_being_accepted(int atomNumber) {
		this.observers.get(atomNumber - 1)
			.awaitCount(3, TestWaitStrategy.SLEEP_100MS, 10_000L)
			.assertNoErrors()
			.assertNoTimeout()
			.assertValues(SUBMITTING, SUBMITTED, STORED);
	}

	@Then("^I can observe atom (\\d+) being rejected as a collision$")
	public void i_can_observe_atom_being_rejected_as_a_collision(int atomNumber) {
		this.observers.get(atomNumber - 1)
			.awaitCount(3, TestWaitStrategy.SLEEP_100MS, 10_000L)
			.assertNoErrors()
			.assertNoTimeout()
			.assertValues(SUBMITTING, SUBMITTED, COLLISION);
	}

	@Then("^I can observe the atom being rejected with a validation error$")
	public void i_can_observe_the_atom_being_rejected_as_a_validation_error() {
		i_can_observe_atom_being_rejected_as_a_validation_error(1);
	}

	@Then("^I can observe atom (\\d+) being rejected with a validation error$")
	public void i_can_observe_atom_being_rejected_as_a_validation_error(int atomNumber) {
		this.observers.get(atomNumber - 1)
			.awaitCount(3, TestWaitStrategy.SLEEP_100MS, 10_000L)
			.assertNoErrors()
			.assertNoTimeout()
			.assertValues(SUBMITTING, SUBMITTED, VALIDATION_ERROR);
	}

	private void resetProperties() {
		this.properties.clear();
		this.properties.put("name", null);
		this.properties.put("symbol", null);
		this.properties.put("description", null);
		this.properties.put("totalSupply", null);
		this.properties.put("granularity", null);
	}

	private void addProperty(String name, String value) {
		if (!this.properties.containsKey(name)) {
			throw new AssertionError("Unknown property: " + name);
		}
		this.properties.put(name, value);
	}
}
