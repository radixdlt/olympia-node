package org.radix.api.services;

import com.radixdlt.tempo.AtomSyncView;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Bytes;

import java.util.List;
import java.util.Map;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.json.JSONObject;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atomos.RRIParticle;
import org.radix.atoms.Atom;
import org.radix.atoms.AtomStore;
import org.radix.atoms.messages.AtomSubmitMessage;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.atomos.RRI;
import com.radixdlt.constraintmachine.Spin;
import org.radix.atoms.particles.conflict.ParticleConflictHandler;
import org.radix.atoms.sync.AtomSyncStore;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.CryptoException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.messaging.MessageCentral;
import org.radix.properties.RuntimeProperties;
import org.radix.shards.ShardChecksumStore;

import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import org.radix.time.Time;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.SystemProfiler;

/**
 * API which is used for internal testing and should not be included in release to users
 */
public class InternalService {
	private static final Logger log = Logging.getLogger();

	private static final InternalService INTERNAL_SERVICE = new InternalService();

	public static InternalService getInstance() {
		return INTERNAL_SERVICE;
	}

	private static boolean spamming = false;

	// Executor for prepare/store
	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	private InternalService() {}

	private static class Spammer implements Runnable {
		private final int nonceBits = Modules.get(RuntimeProperties.class).get("test.nullatom.junk_size", 40);

		private final ECKeyPair owner;
		private final RadixAddress account;
		private final int     iterations;
		private final int     batching;
		private final int     rate;

		private static final Random random = new Random();

		public Spammer(ECKeyPair owner, int iterations, int batching, int rate) {
			this.owner = owner;
			this.iterations = iterations;
			this.rate = rate;
			this.batching = Math.max(1, batching);
			this.account = RadixAddress.from(Modules.get(Universe.class), owner.getPublicKey());
		}

		private byte[] generateNonce(int randomBits) {
			if (randomBits > 0) {
				int byteCount = (randomBits + Byte.SIZE - 1) / Byte.SIZE;
				byte[] bytes = new byte[byteCount];
				synchronized (random) {
					random.nextBytes(bytes);
				}
				int extraBits = (byteCount * Byte.SIZE) - randomBits;
				if (extraBits != 0) {
					byte mask = (byte) ((1 << (Byte.SIZE - extraBits)) - 1);
					bytes[0] &= mask;
				}
				return bytes;
			}
			return Bytes.EMPTY_BYTES;
		}

		private long getRandomLong() {
			synchronized (random) {
				return random.nextLong();
			}
		}

		@Override
		public void run() {
			try {
				spamming = true;

				int remainingIterations = this.iterations;

				while (true) {
					try {
						long sliceStart = System.currentTimeMillis();

						for (int i = 0; i < this.rate; i++) {
							long start = SystemProfiler.getInstance().begin();

							try {
								Atom atom = new Atom(Time.currentTimestamp(), ImmutableMap.of("magic", "0xdeadbeef"));

								for (int b = 0; b < this.batching; b++) {
									byte[] nonce = generateNonce(nonceBits);
									String rriName = nonce != null ? Bytes.toHexString(nonce) : "hi";
									RRI rri = RRI.of(this.account, rriName);
									RRIParticle rriParticle = new RRIParticle(rri);
									UniqueParticle unique = new UniqueParticle(rriName, this.account, getRandomLong());
									atom.addParticleGroupWith(rriParticle, Spin.DOWN, unique, Spin.UP);
								}

								atom.sign(this.owner);

								if (LocalSystem.getInstance().getShards().intersects(atom.getShards()) == true) {
									Modules.get(AtomSyncView.class).inject(atom);
								} else {
									List<Peer> peers = Modules.get(AddressBook.class).recentPeers().collect(Collectors.toList());
									for (Peer peer : peers) {
										if (!peer.getSystem().getNID().equals(LocalSystem.getInstance().getNID()) && peer.getSystem().getShards().intersects(atom.getShards())) {
//											if (!org.radix.universe.System.getInstance().isSynced(peer.getSystem())) // TODO put this back in
//												continue;

											Modules.get(MessageCentral.class).send(peer, new AtomSubmitMessage(atom));
											break;
										}
									}
								}
							} finally {
								SystemProfiler.getInstance().incrementFrom("SPAMATHON:SUBMIT", start);
							}

							remainingIterations--;
							if (remainingIterations <= 0) {
								return;
							}

							if (i % this.rate == this.rate - 1) {
								if (1000 - (System.currentTimeMillis() - sliceStart) > 0) {
									Thread.sleep(1000 - (System.currentTimeMillis() - sliceStart));
								}

								sliceStart = System.currentTimeMillis();
							}
						}
					} catch (Exception ex) {
						log.error(ex);
						try {
							Thread.sleep(1000);
						} catch (InterruptedException nil) {
							// Exit if we are interrupted
							Thread.currentThread().interrupt();
							break;
						}
					}
				}
			} finally {
				spamming = false;
			}
		}
	}

	public JSONObject spamathon(JSONObject params) {
		return this.spamathon(params.getString("iterations"), params.optString("batching"), params.getString("rate"));
	}

	public JSONObject spamathon(String iterations, String batching, String rate) {
		JSONObject result = new JSONObject();

		if (spamming && Modules.get(RuntimeProperties.class).get("atoms.spam.multiple", false)) {
			throw new RuntimeException();
		}
		if (iterations == null) {
			throw new RuntimeException("Iterations not supplied");
		}
		if (Integer.decode(iterations) < 1) {
			throw new RuntimeException("Iterations is invalid");
		}
		if (rate == null) {
			throw new RuntimeException("Rate not supplied");
		}
		if (Integer.decode(rate) < 1) {
			throw new RuntimeException("Rate is invalid");
		}
		if (Integer.decode(rate) > Modules.get(RuntimeProperties.class).get("atoms.spam.max_rate", 1000)) {
			Integer maxRate = Modules.get(RuntimeProperties.class).get("atoms.spam.max_rate", 1000);
			throw new RuntimeException("Rate is too high - Maximum rate is " + maxRate);
		}

		try {
			Thread spammerThread = new Thread(new Spammer(new ECKeyPair(), Integer.decode(iterations), batching == null ? 1 : Integer.decode(batching), Integer.decode(rate)));
			spammerThread.setDaemon(true);
			spammerThread.setName("Spammer " + System.currentTimeMillis());
			spammerThread.start();

			result.put("data", "OK");
		} catch (CryptoException e) {
			result.put("error", e.getMessage());
		}

		return result;
	}

	public JSONObject prepareMessages(String strAtomCount) {
		JSONObject result = new JSONObject();

		if (strAtomCount == null) {
			throw new IllegalArgumentException("Atom count not supplied");
		}
		int atomCount = Integer.decode(strAtomCount);
		if (atomCount < 1) {
			throw new IllegalArgumentException("Atom count invalid: " + atomCount);
		}

		try {
			ECKeyPair sourceKey = new ECKeyPair();
			RadixAddress destination = RadixAddress.from(Modules.get(Universe.class), new ECKeyPair().getPublicKey());
			executor.execute(() -> Loader.getInstance().prepareMessages(sourceKey, destination, atomCount));
			result.put("data", "OK");
		} catch (CryptoException e) {
			result.put("error", e.getMessage());
		}

		return result;
	}

	public JSONObject prepareTransfers(String strAtomCount) {
		JSONObject result = new JSONObject();

		if (strAtomCount == null) {
			throw new IllegalArgumentException("Atom count not supplied");
		}
		int atomCount = Integer.decode(strAtomCount);
		if (atomCount < 1) {
			throw new IllegalArgumentException("Atom count invalid: " + atomCount);
		}

		try {
			ECKeyPair sourceKey = new ECKeyPair();
			RadixAddress destination = RadixAddress.from(Modules.get(Universe.class), new ECKeyPair().getPublicKey());
			executor.execute(() -> Loader.getInstance().prepareTransfers(sourceKey, destination, atomCount));
			result.put("data", "OK");
		} catch (CryptoException e) {
			result.put("error", e.getMessage());
		}

		return result;
	}

	public JSONObject bulkstore() {
		JSONObject result = new JSONObject();

		executor.execute(Loader.getInstance()::store);
		result.put("data", "OK");

		return result;
	}

	public JSONObject ping() {
		JSONObject result = new JSONObject();

		Loader.ping();
		result.put("data", "pong");

		return result;
	}

	public JSONObject dumpAtoms(boolean verbose) {
		JSONObject result = new JSONObject();

		try
		{
			Modules.get(AtomStore.class).dumpAtoms(verbose);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}

		result.put("data", "OK");

		return result;
	}

	public JSONObject dumpShardChunks() {
		JSONObject result = new JSONObject();

		try
		{
			Modules.get(ShardChecksumStore.class).dumpShardChunkChecksums();
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}

		result.put("data", "OK");

		return result;
	}

	public JSONObject dumpSyncBlocks() {
		JSONObject result = new JSONObject();

		try
		{
			Modules.get(AtomSyncStore.class).dumpSyncBlocks();
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}

		result.put("data", "OK");

		return result;
	}


	public JSONObject ledgerMetaData() {
		JSONObject result = new JSONObject();

		try
		{
			Map<String, Object> atomSyncMetaData = Modules.get(AtomSyncView.class).getMetaData();
			Map<String, Object> particleConflictHandlerMetaData = Modules.get(ParticleConflictHandler.class).getMetaData();

			result.put("atom_sync", Modules.get(Serialization.class).toJsonObject(atomSyncMetaData, Output.ALL));
			result.put("particle_conflicts", Modules.get(Serialization.class).toJsonObject(particleConflictHandlerMetaData, Output.ALL));
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}

		return result;
	}
}
