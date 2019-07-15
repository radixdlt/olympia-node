package org.radix.api.services;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.radixdlt.atommodel.message.MessageParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.atommodel.tokens.TokenPermission;
import org.radix.atoms.Atom;
import com.radixdlt.universe.Universe;
import org.radix.atoms.AtomStore;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atomos.RadixAddress;
import org.radix.atoms.events.AtomStoredEvent;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;
import org.radix.atoms.sync.AtomSync;
import org.radix.atoms.sync.AtomSync.AtomComplexity;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.common.AID;
import org.radix.database.exceptions.DatabaseException;
import org.radix.events.EventListener;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.time.Time;
import org.radix.universe.system.LocalSystem;
import com.radixdlt.utils.UInt256;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.radixdlt.atommodel.tokens.TokenDefinitionParticle.SUB_UNITS;

final class Loader {
	private static final Logger log = Logging.getLogger("loader");
	private static final byte[] message = "Test message 1 2 3".getBytes(StandardCharsets.UTF_8);

	private List<Atom> atoms = null;

	private static class Holder {
		private static final Loader INSTANCE = new Loader();
	}

	static Loader getInstance() {
		return Holder.INSTANCE;
	}

	private Loader() {
		// Nothing to do here
	}

	static void ping() {

	}

	synchronized void prepareTransfers(ECKeyPair sourceKey, RadixAddress destination, int atomCount) {
		if (atomCount < 1) {
			throw new IllegalArgumentException("atomCount must be > 0: " + atomCount);
		}
		// particles per atom for batching unallocated & mints
		final int windowSize = 125;
		if (atomCount % windowSize != 0) {
			throw new IllegalArgumentException("atomCount must be factor of window size " + windowSize + ": " + atomCount);
		}

		// Throw away existing, if any
		this.atoms = null;

		try {
			log.info(String.format("Creating %d atoms...", atomCount));
			long start = System.nanoTime();
			List<Atom> newAtoms = Lists.newArrayList();
			RadixAddress myRadixAddress = RadixAddress.from(Modules.get(Universe.class), sourceKey.getPublicKey());

			log.info("Preparing with window size " + windowSize + "...");
			Atom defineTokenAtom = new Atom(Time.currentTimestamp());
			TokenDefinitionParticle tokenDefinition = createTokenDefinition(myRadixAddress);
			UnallocatedTokensParticle rootUnallocatedTokens = createUnallocatedTokens(tokenDefinition, atomCount);
			RRIParticle rriParticle = new RRIParticle(tokenDefinition.getRRI());
			defineTokenAtom.addParticleGroupWith(
				tokenDefinition, Spin.UP,
				rootUnallocatedTokens, Spin.UP,
				rriParticle, Spin.DOWN
			);
			storeAndAwait(defineTokenAtom, sourceKey);

			int windowCount = atomCount / windowSize;
			List<UnallocatedTokensParticle> availableUnallocated = IntStream.range(0, windowCount)
				.mapToObj(i -> createUnallocatedTokens(tokenDefinition, windowSize))
				.collect(Collectors.toList());
			for (int i = 0; i < windowCount; i += windowSize) {
				ParticleGroup.ParticleGroupBuilder unallocatedGroupBuilder = ParticleGroup.builder();
				unallocatedGroupBuilder.addParticle(rootUnallocatedTokens, Spin.DOWN);

				int remainingUnallocatedAmount = (windowCount - i - windowSize) * windowSize;
				if (remainingUnallocatedAmount > 0) {
					UnallocatedTokensParticle remainingUnallocated = createUnallocatedTokens(tokenDefinition, remainingUnallocatedAmount);
					unallocatedGroupBuilder.addParticle(remainingUnallocated, Spin.UP);
					rootUnallocatedTokens = remainingUnallocated;
				}

				for (int y = i; (y - i) < windowSize && y < windowCount; y++) {
					unallocatedGroupBuilder.addParticle(availableUnallocated.get(y), Spin.UP);
				}
				Atom unallocatedAtom = new Atom(Time.currentTimestamp());
				unallocatedAtom.addParticleGroup(unallocatedGroupBuilder.build());
				storeAndAwait(unallocatedAtom, sourceKey);
			}

			List<Atom> mintAtoms = new ArrayList<>();
			List<TransferrableTokensParticle> availableTokens = IntStream.range(0, atomCount)
				.mapToObj(i -> createTransferrableTokens(myRadixAddress, tokenDefinition, 1))
				.collect(Collectors.toList());
			for (int i = 0; i < atomCount; i += windowSize) {
				int window = i / windowSize;
				Atom mintAtom = new Atom(Time.currentTimestamp());
				ParticleGroup.ParticleGroupBuilder mintGroupBuilder = ParticleGroup.builder();
				mintGroupBuilder.addParticle(availableUnallocated.get(window), Spin.DOWN);
				for (int y = 0; y < windowSize; y++) {
					mintGroupBuilder.addParticle(availableTokens.get(i + y), Spin.UP);
				}

				mintAtom.addParticleGroup(mintGroupBuilder.build());

				mintAtoms.add(mintAtom);
			}

			Set<AID> mintAtomHids = new HashSet<>();
			for (Atom mintAtom : mintAtoms) {
				mintAtom.sign(sourceKey);
				Modules.get(AtomSync.class).store(mintAtom);
				mintAtomHids.add(mintAtom.getAID());
			}
			new HashWaiter(mintAtomHids).awaitUninterruptibly();

			log.info("Building...");
			// build actual test atoms
			for (int i = 0; i < atomCount; ++i) {
				TransferrableTokensParticle transfer = createTransferrableTokens(destination, tokenDefinition, 1);
				ParticleGroup transferGroup = ParticleGroup.of(
					SpunParticle.up(transfer),
					SpunParticle.down(availableTokens.get(i))
				);

				Atom atom = new Atom(Time.currentTimestamp());
				atom.addParticleGroup(transferGroup);
				atom.sign(sourceKey);
				newAtoms.add(atom);
			}
			long duration = System.nanoTime() - start;
			double seconds = duration / 1E9;
			double secondsPerAtom = seconds / newAtoms.size();
			double throughput = 1.0 / secondsPerAtom;
			log.info(String.format("Created %s atoms, %.3f s, %.3f ms/atom, %.3f atoms/s",
				newAtoms.size(), seconds, secondsPerAtom * 1000.0, throughput));
			this.atoms = newAtoms;
		} catch (Exception ex) {
 			log.error("While creating", ex);
		}
	}

	private void storeAndAwait(Atom prepareAtom, ECKeyPair sourceKey) throws CryptoException, DatabaseException {
		prepareAtom.sign(sourceKey);
		Modules.get(AtomSync.class).store(prepareAtom);
		new HashWaiter(ImmutableSet.of(prepareAtom.getAID())).awaitUninterruptibly();
	}

	synchronized void prepareMessages(ECKeyPair sourceKey, RadixAddress destination, int atomCount) {
		if (atomCount < 1) {
			throw new IllegalArgumentException("atomCount must be > 0: " + atomCount);
		}
		RadixAddress source = RadixAddress.from(Modules.get(Universe.class), sourceKey.getPublicKey());
		// Throw away existing, if any
		this.atoms = null;

		try {
			log.info(String.format("Creating %d atoms...", atomCount));
			long start = System.nanoTime();
			List<Atom> newAtoms = Lists.newArrayList();
			for (int i = 0; i < atomCount; ++i) {
				MessageParticle mp = new MessageParticle(source, destination, message);
				ParticleGroup pg = ParticleGroup.of(SpunParticle.up(mp));
				Atom atom = new Atom(Time.currentTimestamp());
				atom.addParticleGroup(pg);
				atom.sign(sourceKey);
				newAtoms.add(atom);
			}
			long duration = System.nanoTime() - start;
			double seconds = duration / 1E9;
			double secondsPerAtom = seconds / newAtoms.size();
			double throughput = 1.0 / secondsPerAtom;
			log.info(String.format("Created %s atoms, %.3f s, %.3f ms/atom, %.3f atoms/s",
				newAtoms.size(), seconds, secondsPerAtom * 1000.0, throughput));
			this.atoms = newAtoms;
		} catch (Exception ex) {
			log.error("While creating", ex);
		}
	}

	synchronized void store() {
		if (this.atoms == null) {
			log.info("No atoms preloaded");
			return;
		}

		long stored = 0L;
		long skipped = 0L;
		long start = System.nanoTime();
		try {
			log.info(String.format("Storing %d atoms...", atoms.size()));
			Atom lastStored = null;
			for (Atom atom : this.atoms) {
				if (LocalSystem.getInstance().getShards().intersects(atom.getShards())) {
					for (boolean wasStored = false; !wasStored;) {
						try {
							Modules.get(AtomSync.class).store(atom);
							lastStored = atom;
							stored += 1;
							wasStored = true;
						} catch (IllegalStateException e) {
							if (e.getMessage().startsWith("Commit queue size")) {
								log.info("Commit queue too deep, backing off (" + e.getMessage() + ")");
								// Backoff and retry
                                while (Modules.get(AtomSync.class).committingQueueSize(AtomComplexity.ALL) > 20000) {
									TimeUnit.MILLISECONDS.sleep(500);
								}
								log.info("Commit queue part drained");
							} else {
								throw e;
							}
						}
					}
				} else {
					skipped += 1;
				}
			}
			if (lastStored != null) {
				HashWaiter hw = new HashWaiter(ImmutableSet.of(lastStored.getAID()));
				hw.await(stored, TimeUnit.SECONDS);
			}
		} catch (Exception ex) {
			log.error("While loading", ex);
		} finally {
			// Can't reuse these
			this.atoms = null;
			if (stored == 0) {
				log.info("No atoms stored");
			} else {
				long duration = System.nanoTime() - start;
				double seconds = duration / 1E9;
				double secondsPerAtom = seconds / stored;
				double throughput = 1.0 / secondsPerAtom;
				log.info(String.format("Stored %s atoms, skipped %s, %.3f s, %.3f ms/atom, %.3f atoms/s",
					stored, skipped, seconds, secondsPerAtom * 1000.0, throughput));
			}
		}
	}

	static final class HashWaiter implements EventListener<AtomStoredEvent> {
		private final Semaphore storedSem = new Semaphore(0);
		private final Set<AID> hids = ConcurrentHashMap.newKeySet();

		HashWaiter(Set<AID> hids) throws DatabaseException {
			this.hids.addAll(Objects.requireNonNull(hids));

			Events.getInstance().register(AtomStoredEvent.class, this);

			for (AID hid : hids) {
				if (Modules.get(AtomStore.class).hasAtom(hid)) {
					this.hids.remove(hid);
				}
			}

			if (this.hids.isEmpty()) {
				storedSem.release();
			}
		}

		void awaitUninterruptibly() {
			try {
				storedSem.acquireUninterruptibly();
			} finally {
				Events.getInstance().deregister(AtomStoredEvent.class, this);
			}
		}

		boolean await(long timeout, TimeUnit unit) throws InterruptedException {
			try {
				return storedSem.tryAcquire(timeout, unit);
			} finally {
				Events.getInstance().deregister(AtomStoredEvent.class, this);
			}
		}

		@Override
		public void process(AtomStoredEvent event) {
			this.hids.remove(event.getAtom().getAID());
			if (this.hids.isEmpty()) {
				this.storedSem.release();
			}
		}
	}

	public static UInt256 unitsToSubunits(long units) {
		if (units < 0) {
			throw new IllegalArgumentException("units must be >= 0: " + units);
		}
		// 10^18 is approximately 60 bits, so a positive long (63 bits) cannot overflow here
		return UInt256.from(units).multiply(SUB_UNITS);
	}

	private UnallocatedTokensParticle createUnallocatedTokens(TokenDefinitionParticle tokenDefinition, int amount) {
		return new UnallocatedTokensParticle(
			unitsToSubunits(amount),
			UInt256.ONE,
			tokenDefinition.getRRI(),
			tokenDefinition.getTokenPermissions()
		);
	}

	private TransferrableTokensParticle createTransferrableTokens(RadixAddress myRadixAddress, TokenDefinitionParticle tokenDefinition, int amount) {
		return new TransferrableTokensParticle(
			myRadixAddress,
			unitsToSubunits(amount),
			UInt256.ONE,
			tokenDefinition.getRRI(),
			System.currentTimeMillis() / 60000L + 60000L,
			tokenDefinition.getTokenPermissions()
		);
	}

	private TokenDefinitionParticle createTokenDefinition(RadixAddress myRadixAddress) {
		return new TokenDefinitionParticle(
			myRadixAddress,
			"FLO",
			"Cookie Token!",
			"Cookies!",
			UInt256.ONE,
			null,
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY,
				TokenTransition.BURN, TokenPermission.TOKEN_OWNER_ONLY
			)
		);
	}
}
