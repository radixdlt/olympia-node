package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomValidationException;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.network.AtomQuery;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.core.network.IncreasingRetryTimer;
import com.radixdlt.client.core.network.RadixJsonRpcClient;
import com.radixdlt.client.core.network.RadixNetwork;
import com.radixdlt.client.core.network.WebSocketClient.RadixClientStatus;
import com.radixdlt.client.core.serialization.RadixJson;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.Predicate;
import io.reactivex.observables.ConnectableObservable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RadixLedger wraps a RadixNetwork and abstracts away network logic providing
 * the main interface to interacting with a Radix Ledger, specifically reading
 * and writing atoms onto the Ledger.
 */
public class RadixLedger {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixLedger.class);

	private final RadixNetwork radixNetwork;
	private final AtomicBoolean debug = new AtomicBoolean(false);

	/**
	 * The Universe we need peers for
	 * TODO: is this the right place to have this?
	 */
	private final RadixUniverseConfig config;

	public RadixLedger(RadixUniverseConfig config, RadixNetwork radixNetwork) {
		this.config = config;
		this.radixNetwork = radixNetwork;
	}

	public void setDebug(boolean debug) {
		this.debug.set(debug);
	}

	public int getMagic() {
		return config.getMagic();
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
	 * Maps a public key to it's corresponding Radix address in this universe.
	 * Within a universe, a public key has a one to one bijective relationship to an address
	 *
	 * @param publicKey the key to get an address from
	 * @return the corresponding address to the key for this universe
	 */
	public RadixAddress getAddressFromPublicKey(ECPublicKey publicKey) {
		return new RadixAddress(config.getMagic(), publicKey);
	}

	/**
	 * Returns a cold observable of the first peer found which supports
	 * a set short shards which intersects with a given set of shards.
	 *
	 * @param shards set of shards to find an intersection with
	 * @return a cold observable of the first matching Radix client
	 */
	private Single<RadixJsonRpcClient> getRadixClient(Set<Long> shards) {
		return this.radixNetwork.getRadixClients(shards)
			.flatMapMaybe(client ->
				client.getStatus()
					.filter(status -> !status.equals(RadixClientStatus.FAILURE))
					.map(status -> client)
					.firstOrError()
					.toMaybe()
			)
			.flatMapMaybe(client ->
				client.getUniverse()
					.doOnSuccess(cliUniverse -> {
						if (!config.equals(cliUniverse)) {
							LOGGER.warn("{} has universe: {} but looking for {}",
								client, cliUniverse.getHash(), config.getHash());
						}
					})
					.map(config::equals)
					.filter(b -> b)
					.map(b -> client)
			)
			.firstOrError();
	}

	/**
	 * Returns a cold observable of the first peer found which supports
	 * a set short shards which intersects with a given shard
	 *
	 * @param shard a shards to find an intersection with
	 * @return a cold observable of the first matching Radix client
	 */
	private Single<RadixJsonRpcClient> getRadixClient(Long shard) {
		return getRadixClient(Collections.singleton(shard));
	}

	/**
	 * Returns a new hot Observable Atom Query which will connect to the network
	 * to retrieve the requested atoms.
	 *
	 * @param destination destination (which determines shard) to query atoms for
	 * @return a new Observable Atom Query
	 */
	public Observable<Atom> getAllAtoms(EUID destination) {
		Objects.requireNonNull(destination);

		final AtomQuery<Atom> atomQuery = new AtomQuery<>(destination, Atom.class);
		return getRadixClient(destination.getShard())
			.flatMapObservable(client -> client.getAtoms(atomQuery))
			.doOnError(throwable -> {
				LOGGER.warn("Error on getAllAtoms: {}", destination);
			})
			.retryWhen(new IncreasingRetryTimer())
			.filter(new Predicate<Atom>() {
				private final Set<RadixHash> atomsSeen = new HashSet<>();

				@Override
				public boolean test(Atom atom) {
					if (atomsSeen.contains(atom.getHash())) {
						LOGGER.warn("Atom Already Seen: destination({})", destination);
						return false;
					}
					atomsSeen.add(atom.getHash());

					return true;
				}
			})
			.filter(atom -> {
				try {
					RadixAtomValidator.getInstance().validate(atom);
					return true;
				} catch (AtomValidationException e) {
					// TODO: Stop stream and mark client as untrustable
					LOGGER.error(e.toString());
					return false;
				}
			})
			.doOnSubscribe(
				atoms -> LOGGER.info("Atom Query Subscribe: destination({})", destination)
			)
			.publish()
			.refCount();
	}

	/**
	 * Immediately submits an atom into the ledger without waiting for subscription. The returned
	 * observable is a full replay of the status of the atom, from submission to acceptance by
	 * the network.
	 *
	 * @param atom atom to submit into the ledger
	 * @return Observable emitting status updates to submission
	 */
	public Observable<AtomSubmissionUpdate> submitAtom(Atom atom) {
		Observable<AtomSubmissionUpdate> status = getRadixClient(atom.getRequiredFirstShard())
			.doOnSuccess(client -> LOGGER.info("Found client to submit atom: {}", client.getLocation()))
			.doOnError(throwable -> {
				LOGGER.warn("Error on submitAtom {}", atom.getHid());
				throwable.printStackTrace();
			})			.flatMapObservable(client -> client.submitAtom(atom))
			.doOnError(Throwable::printStackTrace)
			.retryWhen(new IncreasingRetryTimer());

		if (debug.get()) {
			try {
				RadixAtomValidator.getInstance().validate(atom);
			} catch (AtomValidationException e) {
				LOGGER.error(e.toString());
			}

			return status.doOnNext(atomSubmissionUpdate -> {
				if (atomSubmissionUpdate.getState() == AtomSubmissionState.VALIDATION_ERROR) {
					LOGGER.error("{}\n{}", atomSubmissionUpdate.getMessage(), RadixJson.getGson().toJson(atom));
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
