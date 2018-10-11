package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.particles.DataParticle;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.serialization.Dson;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.observables.ConnectableObservable;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidAtomFilter {
	private static final Logger LOGGER = LoggerFactory.getLogger(ValidAtomFilter.class);

	private final RadixAddress address;
	private final Dson serializer;
	private final ConcurrentHashMap<ByteBuffer, Particle> upParticles = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<ByteBuffer, Atom> missingUpParticles = new ConcurrentHashMap<>();

	public ValidAtomFilter(RadixAddress address, Dson serializer) {
		this.address = address;
		this.serializer = serializer;
	}

	private void addAtom(Atom atom, ObservableEmitter<Atom> emitter) {
		atom.particles(Spin.DOWN)
			.filter(d -> d.getAddresses().stream().allMatch(address::ownsKey))
			.forEach(down -> {
				ByteBuffer dson = ByteBuffer.wrap(serializer.toDson(down));
				Particle up = upParticles.remove(dson);
				if (up == null) {
					throw new IllegalStateException("Missing UP particle for " + down);
				}
			});

		atom.particles(Spin.UP)
			.filter(up -> !up.getAddresses().isEmpty() && !(up instanceof DataParticle) // FIXME: remove hardcode of DataParticle
				&& up.getAddresses().stream().allMatch(address::ownsKey))
			.forEach(up -> {
				ByteBuffer dson = ByteBuffer.wrap(serializer.toDson(up));
				upParticles.compute(dson, (thisHash, current) -> {
					if (current == null) {
						return up;
					} else {
						throw new IllegalStateException("UP particle already exists: " + up);
					}
				});

				Atom reanalyzeAtom = missingUpParticles.remove(dson);
				if (reanalyzeAtom != null) {
					checkDownParticles(reanalyzeAtom, emitter);
				}
			});
	}

	private void checkDownParticles(Atom atom, ObservableEmitter<Atom> emitter) {
		Optional<ByteBuffer> missingUp = atom.particles(Spin.DOWN)
			.filter(p -> p.getAddresses().stream().allMatch(address::ownsKey))
			.map(serializer::toDson)
			.map(ByteBuffer::wrap)
			.filter(dson -> !upParticles.containsKey(dson))
			.findFirst();

		if (missingUp.isPresent()) {
			LOGGER.info("Missing up particle for atom: " + atom);

			missingUpParticles.compute(missingUp.get(), (thisHash, current) -> {
				if (current == null) {
					return atom;
				} else {
					throw new IllegalStateException();
				}
			});
		} else {
			emitter.onNext(atom);
			addAtom(atom, emitter);
		}
	}

	public Observable<Atom> filter(Atom atom) {
		ConnectableObservable<Atom> observable =
			Observable.<Atom>create(emitter -> {
				synchronized (ValidAtomFilter.this) {
					checkDownParticles(atom, emitter);
				}
				emitter.onComplete();
			}).replay();

		observable.connect();

		return observable;
	}
}
