package com.radix.acceptance.token_fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Transaction;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.data.SendMessageAction;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.reactivex.observers.TestObserver;

public class TokenFees {

	private RadixApplicationAPI api;
	private final RadixIdentity identity = RadixIdentities.createNew();
	private final TestObserver<Object> observer = TestObserver.create();

	@Given("^I have a connection to a Radix network,$")
	public void i_have_a_connection_to_a_radix_network() {
		this.api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), this.identity);
		TokenUtilities.requestTokensFor(this.api);
	}

	@When("^I submit an atom without any fees,$")
	public void i_submit_an_atom_without_any_fees() {
		RadixAddress address = this.api.getAddress();
		Transaction t = this.api.createTransaction();
		t.stage(SendMessageAction.create(address, address, "Test message".getBytes(StandardCharsets.UTF_8), true));
		t.commitAndPushWithFee(BigDecimal.ZERO)
			.toObservable()
			.doOnNext(this::printSubmitAtomAction)
			.subscribe(this.observer);
	}

	@Then("^I can see that atom being rejected by the network$")
	public void i_can_observe_the_atom_being_rejected_by_the_network() {
		awaitAtomStatus(AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
	}

	@When("^I submit an atom with a fee that is too small,$")
	public void i_submit_an_atom_with_a_fee_that_is_too_small() {
		RadixAddress address = this.api.getAddress();
		Transaction t = this.api.createTransaction();
		t.stage(SendMessageAction.create(address, address, "Test message".getBytes(StandardCharsets.UTF_8), true));
		// Minimum fee assumed to be 40 millirads.  We will try to pay 39 millirads.
		t.commitAndPushWithFee(BigDecimal.valueOf(39, 3)) // 39 x 10^{-3} rads
			.toObservable()
			.doOnNext(this::printSubmitAtomAction)
			.subscribe(this.observer);
	}

	@When("^I submit an atom with a fee that equals the minimum fee,$")
	public void i_submit_an_atom_with_a_fee_that_equals_the_minimum_fee() {
		RadixAddress address = this.api.getAddress();
		Transaction t = this.api.createTransaction();
		t.stage(SendMessageAction.create(address, address, "Test message".getBytes(StandardCharsets.UTF_8), true));
		// Minimum fee assumed to be 40 millirads.
		t.commitAndPushWithFee(BigDecimal.valueOf(40, 3)) // 40 x 10^{-3} rads
			.toObservable()
			.doOnNext(this::printSubmitAtomAction)
			.subscribe(this.observer);
	}

	@Then("^I can see that atom being accepted by the network$")
	public void i_can_observe_the_atom_being_accepted_by_the_network() {
		awaitAtomStatus(AtomStatus.STORED);
	}

	@When("^I submit an atom with a fee that exceeds the minimum fee,$")
	public void i_submit_an_atom_with_a_fee_that_exceeds_the_minimum_fee() {
		RadixAddress address = this.api.getAddress();
		Transaction t = this.api.createTransaction();
		t.stage(SendMessageAction.create(address, address, "Test message".getBytes(StandardCharsets.UTF_8), true));
		// Minimum fee assumed to be 40 millirads.
		t.commitAndPushWithFee(BigDecimal.valueOf(80, 3)) // 80 x 10^{-3} rads
			.toObservable()
			.doOnNext(this::printSubmitAtomAction)
			.subscribe(this.observer);
	}

	@When("^I create an atom with a size smaller than 3072 bytes,$")
	public void i_create_an_atom_with_a_size_smaller_than_3072_bytes() {
		RadixAddress address = this.api.getAddress();
		Transaction t = this.api.createTransaction();
		t.stage(SendMessageAction.create(address, address, "Test message".getBytes(StandardCharsets.UTF_8), true));
		t.commitAndPush() // Fee is computed for us
			.toObservable()
			.doOnNext(this::printSubmitAtomAction)
			.subscribe(this.observer);
	}

	@And("^I submit that atom to the network with the computed minimum fee,$")
	public void i_submit_that_atom_to_the_network_with_the_computed_minimum_fee() {
		// Note that submitting happens in previous clause, so nothing to do here.
	}

	@Then("^I can see that the fee is 40 millirads$")
	public void i_can_see_that_the_fee_is_40_millirads() {
		assertEquals(0, atomFee().compareTo(BigDecimal.valueOf(40, 3)));
	}

	@When("^I create an atom with a size larger than 3072 bytes,$")
	public void i_create_an_atom_with_a_size_larger_than_3072_bytes() {
		RadixAddress address = this.api.getAddress();
		Transaction t = this.api.createTransaction();
		String bigMessage = Strings.repeat("X", 3072); // message size + other data will be greater than 3072 bytes
		t.stage(SendMessageAction.create(address, address, bigMessage.getBytes(StandardCharsets.UTF_8), true));
		t.commitAndPush() // Fee is computed for us
			.toObservable()
			.doOnNext(this::printSubmitAtomAction)
			.subscribe(this.observer);
	}

	@Then("^I can see that the fee is greater than 40 millirads$")
	public void i_can_see_that_the_fee_is_greater_than_40_millirads() {
		assertTrue(atomFee().compareTo(BigDecimal.valueOf(40, 3)) > 0);
	}

	@When("^I create an atom that creates a mutable supply token,$")
	public void i_create_an_atom_that_creates_a_mutable_supply_token() {
		this.api.createMultiIssuanceToken(
				RRI.of(this.api.getAddress(), "TEST"),
				"Test token name",
				"Test token description"
			).toObservable()
			.doOnNext(this::printSubmitAtomAction)
			.subscribe(this.observer);
	}

	@Then("^I can see that the fee is at least 1 rad$")
	public void i_can_see_that_the_fee_is_at_least_rad() {
		assertTrue(atomFee().compareTo(BigDecimal.valueOf(1)) >= 0);
	}

	@When("^I create an atom that creates a fixed supply token,$")
	public void i_create_an_atom_that_creates_a_fixed_supply_token() {
		this.api.createFixedSupplyToken(
				RRI.of(this.api.getAddress(), "TEST"),
				"Test token name",
				"Test token description",
				BigDecimal.valueOf(100)
			).toObservable()
			.doOnNext(this::printSubmitAtomAction)
			.subscribe(this.observer);
	}

	private BigDecimal atomFee() {
		awaitAtomStatus(AtomStatus.STORED);
		List<Object> events = this.observer.values();
		SubmitAtomStatusAction sasa = (SubmitAtomStatusAction) events.get(events.size() - 1); // Checked in awaitAtomStatus
		return feeFrom(sasa.getAtom());
	}

	private void awaitAtomStatus(AtomStatus... finalStates) {
		ImmutableSet<AtomStatus> finalStatesSet = ImmutableSet.copyOf(finalStates);

		this.observer.awaitTerminalEvent();
		this.observer.assertNoErrors();
		this.observer.assertNoTimeout();
		List<Object> events = this.observer.values();
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
			SubmitAtomStatusAction sasa = (SubmitAtomStatusAction) saa;
			System.out.println(sasa.getStatusNotification().getAtomStatus());
			System.out.println(sasa.getStatusNotification().getData());
		}
	}

	// Fee computation
	private BigDecimal feeFrom(Atom atom) {
		UInt256 totalFee = atom.particleGroups()
			.filter(this::isFeeGroup)
			.flatMap(pg -> pg.particles(Spin.UP))
			.filter(UnallocatedTokensParticle.class::isInstance)
			.map(UnallocatedTokensParticle.class::cast)
			.map(UnallocatedTokensParticle::getAmount)
			.reduce(UInt256.ZERO, UInt256::add);

		return TokenUnitConversions.subunitsToUnits(totalFee);
	}

	private boolean isFeeGroup(ParticleGroup pg) {
		// No free storage in metadata
		if (!pg.getMetaData().isEmpty()) {
			return false;
		}
		Map<Class<? extends Particle>, List<SpunParticle>> grouping = pg.spunParticles()
			.collect(Collectors.groupingBy(sp -> sp.getParticle().getClass()));
		List<SpunParticle> spunTransferableTokens = grouping.remove(TransferrableTokensParticle.class);
		List<SpunParticle> spunUnallocatedTokens = grouping.remove(UnallocatedTokensParticle.class);
		// If there is other "stuff" in the group, or no "burns", then it's not a fee group
		if (grouping.isEmpty() && spunUnallocatedTokens != null) {
			ImmutableList<TransferrableTokensParticle> transferableTokens = spunTransferableTokens == null
				? ImmutableList.of()
				: spunTransferableTokens.stream()
					.map(SpunParticle::getParticle)
					.map(p -> (TransferrableTokensParticle) p)
					.collect(ImmutableList.toImmutableList());
			return allUpForFeeToken(spunUnallocatedTokens) && allSameAddressAndForFeeToken(transferableTokens);
		}
		return false;
	}

	// Check that all transferable particles are in for the same address and for the fee token
	private boolean allSameAddressAndForFeeToken(ImmutableList<TransferrableTokensParticle> transferableTokens) {
		if (transferableTokens.isEmpty()) {
			return true;
		}
		RRI feeTokenRri = this.api.getNativeTokenRef();
		RadixAddress addr = transferableTokens.get(0).getAddress();
		return transferableTokens.stream()
			.allMatch(ttp -> ttp.getAddress().equals(addr) && feeTokenRri.equals(ttp.getTokenDefinitionReference()));
	}

	// Check that all unallocated particles are in the up state and for the fee token
	private boolean allUpForFeeToken(List<SpunParticle> spunUnallocatedTokens) {
		return spunUnallocatedTokens.stream()
			.allMatch(this::isUpAndForFee);
	}

	private boolean isUpAndForFee(SpunParticle sp) {
		if (sp.getSpin() == Spin.UP) {
			UnallocatedTokensParticle utp = (UnallocatedTokensParticle) sp.getParticle();
			RRI feeTokenRri = this.api.getNativeTokenRef();
			return feeTokenRri.equals(utp.getTokDefRef());
		}
		return false;
	}
}
