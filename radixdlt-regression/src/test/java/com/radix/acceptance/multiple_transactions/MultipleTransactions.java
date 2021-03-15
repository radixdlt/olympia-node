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

package com.radix.acceptance.multiple_transactions;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.LocalRadixIdentity;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.tokens.InsufficientFundsException;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.atom.Atom;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusEvent;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.radixdlt.client.core.atoms.AtomStatus.EVICTED_FAILED_CM_VERIFICATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * See <a href="https://radixdlt.atlassian.net/browse/RPNV1-356">RPNV1-356: Developer - Multiple atomic transfers</a>.
 */
public class MultipleTransactions {
	private static final BigDecimal INITIAL_AMOUNT = BigDecimal.valueOf(100);
	private static final BigDecimal SMALL_AMOUNT = BigDecimal.valueOf(5);
	private static final BigDecimal EXCEEDING_AMOUNT = INITIAL_AMOUNT.add(SMALL_AMOUNT);

	private final List<TestObserver<Object>> observers = Lists.newArrayList();

	private RadixApplicationAPI api;
	private RadixNode nodeConnection;

	private LocalRadixIdentity ownerIdentity;
	private LocalRadixIdentity source1;
	private LocalRadixIdentity source2;
	private LocalRadixIdentity source3;
	private LocalRadixIdentity destination1;
	private LocalRadixIdentity destination2;
	private LocalRadixIdentity destination3;

	private RadixAddress sourceAddress1;
	private RadixAddress sourceAddress2;
	private RadixAddress sourceAddress3;

	private RadixAddress destinationAddress1;
	private RadixAddress destinationAddress2;
	private RadixAddress destinationAddress3;

	private RRI tokenA;
	private RRI tokenB;

	@Given("^I have access to a suitable Radix network$")
	public void i_have_access_to_a_suitable_Radix_network() {
		ownerIdentity = RadixIdentities.createNew();
		api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), ownerIdentity);

		api.discoverNodes();
		nodeConnection = api.getNetworkState()
				.map(RadixNetworkState::getNodes)
				.filter(s -> !s.isEmpty())
				.map(s -> s.iterator().next())
				.firstOrError()
				.blockingGet();

		TokenUtilities.requestTokensFor(api);

		source1 = RadixIdentities.createNew();
		sourceAddress1 = api.getAddress(source1.getPublicKey());

		source2 = RadixIdentities.createNew();
		sourceAddress2 = api.getAddress(source2.getPublicKey());

		source3 = RadixIdentities.createNew();
		sourceAddress3 = api.getAddress(source3.getPublicKey());

		destination1 = RadixIdentities.createNew();
		destinationAddress1 = api.getAddress(destination1.getPublicKey());

		destination2 = RadixIdentities.createNew();
		destinationAddress2 = api.getAddress(destination2.getPublicKey());

		destination3 = RadixIdentities.createNew();
		destinationAddress3 = api.getAddress(destination3.getPublicKey());

		tokenA = RRI.of(api.getAddress(), "RPNV1A");
		api.createFixedSupplyToken(tokenA, "RPNV1A", "TEST", new BigDecimal(1000)).blockUntilComplete();

		api.sendTokens(tokenA, sourceAddress1, INITIAL_AMOUNT).blockUntilComplete();
		api.sendTokens(tokenA, sourceAddress2, INITIAL_AMOUNT).blockUntilComplete();

		tokenB = RRI.of(api.getAddress(), "RPNV1B");
		api.createFixedSupplyToken(tokenB, "RPNV1B", "TEST", new BigDecimal(1000)).blockUntilComplete();

		api.sendTokens(tokenB, sourceAddress3, INITIAL_AMOUNT).blockUntilComplete();
	}

	@When("^I submit atom which contains two transfers with the same token type "
			+ "from (\\d+) source address(?:es)? "
			+ "to (\\d+) destination address(?:es)?$")
	public void i_submit_atom_with_one_transaction(final int sources, final int destinations) {
		submitTransfersWithAmount(sources, destinations, SMALL_AMOUNT, SMALL_AMOUNT);
	}

	@When("^I submit atom which contains two transfers with the same token type "
			+ "from 2 source addresses to 2 destination addresses "
			+ "where one transfer exceeds amount of available funds$")
	public void i_submit_atom_with_two_transfers_where_one_transfer_is_too_large() {
		try {
			submitTransfersWithAmount(2, 2, SMALL_AMOUNT, EXCEEDING_AMOUNT);
			fail("Transfer with insufficient funds was unexpectedly accepted");
		} catch (InsufficientFundsException e) {
			deferErrorDelivery();
		}
	}

	private void deferErrorDelivery() {
		final var observer = new TestObserver<>();
		Single.just(createValidationError()).subscribe(observer);
		observers.add(observer);
	}

	private SubmitAtomStatusAction createValidationError() {
		return SubmitAtomStatusAction.fromStatusNotification(
			UUID.randomUUID().toString(),
			new Atom(List.of()).buildAtom(),
			nodeConnection,
			new AtomStatusEvent(EVICTED_FAILED_CM_VERIFICATION)
		);
	}

	@When("^I submit atom which contains two transfers with different token types RPNV1A and RPNV1B$")
	public void i_submit_atom_with_different_token_types() {
		final var observer = new TestObserver<>();
		final var transaction = api.createTransaction();
		final var from1 = sourceAddress1;
		final var from2 = sourceAddress3;
		final var to1 = destinationAddress1;
		final var to2 = destinationAddress3;

		transaction.stage(TransferTokensAction.create(tokenA, from1, to1, SMALL_AMOUNT));
		transaction.stage(TransferTokensAction.create(tokenB, from2, to2, SMALL_AMOUNT));

		final var initialAtom = transaction.buildAtom();
		var atom = ownerIdentity.syncAddSignature(initialAtom);
		atom = source1.syncAddSignature(atom);
		atom = source3.syncAddSignature(atom);

		System.out.println("Atom: " + atom);

		api.submitAtom(atom.buildAtom(), false, nodeConnection)
				.toObservable()
				.subscribe(observer);
		observers.add(observer);
	}

	@Then("^I can observe the atom being accepted$")
	public void i_can_observe_the_atom_being_accepted() {
		awaitAtomStatus(observers.size(), AtomStatus.STORED);
	}

	@Then("^I can observe the atom being rejected with a validation error$")
	public void i_can_observe_the_atom_being_rejected_with_a_validation_error() {
		awaitAtomStatus(observers.size(), EVICTED_FAILED_CM_VERIFICATION);
	}

	private void awaitAtomStatus(final int atomNumber, final AtomStatus... finalStates) {
		final var finalStatesSet = ImmutableSet.<AtomStatus>builder()
				.addAll(Arrays.asList(finalStates))
				.build();

		final var testObserver = observers.get(atomNumber - 1);
		testObserver.awaitTerminalEvent();
		testObserver.assertNoErrors();
		testObserver.assertNoTimeout();

		final var events = testObserver.values();

		events.forEach(System.out::println);

		assertThat(events).last()
				.isInstanceOf(SubmitAtomStatusAction.class)
				.extracting(o -> ((SubmitAtomStatusAction) o).getStatusNotification().getAtomStatus())
				.isIn(finalStatesSet);
	}

	private void submitTransfersWithAmount(
			final int sources,
			final int destinations,
			final BigDecimal transferAmount1,
			final BigDecimal transferAmount2
	) {
		final var observer = new TestObserver<>();
		final var transaction = api.createTransaction();
		final var from1 = sourceAddress1;
		final var from2 = sources == 1 ? sourceAddress1 : sourceAddress2;
		final var to1 = destinationAddress1;
		final var to2 = destinations == 1 ? destinationAddress1 : destinationAddress2;
		final var twoTransfers = !(from1.equals(from2) && to1.equals(to2));

		transaction.stage(TransferTokensAction.create(tokenA, from1, to1, transferAmount1));

		if (twoTransfers) {
			transaction.stage(TransferTokensAction.create(tokenA, from2, to2, transferAmount2));
		}

		final var initialAtom = transaction.buildAtom();
		var atom = ownerIdentity.syncAddSignature(initialAtom);
		atom = source1.syncAddSignature(atom);

		if (sources > 1) {
			atom = source2.syncAddSignature(atom);
		}

		System.out.println("Atom: " + atom);

		api.submitAtom(atom.buildAtom(), false, nodeConnection)
				.toObservable()
				.subscribe(observer);
		observers.add(observer);
	}
}
