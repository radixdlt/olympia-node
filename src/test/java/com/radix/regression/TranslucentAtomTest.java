package com.radix.regression;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.identity.LocalRadixIdentity;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.message.MessageParticle.MessageParticleBuilder;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType;
import io.reactivex.observers.TestObserver;
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
		this.api = RadixApplicationAPI.create(Bootstrap.LOCALHOST_SINGLENODE, identity);
	}

	@Test
	public void testWrongDestination() {
		MessageParticle messageParticle = new MessageParticleBuilder()
			.from(api.getMyAddress())
			.to(api.getMyAddress())
			.payload(new byte[1])
			.nonce(0)
			.build();
		messageParticle.getDestinations().removeIf(p -> true);
		messageParticle.getDestinations().add(EUID.ONE);
		UnsignedAtom unsignedAtom = new UnsignedAtom(new Atom(ParticleGroup.of(SpunParticle.up(messageParticle)), System.currentTimeMillis()));
		Atom signedAtom = this.identity.syncSign(unsignedAtom);
		Result result = api.submitAtom(signedAtom);
		TestObserver<SubmitAtomAction> testObserver = TestObserver.create(Util.loggingObserver("SubmitAtom"));
		result.toObservable()
			.subscribe(testObserver);
		testObserver.awaitTerminalEvent(5, TimeUnit.SECONDS);
		testObserver.assertNoErrors();
		testObserver.assertComplete();
		testObserver.assertValueAt(3, action -> {
			SubmitAtomResultAction res = (SubmitAtomResultAction) action;
			return res.getType().equals(SubmitAtomResultActionType.VALIDATION_ERROR);
		});
	}

	@Test
	public void testExtraDestination() {
		MessageParticle messageParticle = new MessageParticleBuilder()
			.from(api.getMyAddress())
			.to(api.getMyAddress())
			.payload(new byte[1])
			.nonce(0)
			.build();
		messageParticle.getDestinations().add(EUID.ONE);
		UnsignedAtom unsignedAtom = new UnsignedAtom(new Atom(ParticleGroup.of(SpunParticle.up(messageParticle)), System.currentTimeMillis()));
		Atom signedAtom = this.identity.syncSign(unsignedAtom);
		Result result = api.submitAtom(signedAtom);
		TestObserver<SubmitAtomAction> testObserver = TestObserver.create(Util.loggingObserver("SubmitAtom"));
		result.toObservable()
			.subscribe(testObserver);
		testObserver.awaitTerminalEvent(5, TimeUnit.SECONDS);
		testObserver.assertNoErrors();
		testObserver.assertComplete();
		testObserver.assertValueAt(3, action -> {
			SubmitAtomResultAction res = (SubmitAtomResultAction) action;
			return res.getType().equals(SubmitAtomResultActionType.VALIDATION_ERROR);
		});
	}
}
