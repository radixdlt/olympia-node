package com.radixdlt.client.core.ledger;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.atommodel.message.MessageParticle;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.observables.ConnectableObservable;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.Serialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ValidAtomFilter {
	private static final Logger LOGGER = LoggerFactory.getLogger(ValidAtomFilter.class);

	private final RadixAddress address;
	private final Serialization serializer;
	private final ConcurrentHashMap<ByteBuffer, Particle> upParticles = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<ByteBuffer, AtomObservation> missingUpParticles = new ConcurrentHashMap<>();

	public ValidAtomFilter(RadixAddress address, Serialization serializer) {
		this.address = address;
		this.serializer = serializer;
	}

	private void addAtom(AtomObservation atomObservation, ObservableEmitter<AtomObservation> emitter) {
		Atom atom = atomObservation.getAtom();
		atom.particles(Spin.DOWN)
			.filter(d -> d.getAddresses().stream().allMatch(address::ownsKey))
			.forEach(down -> {
				ByteBuffer dson = ByteBuffer.wrap(serializer.toDson(down, Output.HASH));
				Particle up = upParticles.remove(dson);
				if (up == null) {
					throw new IllegalStateException("Missing UP particle for " + down);
				}
			});

		atom.particles(Spin.UP)
			.filter(up -> !up.getAddresses().isEmpty() && !(up instanceof MessageParticle) // FIXME: remove hardcode of DataParticle
				&& up.getAddresses().stream().allMatch(address::ownsKey))
			.forEach(up -> {
				ByteBuffer dson = ByteBuffer.wrap(serializer.toDson(up, Output.HASH));
				upParticles.compute(dson, (thisHash, current) -> {
					if (current == null) {
						return up;
					} else {
						throw new IllegalStateException("UP particle already exists: " + up);
					}
				});

				AtomObservation reanalyzeAtom = missingUpParticles.remove(dson);
				if (reanalyzeAtom != null) {
					checkDownParticles(reanalyzeAtom, emitter);
				}
			});
	}

	private void checkDownParticles(AtomObservation atomObservation, ObservableEmitter<AtomObservation> emitter) {
		Atom atom = atomObservation.getAtom();
		Optional<ByteBuffer> missingUp = atom.particles(Spin.DOWN)
			.filter(p -> p.getAddresses().stream().allMatch(address::ownsKey))
			.map(p -> serializer.toDson(p, Output.HASH))
			.map(ByteBuffer::wrap)
			.filter(dson -> !upParticles.containsKey(dson))
			.findFirst();

		if (missingUp.isPresent()) {
			LOGGER.info("Missing up particle for atom: " + atom);

			missingUpParticles.compute(missingUp.get(), (thisHash, current) -> {
				if (current == null) {
					return atomObservation;
				} else {
					throw new IllegalStateException();
				}
			});
		} else {
			emitter.onNext(atomObservation);
			addAtom(atomObservation, emitter);
		}
	}

	public Observable<AtomObservation> filter(AtomObservation atomObservation) {
		ConnectableObservable<AtomObservation> observable =
			Observable.<AtomObservation>create(emitter -> {
				synchronized (ValidAtomFilter.this) {
					checkDownParticles(atomObservation, emitter);
				}
				emitter.onComplete();
			}).replay();

		observable.connect();

		return observable;
	}
}
