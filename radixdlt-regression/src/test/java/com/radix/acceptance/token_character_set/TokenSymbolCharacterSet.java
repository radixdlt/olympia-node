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

package com.radix.acceptance.token_character_set;

import static org.assertj.core.api.Assertions.assertThat;

import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.identifiers.RRI;
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
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.reactivex.observers.TestObserver;

public class TokenSymbolCharacterSet {
	private static final String NAME = "name";
	private static final String SYMBOL = "symbol";
	private static final String DESCRIPTION = "description";
	private static final String INITIAL_SUPPLY = "initialSupply";
	private static final String NEW_SUPPLY = "newSupply";
	private static final String GRANULARITY = "granularity";

	private RadixApplicationAPI api;
	private RadixIdentity identity;
	private final SpecificProperties properties = SpecificProperties.of(
		NAME,           "RLAU-40 Test token",
		SYMBOL,			"RLAU",
		DESCRIPTION,	"RLAU-40 Test token",
		INITIAL_SUPPLY,	"1000000000",
		NEW_SUPPLY,		"1000000000",
		GRANULARITY,	"1"
	);
	private final List<TestObserver<Object>> observers = Lists.newArrayList();

	@Given("^I have access to a suitable Radix network$")
	public void i_have_access_to_a_suitable_Radix_network() {
		this.identity = RadixIdentities.createNew();
		this.api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), this.identity);
		TokenUtilities.requestTokensFor(this.api);

		// Reset data
		this.properties.clear();
		this.observers.clear();
	}

	@When("^I submit a mutable-supply token-creation request with symbol \"([^\"]*)\"$")
	public void i_submit_a_mutable_supply_token_creation_request_with_symbol(String symbol) {
		this.properties.put(SYMBOL, symbol);
		createToken(CreateTokenAction.TokenSupplyType.MUTABLE);
	}

	@Then("^I can observe the atom being accepted$")
	public void i_can_observe_the_atom_being_accepted() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_accepted(observers.size());
	}

	@Then("^I can observe atom (\\d+) being accepted$")
	public void i_can_observe_atom_being_accepted(int atomNumber) {
		awaitAtomStatus4(atomNumber, AtomStatus.STORED);
	}

	@Then("^I can observe the atom being rejected with a validation error$")
	public void i_can_observe_the_atom_being_rejected_as_a_validation_error() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_rejected_as_a_validation_error(observers.size());
	}

	@Then("^I can observe atom (\\d+) being rejected with a validation error$")
	public void i_can_observe_atom_being_rejected_as_a_validation_error(int atomNumber) {
		awaitAtomStatus4(atomNumber, AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
	}

	@Then("^I can observe the atom being rejected with a failure$")
	public void i_can_observe_the_atom_being_rejected_as_a_failure() {
		// "the atom" = most recent atom
		i_can_observe_atom_being_rejected_as_a_failure(observers.size());
	}

	@Then("^I can observe atom (\\d+) being rejected with a failure$")
	public void i_can_observe_atom_being_rejected_as_a_failure(int atomNumber) {
		awaitAtomStatus3(atomNumber, AtomStatus.EVICTED_INVALID_ATOM);
	}

	private void createToken(CreateTokenAction.TokenSupplyType tokenCreateSupplyType) {
		TestObserver<Object> observer = new TestObserver<>();
		api.createToken(
				RRI.of(api.getAddress(), this.properties.get(SYMBOL)),
				this.properties.get(NAME),
				this.properties.get(DESCRIPTION),
				BigDecimal.valueOf(Long.valueOf(this.properties.get(INITIAL_SUPPLY))),
				BigDecimal.valueOf(Long.valueOf(this.properties.get(GRANULARITY))),
				tokenCreateSupplyType)
			.toObservable()
			.doOnNext(this::printSubmitAtomAction)
			.subscribe(observer);
		this.observers.add(observer);
	}

	private void awaitAtomStatus3(int atomNumber, AtomStatus... finalStates) {
		ImmutableSet<AtomStatus> finalStatesSet = ImmutableSet.<AtomStatus>builder()
			.addAll(Arrays.asList(finalStates))
			.build();

		TestObserver<Object> testObserver = this.observers.get(atomNumber - 1);
		testObserver.awaitTerminalEvent();
		testObserver.assertNoErrors();
		testObserver.assertNoTimeout();
		List<Object> events = testObserver.values();
		assertThat(events).extracting(o -> o.getClass().toString())
			.startsWith(
				SubmitAtomRequestAction.class.toString(),
				SubmitAtomSendAction.class.toString()
			);
		assertThat(events).last()
			.isInstanceOf(SubmitAtomStatusAction.class)
			.extracting(o -> SubmitAtomStatusAction.class.cast(o).getStatusNotification().getAtomStatus())
			.isIn(finalStatesSet);
	}

	private void awaitAtomStatus4(int atomNumber, AtomStatus... finalStates) {
		ImmutableSet<AtomStatus> finalStatesSet = ImmutableSet.<AtomStatus>builder()
			.addAll(Arrays.asList(finalStates))
			.build();

		TestObserver<Object> testObserver = this.observers.get(atomNumber - 1);
		testObserver.awaitTerminalEvent();
		testObserver.assertNoErrors();
		testObserver.assertNoTimeout();
		List<Object> events = testObserver.values();
		assertThat(events).extracting(o -> o.getClass().toString())
			.startsWith(
				SubmitAtomRequestAction.class.toString(),
				SubmitAtomSendAction.class.toString()
			);
		assertThat(events).last()
			.isInstanceOf(SubmitAtomStatusAction.class)
			.extracting(o -> SubmitAtomStatusAction.class.cast(o).getStatusNotification().getAtomStatus())
			.isIn(finalStatesSet);
	}

	private void printSubmitAtomAction(SubmitAtomAction saa) {
		System.out.println(saa);
		if (saa instanceof SubmitAtomStatusAction) {
			SubmitAtomStatusAction sara = (SubmitAtomStatusAction) saa;
			System.out.println(sara.getStatusNotification().getAtomStatus());
			System.out.println(sara.getStatusNotification().getData());
		}
	}
}
