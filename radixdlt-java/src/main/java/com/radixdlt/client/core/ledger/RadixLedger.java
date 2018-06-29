package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomValidationException;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.network.AtomQuery;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.core.network.IncreasingRetryTimer;
import com.radixdlt.client.core.network.RadixNetwork;
import com.radixdlt.client.core.serialization.RadixJson;
import io.reactivex.Observable;
import io.reactivex.functions.Predicate;
import io.reactivex.observables.ConnectableObservable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RadixLedger wraps a RadixNetwork and abstracts away network logic providing
 * the main interface to interacting with a Radix Ledger, specifically reading
 * and writing atoms onto the Ledger.
 */
public class RadixLedger {
	private static final Logger logger = LoggerFactory.getLogger(RadixLedger.class);

	private final RadixNetwork radixNetwork;
	private final int magic;
	private final AtomicBoolean debug = new AtomicBoolean(false);

	public RadixLedger(int magic, RadixNetwork radixNetwork) {
		this.magic = magic;
		this.radixNetwork = radixNetwork;
	}

	public void setDebug(boolean debug) {
		this.debug.set(debug);
	}

	public int getMagic() {
		return magic;
	}

	/**
	 * Returns the network this ledger is running on top of
	 *
	 * @return the network this ledger is running on top of
	 */
	public RadixNetwork getNetwork() {
		return radixNetwork;
	}


	/**
	 * Returns a new hot Observable Atom Query which will connect to the network
	 * to retrieve the requested atoms.
	 *
	 * @param destination destination (which determines shard) to query atoms for
	 * @param atomClass atom class type to filter for
	 * @return a new Observable Atom Query
	 */
	public <T extends Atom> io.reactivex.Observable<T> getAllAtoms(EUID destination, Class<T> atomClass) {
		Objects.requireNonNull(destination);
		Objects.requireNonNull(atomClass);

		final AtomQuery<T> atomQuery = new AtomQuery<>(destination, atomClass);
		return radixNetwork.getRadixClient(destination.getShard())
			//.doOnSubscribe(client -> logger.info("Looking for client to serve atoms at: " + destination))
			//.doOnSuccess(client -> logger.info("Found client to serve atoms: " + client.getLocation()))
			.flatMapObservable(client -> client.getAtoms(atomQuery))
			.doOnError(Throwable::printStackTrace)
			.retryWhen(new IncreasingRetryTimer())
			.filter(new Predicate<T>() {
				final Set<RadixHash> atomsSeen = new HashSet<>();

				@Override
				public boolean test(T t) {
					if (atomsSeen.contains(t.getHash())) {
						logger.warn("Atom Already Seen: destination({}) atom({})", destination, t);
						return false;
					}
					atomsSeen.add(t.getHash());

					return true;
				}
			})
			.filter(atom -> {
				try {
					RadixAtomValidator.getInstance().validate(atom);
					return true;
				} catch (AtomValidationException e) {
					// TODO: Stop stream and mark client as untrustable
					logger.error(e.toString());
					return false;
				}
			})
			.doOnSubscribe(atoms -> logger.info("Atom Query Subscribe: destination({}) class({})", destination, atomClass.getSimpleName()))
			.publish()
			.refCount()
		;
	}

	/**
	 * Immediately submits an atom into the ledger without waiting for subscription. The returned
	 * observable is a full replay of the status of the atom, from submission to acceptance by
	 * the network.
	 *
	 * @param atom atom to submit into the ledger
	 * @return Observable emitting status updates to submission
	 */
	public io.reactivex.Observable<AtomSubmissionUpdate> submitAtom(Atom atom) {
		io.reactivex.Observable<AtomSubmissionUpdate> status = radixNetwork.getRadixClient(atom.getRequiredFirstShard())
			//.doOnSubscribe(client -> logger.info("Looking for client to submit atom"))
			//.doOnSuccess(client -> logger.info("Found client to submit atom: " + client.getLocation()))
			.flatMapObservable(client -> client.submitAtom(atom))
			.doOnError(Throwable::printStackTrace)
			.retryWhen(new IncreasingRetryTimer());

		if (debug.get()) {
			try {
				RadixAtomValidator.getInstance().validate(atom);
			} catch (AtomValidationException e) {
				logger.error(e.toString());
			}

			return status.doOnNext(atomSubmissionUpdate -> {
				if (atomSubmissionUpdate.getState() == AtomSubmissionState.VALIDATION_ERROR) {
					logger.error(atomSubmissionUpdate.getMessage() + "\n" + RadixJson.getGson().toJson(atom));
				}
			});
		}

		ConnectableObservable<AtomSubmissionUpdate> replay = status.replay();
		replay.connect();

		return replay;
	}

	/**
	 * Attempt to cleanup resources
	 */
	public void close() {
		// TODO: unsubscribe from all atom observables
	}
}
