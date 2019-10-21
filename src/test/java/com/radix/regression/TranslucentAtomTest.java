package com.radix.regression;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.identity.LocalRadixIdentity;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.message.MetadataMap;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;

import static org.assertj.core.api.Assertions.assertThat;

import io.reactivex.observers.TestObserver;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.radix.common.ID.EUID;

public class TranslucentAtomTest {
	private RadixApplicationAPI api;
	private LocalRadixIdentity identity;

	@Before
	public void setUp() {
		this.identity = RadixIdentities.createNew();
		this.api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), identity);
	}

	@Test
	public void testWrongDestination() {
		MessageParticle messageParticle = new MessageParticle(
			api.getAddress(),
			api.getAddress(),
			new byte[1],
			new MetadataMap(),
			0, ImmutableSet.of(EUID.ONE));
		Atom unsignedAtom = Atom.create(ParticleGroup.of(SpunParticle.up(messageParticle)), System.currentTimeMillis());
		Atom signedAtom = this.identity.addSignature(unsignedAtom).blockingGet();
		Result result = api.submitAtom(signedAtom);
		TestObserver<SubmitAtomAction> testObserver = TestObserver.create(Util.loggingObserver("SubmitAtom"));
		result.toObservable()
			.subscribe(testObserver);
		testObserver.awaitTerminalEvent(5, TimeUnit.SECONDS);
		testObserver.assertNoErrors();
		testObserver.assertComplete();
		testObserver.assertNoTimeout();
		List<SubmitAtomAction> events = testObserver.values();
		assertThat(events).extracting(o -> o.getClass().toString())
			.startsWith(
				SubmitAtomRequestAction.class.toString(),
				SubmitAtomSendAction.class.toString()
			);
		assertThat(events).last()
			.isInstanceOf(SubmitAtomStatusAction.class)
			.extracting(o -> SubmitAtomStatusAction.class.cast(o).getStatusNotification().getAtomStatus())
			.isEqualTo(AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
	}

	@Test
	public void testExtraDestination() {
		MessageParticle messageParticle = new MessageParticle(
			api.getAddress(),
			api.getAddress(),
			new byte[1],
			new MetadataMap(),
			0, ImmutableSet.of(api.getAddress().getUID(), EUID.ONE));
		Atom unsignedAtom = Atom.create(ParticleGroup.of(SpunParticle.up(messageParticle)), System.currentTimeMillis());
		Atom signedAtom = this.identity.addSignature(unsignedAtom).blockingGet();
		Result result = api.submitAtom(signedAtom);
		TestObserver<SubmitAtomAction> testObserver = TestObserver.create(Util.loggingObserver("SubmitAtom"));
		result.toObservable()
			.subscribe(testObserver);
		testObserver.awaitTerminalEvent(30, TimeUnit.SECONDS);
		testObserver.assertNoErrors();
		testObserver.assertComplete();
		testObserver.assertNoTimeout();
		List<SubmitAtomAction> events = testObserver.values();
		assertThat(events).extracting(o -> o.getClass().toString())
			.startsWith(
				SubmitAtomRequestAction.class.toString(),
				SubmitAtomSendAction.class.toString()
			);
		assertThat(events).last()
			.isInstanceOf(SubmitAtomStatusAction.class)
			.extracting(o -> SubmitAtomStatusAction.class.cast(o).getStatusNotification().getAtomStatus())
			.isEqualTo(AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
	}
}
