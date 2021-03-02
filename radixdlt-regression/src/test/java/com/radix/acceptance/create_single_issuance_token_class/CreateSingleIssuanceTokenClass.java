/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radix.acceptance.create_single_issuance_token_class;

import com.radixdlt.client.application.RadixApplicationAPI.Transaction;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.radix.acceptance.SpecificProperties;
import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.identifiers.RadixAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import com.radixdlt.utils.UInt256;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.reactivex.observers.TestObserver;

/**
 * See <a href="https://radixdlt.atlassian.net/browse/RLAU-40">RLAU-40</a>.
 */
public class CreateSingleIssuanceTokenClass {
	private static final String NAME = "name";
	private static final String SYMBOL = "symbol";
	private static final String DESCRIPTION = "description";
	private static final String TOTAL_SUPPLY = "totalSupply";
	private static final String GRANULARITY = "granularity";

	private RadixApplicationAPI api;
	private RadixNode nodeConnection;
	private RadixIdentity identity;
	private final SpecificProperties properties = SpecificProperties.of(
		NAME,           "RLAU-40 Test token",
		SYMBOL,			"RLAU",
		DESCRIPTION,	"RLAU-40 Test token",
		TOTAL_SUPPLY,	"1000000000",
		GRANULARITY,	"1"
	);
	private final List<TestObserver<Object>> observers = Lists.newArrayList();

	@Given("^I have access to a suitable Radix network$")
	public void i_have_access_to_a_suitable_Radix_network() {
		this.identity = RadixIdentities.createNew();
		this.api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), this.identity);
		TokenUtilities.requestTokensFor(this.api);
		this.api.discoverNodes();
		this.nodeConnection = this.api.getNetworkState()
			.map(RadixNetworkState::getNodes)
			.filter(s -> !s.isEmpty())
			.map(s -> s.iterator().next())
			.firstOrError()
			.blockingGet();

		// Reset data
		this.properties.clear();
		this.observers.clear();
	}

	@When("^I submit a fixed-supply token-creation request "
			+ "with name \"([^\"]*)\", symbol \"([^\"]*)\", totalSupply (\\d+) scaled and granularity (\\d+) scaled$")
	public void i_submit_a_fixed_supply_token_creation_request_with_name_symbol_totalSupply_scaled_and_granularity_scaled(
			String name, String symbol, int totalSupply, int granularity) {
		this.properties.put(NAME, name);
		this.properties.put(SYMBOL, symbol);
		this.properties.put(TOTAL_SUPPLY, Integer.toString(totalSupply));
		this.properties.put(GRANULARITY, Integer.toString(granularity));
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit a fixed-supply token-creation request "
		+ "with name \"([^\"]*)\", description \"([^\"]*)\", symbol \"([^\"]*)\", totalSupply (\\d+) scaled and granularity (\\d+) scaled$")
	public void i_submit_a_fixed_supply_token_creation_request_with_name_description_symbol_totalSupply_scaled_and_granularity_scaled(
			String name, String description, String symbol, int totalSupply, int granularity) {
		this.properties.put(NAME, name);
		this.properties.put(DESCRIPTION, description);
		this.properties.put(SYMBOL, symbol);
		this.properties.put(TOTAL_SUPPLY, Integer.toString(totalSupply));
		this.properties.put(GRANULARITY, Integer.toString(granularity));
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit a fixed-supply token-creation request with symbol \"([^\"]*)\" and totalSupply (\\d+) scaled$")
	public void i_submit_a_fixed_supply_token_creation_request_with_symbol_totalSupply(String symbol, int totalSupply) {
		this.properties.put(SYMBOL, symbol);
		this.properties.put(TOTAL_SUPPLY, Integer.toString(totalSupply));
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit a fixed-supply token-creation request with granularity (\\d+) scaled$")
	public void i_submit_a_fixed_supply_token_creation_request_with_granularity(int granularity) {
		this.properties.put(GRANULARITY, Integer.toString(granularity));
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit a fixed-supply token-creation request with granularity 0$")
	public void i_submit_a_fixed_supply_token_creation_request_with_granularity_0() {
		this.properties.put(GRANULARITY, "0");
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit a fixed-supply token-creation request with symbol \"([^\"]*)\"$")
	public void i_submit_a_fixed_supply_token_creation_request_with_symbol(String symbol) {
		this.properties.put(SYMBOL, symbol);
		createToken(CreateTokenAction.TokenSupplyType.FIXED);
	}

	@When("^I submit a token transfer request of (\\d+) scaled for \"([^\"]*)\" to an arbitrary account$")
	public void i_submit_a_token_transfer_request_of_for_to_an_arbitrary_account(int count, String symbol) {
		RRI tokenClass = RRI.of(api.getAddress(), symbol);
		RadixAddress arbitrary = api.getAddress(RadixIdentities.createNew().getPublicKey());
		// Ensure balance is up-to-date.
		api.observeBalance(api.getAddress(), tokenClass)
			.firstOrError()
			.blockingGet();

		TestObserver<Object> observer = new TestObserver<>();

		TransferTokensAction transferTokensAction = TransferTokensAction.create(
			tokenClass, api.getAddress(), arbitrary, BigDecimal.valueOf(count)
		);

		Transaction tx = api.createTransaction();
		tx.stage(transferTokensAction);
		tx.commitAndPush(nodeConnection)
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);
		this.observers.add(observer);
	}

	@When("^I observe the atom being accepted$")
	public void i_observe_the_atom_being_accepted() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_accepted(observers.size());
	}

	@Then("^I can observe the atom being accepted$")
	public void i_can_observe_the_atom_being_accepted() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_accepted(observers.size());
	}

	@Then("^I can observe atom (\\d+) being accepted$")
	public void i_can_observe_atom_being_accepted(int atomNumber) {
		awaitAtomStatus(atomNumber, AtomStatus.STORED);
	}

	@Then("^I can observe the atom being rejected with a validation error$")
	public void i_can_observe_the_atom_being_rejected_as_a_validation_error() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_rejected_as_a_validation_error(observers.size());
	}

	@Then("^I can observe atom (\\d+) being rejected with a validation error$")
	public void i_can_observe_atom_being_rejected_as_a_validation_error(int atomNumber) {
		awaitAtomStatus(atomNumber, AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
	}

	@Then("^I can observe the atom being rejected with an error$")
	public void i_can_observe_atom_being_rejected_with_an_error() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_rejected_with_an_error(observers.size());
	}

	@Then("^I can observe atom (\\d+) being rejected with an error$")
	public void i_can_observe_atom_being_rejected_with_an_error(int atomNumber) {
		awaitAtomStatus(atomNumber, AtomStatus.CONFLICT_LOSER, AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
	}

	@Then("^I can observe token \"([^\"]*)\" balance equal to (\\d+) scaled$")
	public void i_can_observe_token_balance_equal_to_scaled(String symbol, int balance) {
		RRI tokenClass = RRI.of(api.getAddress(), symbol);
		// Ensure balance is up-to-date.
		BigDecimal tokenBalanceDecimal = api.observeBalance(api.getAddress(), tokenClass)
			.firstOrError()
			.blockingGet();
		UInt256 tokenBalance = TokenUnitConversions.unitsToSubunits(tokenBalanceDecimal);
		UInt256 requiredBalance = TokenUnitConversions.unitsToSubunits(balance);
		assertEquals(requiredBalance, tokenBalance);
	}

	private void createToken(CreateTokenAction.TokenSupplyType tokenCreateSupplyType) {
		TestObserver<Object> observer = new TestObserver<>();
		Transaction tx = api.createTransaction();
		CreateTokenAction createTokenAction = CreateTokenAction.create(
			RRI.of(api.getAddress(), this.properties.get(SYMBOL)),
			this.properties.get(NAME),
			this.properties.get(DESCRIPTION),
			BigDecimal.valueOf(Long.valueOf(this.properties.get(TOTAL_SUPPLY))),
			BigDecimal.valueOf(Long.valueOf(this.properties.get(GRANULARITY))),
			tokenCreateSupplyType
		);
		tx.stage(createTokenAction);
		tx.commitAndPush(nodeConnection)
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);
		this.observers.add(observer);
	}

	private void awaitAtomStatus(int atomNumber, AtomStatus... finalStates) {
		ImmutableSet<AtomStatus> finalStatesSet = ImmutableSet.<AtomStatus>builder()
			.addAll(Arrays.asList(finalStates))
			.build();

		TestObserver<Object> testObserver = this.observers.get(atomNumber - 1);
		testObserver.awaitTerminalEvent();
		testObserver.assertNoErrors();
		testObserver.assertNoTimeout();
		List<Object> events = testObserver.values();
		assertThat(events).extracting(o -> o.getClass().toString())
			.startsWith(SubmitAtomSendAction.class.toString());
		assertThat(events).last()
			.isInstanceOf(SubmitAtomStatusAction.class)
			.extracting(o -> SubmitAtomStatusAction.class.cast(o).getStatusNotification().getAtomStatus())
			.isIn(finalStatesSet);
	}
}
