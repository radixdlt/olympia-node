package org.radix.atoms.sync;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.middleware.AtomCheckHook;
import com.radixdlt.atomos.Result;
import com.radixdlt.engine.AtomStatus;
import com.radixdlt.middleware.ImmutableAtom;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.engine.RadixEngineAtom;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.middleware.RadixEngineUtils;
import com.radixdlt.middleware.RadixEngineUtils.CMAtomConversionException;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.radixdlt.tempo.AtomSyncView;
import org.radix.atoms.Atom;
import org.radix.atoms.AtomDependencyNotFoundException;
import org.radix.atoms.AtomDiscoveryRequest;
import org.radix.atoms.AtomStore;
import org.radix.atoms.PreparedAtom;
import org.radix.atoms.events.AtomDeletedEvent;
import org.radix.atoms.events.AtomEvent;
import org.radix.atoms.events.AtomExceptionEvent;
import org.radix.atoms.events.AtomListener;
import org.radix.atoms.events.AtomStoreEvent;
import org.radix.atoms.events.AtomStoredEvent;
import org.radix.atoms.events.AtomUpdatedEvent;
import org.radix.atoms.events.ParticleEvent;
import org.radix.atoms.events.ParticleListener;
import org.radix.atoms.messages.AtomSubmitMessage;
import org.radix.atoms.particles.conflict.ParticleConflict;
import org.radix.atoms.particles.conflict.ParticleConflictException;
import org.radix.atoms.particles.conflict.events.ConflictConcurrentEvent;
import org.radix.atoms.particles.conflict.events.ConflictDetectedEvent;
import org.radix.atoms.particles.conflict.events.ConflictEvent;
import org.radix.atoms.particles.conflict.events.ConflictFailedEvent;
import org.radix.atoms.particles.conflict.events.ConflictResolvedEvent;
import org.radix.atoms.particles.conflict.events.ConflictUpdatedEvent;
import org.radix.atoms.sync.messages.AtomBroadcastMessage;
import org.radix.atoms.sync.messages.AtomChecksumChunksDiscoveryRequestMessage;
import org.radix.atoms.sync.messages.AtomChecksumChunksDiscoveryResponseMessage;
import org.radix.atoms.sync.messages.AtomChecksumDiscoveryRequestMessage;
import org.radix.atoms.sync.messages.AtomChecksumDiscoveryResponseMessage;
import org.radix.atoms.sync.messages.AtomSyncDeliveryRequestMessage;
import org.radix.atoms.sync.messages.AtomSyncDeliveryResponseMessage;
import org.radix.atoms.sync.messages.AtomSyncInventoryRequestMessage;
import org.radix.atoms.sync.messages.AtomSyncInventoryResponseMessage;
import org.radix.collections.SetBlockingQueue;
import com.radixdlt.common.AID;
import org.radix.common.Syncronicity;
import com.radixdlt.common.EUID;
import com.radixdlt.utils.Offset;

import org.radix.common.executors.Executable;
import org.radix.common.executors.Executor;
import org.radix.common.executors.ScheduledExecutable;
import com.radixdlt.common.Pair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;

import org.radix.database.exceptions.DatabaseException;
import org.radix.database.exceptions.KeyExistsDatabaseException;
import org.radix.discovery.DiscoveryCursor;
import org.radix.discovery.DiscoveryRequest.Action;
import org.radix.events.Event.EventPriority;
import org.radix.events.Events;
import org.radix.exceptions.AtomAlreadyInProcessingException;
import org.radix.exceptions.AtomAlreadyStoredException;
import org.radix.exceptions.ExceptionEvent;
import org.radix.exceptions.ExceptionListener;
import org.radix.exceptions.ValidationException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.modules.Service;
import org.radix.modules.exceptions.ModuleException;
import org.radix.network.Network;
import org.radix.network.Protocol;
import org.radix.network.discovery.SyncDiscovery;
import org.radix.network.messaging.MessageProcessor;
import org.radix.network.messaging.Messaging;
import org.radix.network.peers.Peer;
import org.radix.network.peers.PeerHandler;
import org.radix.network.peers.PeerListener;
import org.radix.network.peers.PeerTask;
import org.radix.network.peers.PeerHandler.PeerDomain;
import org.radix.network.peers.events.PeerDisconnectedEvent;
import org.radix.network.peers.events.PeerEvent;
import org.radix.network.peers.filters.PeerFilter;
import org.radix.network.peers.filters.TCPPeerFilter;
import org.radix.properties.RuntimeProperties;
import org.radix.routing.NodeAddressGroupTable;

import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.MapHelper;
import com.radixdlt.serialization.Serialization;

import org.radix.shards.ShardChecksumStore;
import org.radix.shards.ShardSpace;
import org.radix.state.State;
import org.radix.state.StateDomain;
import org.radix.time.NtpService;
import org.radix.time.TemporalProof;
import org.radix.time.TemporalProofNotValidException;
import org.radix.time.TemporalProofValidator;
import org.radix.time.TemporalVertex;
import org.radix.time.Time;
import com.radixdlt.universe.Universe;
import org.radix.universe.system.LocalSystem;
import org.radix.universe.system.events.QueueFullEvent;
import org.radix.utils.BArray;
import org.radix.utils.ExceptionUtils;
import org.radix.utils.MathUtils;
import org.radix.utils.SystemProfiler;
import org.radix.validation.ConstraintMachineValidationException;
import org.radix.validation.ValidationHandler;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

public class AtomSync extends Service
{
	public static enum AtomComplexity
	{
		ALL, NONCOMPLEX, COMPLEX
	}

	private static final Logger log = Logging.getLogger();
	private static final Logger discoveryLog = Logging.getLogger("discovery");
	private static final Logger atomsLog = Logging.getLogger("atoms");

	private static 		int COMMIT_QUEUE_LIMIT = 0;
	public static final int COMMIT_ATTEMPTS = 5;
	public static final int BROADCAST_LIMIT = 2;
	public static final int INVENTORY_SYNC_LIMIT = 32;
    public static final int SHARD_THRESHOLD_STEP = 8;
    public static final int CHECKSUM_SYNC_LIMIT = 32;
	public static final int CHECKSUM_CHUNKS_LIMIT = 1<<16;
	public static final int CHECKSUM_CHUNKS_BITSET_LIMIT = 1<<13;

	// Size of each fragment
	private static final int FRAGMENT_SIZE = 60_000;

	// Threshold for messages about large atoms in FRAGMENT_SIZE fragments
	private static final int FRAGMENT_COUNT_THRESHOLD = 16;

	private static final Comparator<AtomSyncDeliveryResponseMessage> FRAGMENT_ORDER_COMPARATOR = Comparator.comparingInt(AtomSyncDeliveryResponseMessage::getFragment);

	// Chunk size for AtomDiscoveryResponseMessage
	private static final int MAX_ATOMS_PER_CHECKSUM_DISCOVERY_RESPONSE = 1024;

	// Comparator for ordering by timestamps / clocks
	private static final Comparator<PreparedAtom> TIMESTAMP_ORDER_COMPARATOR = Comparator.comparingLong(PreparedAtom::getTimestamp);
	private static final Comparator<PreparedAtom> ATOM_CLOCK_ORDER_COMPARATOR = Comparator.comparingLong(PreparedAtom::getClock);

	private class RemoteAID
	{
		private final AID aid;
		private final Peer peer;
		private boolean reattempted;

		public RemoteAID(AID aid, Peer peer)
		{
			this.aid = aid;
			this.peer = peer;
			this.reattempted = false;
		}

		boolean reattempt()
		{
			if (this.reattempted == false)
			{
				this.reattempted = true;
				return true;
			}

			return false;
		}

		public AID getAID()
		{
			return aid;
		}

		public Peer getPeer()
		{
			return peer;
		}

		@Override
		public boolean equals(Object obj)
		{
			return this.aid.equals(obj);
		}

		@Override
		public int hashCode()
		{
			return this.aid.hashCode();
		}
	}

	private class InventoryTimeout extends PeerTask
	{
		private final long 				 session;
		private final InventorySyncState inventorySyncState;

		InventoryTimeout(InventorySyncState inventorySyncState, long delay, TimeUnit unit)
		{
			super(inventorySyncState.getPeer(), delay, unit);

			this.session = inventorySyncState.getSession();
			this.inventorySyncState = inventorySyncState;
		}

		@Override
		public void execute()
		{
			if (getPeer().getState().in(State.CONNECTED))
			{
				if (this.session == this.inventorySyncState.getSession())
				{
					discoveryLog.error(this.getPeer()+ " did not respond to InventorySyncState "+this.inventorySyncState);

					if (AtomSync.this.inventorySyncQueue.add(this.inventorySyncState) == false)
					{
						atomsLog.error("Could not queue InventorySyncState for "+getPeer()+" ... rescheduling");
						Executor.getInstance().schedule(new PostponedInventoryTimeout(this.inventorySyncState, 1, TimeUnit.SECONDS));
					}
				}
			}
		}
	}

	private class PostponedInventoryTimeout extends PeerTask
	{
		private final long 				 session;
		private final InventorySyncState inventorySyncState;

		PostponedInventoryTimeout(InventorySyncState inventorySyncState, long delay, TimeUnit unit)
		{
			super(inventorySyncState.getPeer(), delay, unit);

			this.session = inventorySyncState.getSession();
			this.inventorySyncState = inventorySyncState;
		}

		@Override
		public void execute()
		{
			if (getPeer().getState().in(State.CONNECTED))
			{
				if (this.session == this.inventorySyncState.getSession())
				{
					if (AtomSync.this.inventorySyncQueue.add(this.inventorySyncState) == false)
					{
						atomsLog.error("Could not queue InventorySyncState for "+getPeer()+" ... rescheduling");
						Executor.getInstance().schedule(new PostponedInventoryTimeout(this.inventorySyncState, 1, TimeUnit.SECONDS));
					}
				}
			}
		}
	}

	private class DeliveryTimeout extends PeerTask
	{
		private final Set<AID> deliveries;

		DeliveryTimeout(final Peer peer, final Set<AID> deliveries, long delay, TimeUnit unit)
		{
			super(peer, delay, unit);

			this.deliveries = deliveries;
		}

		@Override
		public void execute()
		{
			for (AID object : this.deliveries)
			{
				RemoteAID delivery = AtomSync.this.removeDelivery(object);
				if (delivery != null)
				{
					if (delivery.reattempt() == true)
					{
						AtomSync.this.addInventory(delivery);
						discoveryLog.error(this.getPeer()+ " did not respond to delivery request for "+object+" - reattempting");
					}
					else
						discoveryLog.error(this.getPeer()+ " did not respond to delivery request for "+object);
				}
			}
		}
	}

	private class PostponedChecksumTimeout extends PeerTask
	{
		private final long session;
		private final ChecksumSyncState checksumSyncState;

		PostponedChecksumTimeout(final ChecksumSyncState checksumSyncState, long delay, TimeUnit unit)
		{
			super(checksumSyncState.getPeer(), delay, unit);

			this.session = checksumSyncState.getSession();
			this.checksumSyncState = checksumSyncState;
		}

		@Override
		public void execute()
		{
			if (getPeer().getState().in(State.CONNECTED))
			{
				if (this.session == this.checksumSyncState.getSession())
				{
					if (AtomSync.this.checksumSyncQueue.add(this.checksumSyncState) == false)
					{
						atomsLog.error("Could not queue ChecksumState for "+getPeer()+" ... rescheduling");
						Executor.getInstance().schedule(new PostponedChecksumTimeout(this.checksumSyncState, 1, TimeUnit.SECONDS));
					}
				}
			}
		}
	}

	private class ChecksumTimeout extends PeerTask
	{
		private final long 			session;
		private final ChecksumSyncState checksumSyncState;

		ChecksumTimeout(final ChecksumSyncState checksumSyncState, long delay, TimeUnit unit)
		{
			super(checksumSyncState.getPeer(), delay, unit);

			this.checksumSyncState = checksumSyncState;
			this.session = checksumSyncState.getSession();
		}

		@Override
		public void execute()
		{
			if (getPeer().getState().in(State.CONNECTED))
			{
				if (this.session == this.checksumSyncState.getSession())
				{
					discoveryLog.error(this.getPeer()+ " did not respond to ChecksumState "+this.checksumSyncState);

					if (AtomSync.this.checksumSyncQueue.add(this.checksumSyncState) == false)
					{
						atomsLog.error("Could not queue ChecksumState for "+getPeer()+" ... rescheduling");
						Executor.getInstance().schedule(new PostponedChecksumTimeout(this.checksumSyncState, 1, TimeUnit.SECONDS));
					}
				}
			}
		}
	}

	private class PrepareProcessor extends Executable
	{
		private final int shardThreshold;
		private final LinkedBlockingQueue<Atom> localPrepareQueue = new LinkedBlockingQueue<>();

		public PrepareProcessor(int shardThreshold)
		{
			this.shardThreshold = shardThreshold;
		}

		@Override
		public void execute()
		{
			while (!isTerminated())
			{
				Atom atom = this.localPrepareQueue.poll();

				if (atom == null)
				{
					try {
						atom = AtomSync.this.prepareQueue.poll(1, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						// We are done, just exit loop if we are interrupted
						break;
					}

					if (atom == null)
						continue;
				}

				int numShards = atom.getShards().size();

				if (numShards > this.shardThreshold && committingQueueSize(AtomComplexity.NONCOMPLEX) > 0)
				{
					int idealPrepareProcessor = MathUtils.roundUpBase2(numShards);
					PrepareProcessor prepareProcessor = AtomSync.this.prepareProcessors.get(idealPrepareProcessor);

					if (prepareProcessor == null)
						prepareProcessor = AtomSync.this.prepareProcessors.get(Integer.MAX_VALUE);

					prepareProcessor.localPrepareQueue.add(atom);
					continue;
				}

				if (atomsLog.hasLevel(Logging.DEBUG)) {
					atomsLog.debug("Validating Atom "+atom.getHID()+" to SIGNATURE");
				}

				try {
					TemporalProofValidator.validate(atom.getTemporalProof());
				} catch (Exception e) {
					atomsLog.error(e);
					Events.getInstance().broadcast(new AtomExceptionEvent(e, atom));
					continue;
				}

				final SimpleRadixEngineAtom cmAtom;
				try {
					cmAtom = RadixEngineUtils.toCMAtom(atom);
				} catch (CMAtomConversionException e) {
					ConstraintMachineValidationException ex = new ConstraintMachineValidationException(atom, e.getMessage(), e.getDataPointer());
					atomsLog.error(ex);
					Events.getInstance().broadcast(new AtomExceptionEvent(ex, atom));
					continue;
				}

				Modules.get(ValidationHandler.class).getRadixEngine().store(cmAtom, new AtomEventListener<SimpleRadixEngineAtom>() {
					@Override
					public void onCMSuccess(SimpleRadixEngineAtom cmAtom) {
						if (atomsLog.hasLevel(Logging.DEBUG)) {
							atomsLog.debug("Validated Atom " + cmAtom.getAtom().getAID() + " to SIGNATURE");
						}
					}

					@Override
					public void onCMError(SimpleRadixEngineAtom cmAtom, Set<CMError> errors) {
						CMError cmError = errors.iterator().next();
						ConstraintMachineValidationException e = new ConstraintMachineValidationException(cmAtom.getAtom(), cmError.getErrorDescription(), cmError.getDataPointer());
						atomsLog.error(e);
						Events.getInstance().broadcast(new AtomExceptionEvent(e, (Atom) cmAtom.getAtom()));
					}

					@Override
					public void onStateStore(SimpleRadixEngineAtom cmAtom) {
						if (atomsLog.hasLevel(Logging.DEBUG)) {
							atomsLog.debug("Validated Atom " + cmAtom.getAtom().getAID() + " to COMPLETE");
						}
					}

					@Override
					public void onStateConflict(SimpleRadixEngineAtom cmAtom, DataPointer dp, SimpleRadixEngineAtom conflictAtom) {
						SpunParticle issueParticle = dp.getParticleFrom(conflictAtom.getAtom());

						final ParticleConflictException conflict = new ParticleConflictException(
							new ParticleConflict(
								issueParticle,
								ImmutableSet.of((Atom) cmAtom.getAtom(), (Atom) conflictAtom.getAtom())
							));
						AtomExceptionEvent atomExceptionEvent = new AtomExceptionEvent(conflict, (Atom) cmAtom.getAtom());
						Events.getInstance().broadcast(atomExceptionEvent);
						atomsLog.error(conflict);
					}

					@Override
					public void onStateMissingDependency(SimpleRadixEngineAtom cmAtom, DataPointer dp) {
						SpunParticle issueParticle = dp.getParticleFrom(cmAtom.getAtom());

						final AtomDependencyNotFoundException notFoundException =
							new AtomDependencyNotFoundException(
								String.format("Atom has missing dependencies in transitions: %s", issueParticle.getParticle().getHID()),
								Collections.singleton(issueParticle.getParticle().getHID()),
								(Atom) cmAtom.getAtom()
							);

						AtomExceptionEvent atomExceptionEvent = new AtomExceptionEvent(notFoundException, (Atom) cmAtom.getAtom());
						Events.getInstance().broadcast(atomExceptionEvent);
						atomsLog.error(notFoundException);
					}
				});
			}
		}
	}

	private Thread		prepareProcessorThreads[] = null;
	private	final LinkedBlockingQueue<Atom> prepareQueue = new LinkedBlockingQueue<>();
	private	final Map<Integer, PrepareProcessor> prepareProcessors = new HashMap<>();

	private	final Map<AID, AtomStatus>	committing = new ConcurrentHashMap<>();
	private	final Map<AID, Integer> commitAttempts = new ConcurrentHashMap<>();
	private final AtomicInteger complexAtomsInCommitting = new AtomicInteger(0);
	private final AtomicInteger nonComplexAtomsInCommitting = new AtomicInteger(0);

	private Executable 	broadcastProcessor = null;
	private Thread		broadcastThread = null;
	private	final SetBlockingQueue<Atom> 	broadcastQueue = new SetBlockingQueue<>();

	private Executable 	inventoryProcessor = null;
	private Thread		inventoryThread = null;
	private final BlockingQueue<RemoteAID> inventories = new LinkedBlockingQueue<>();
	private	final Map<Peer, InventorySyncState> inventorySyncStates = new ConcurrentHashMap<>();
	private	final SetBlockingQueue<InventorySyncState> inventorySyncQueue = new SetBlockingQueue<>();

	private Executable 	deliveryProcessor = null;
	private Thread		deliveryThread = null;
	private	final Map<AID, RemoteAID> deliveries = new ConcurrentHashMap<>();
	private	final Map<AID, TreeSet<AtomSyncDeliveryResponseMessage>> deliveryFragments = new ConcurrentHashMap<>();

	private Executable 	checksumProcessor = null;
	private Thread		checksumThread = null;
	private	final Map<Peer, ChecksumSyncState> checksumSyncStates = new ConcurrentHashMap<>();
	private	final SetBlockingQueue<ChecksumSyncState> 	checksumSyncQueue = new SetBlockingQueue<>();

	private final Set<Peer> syncPeers = new HashSet<>();

	public AtomSync()
	{
		super();

		AtomSync.COMMIT_QUEUE_LIMIT = Modules.get(RuntimeProperties.class).get("ledger.sync.commit.max", Math.max(16384, Math.max(16384, Math.floor(Runtime.getRuntime().maxMemory()*0.1/16384)))).intValue();
		atomsLog.info("Using commit queue limit of " + AtomSync.COMMIT_QUEUE_LIMIT);
	}

	@Override
	public void start_impl() throws ModuleException
	{
		// TODO remove atomsync so we no longer need this
		Modules.put(AtomSyncView.class, new AtomSyncView() {
			@Override
			public void receive(Atom atom) {
				AtomSync.this.store(atom);
			}

			@Override
			public AtomStatus getAtomStatus(AID aid) {
				return AtomSync.this.committing.get(aid);
			}

			@Override
			public long getQueueSize() {
				return AtomSync.this.committingQueueSize(AtomComplexity.ALL);
			}

			@Override
			public Map<String, Object> getMetaData() {
				return AtomSync.this.getMetaData();
			}
		});

		register("atom.broadcast", new MessageProcessor<AtomBroadcastMessage>()
		{
			@Override
			public void process(AtomBroadcastMessage message, Peer peer)
			{
				try
				{
					if (atomsLog.hasLevel(Logging.DEBUG))
						atomsLog.debug("atom.broadcast for Atom "+message.getAtomHID());

					if (AtomSync.this.committing.containsKey(message.getAtomHID()) == false &&
						AtomSync.this.deliveries.containsKey(message.getAtomHID()) == false &&
						Modules.get(AtomStore.class).hasAtom(message.getAtomHID()) == false)
						AtomSync.this.addInventory(new RemoteAID(message.getAtomHID(), peer));
				}
				catch (Exception ex)
				{
					atomsLog.error("Processing of atom.broadcast for Atom "+message.getAtomHID()+" failed", ex);
				}
			}
		});

		register("atom.sync.checksum.chunks.discovery.request", new MessageProcessor<AtomChecksumChunksDiscoveryRequestMessage>()
		{
			@Override
			public void process(AtomChecksumChunksDiscoveryRequestMessage message, Peer peer)
			{
				Executor.getInstance().submit(new Executable()
				{
					@Override
					public void execute()
					{
						try
						{
							if (discoveryLog.hasLevel(Logging.DEBUG))
								discoveryLog.debug("atom.sync.checksum.chunks.discovery.request for checksums from chunk "+message.getChunk()+" with chunk mask "+message.getChunkMask()+" from "+peer);

							BArray chunkBits = new BArray(CHECKSUM_CHUNKS_BITSET_LIMIT);

							int from = (message.getChunk() / CHECKSUM_CHUNKS_BITSET_LIMIT) * CHECKSUM_CHUNKS_BITSET_LIMIT;
							for (int chunk = message.getChunk() ; chunk < Math.min(message.getChunk() + AtomSync.CHECKSUM_CHUNKS_LIMIT, ShardSpace.SHARD_CHUNKS) ; chunk++)
							{
								if (chunk == from + AtomSync.CHECKSUM_CHUNKS_BITSET_LIMIT)
								{
									Messaging.getInstance().send(new AtomChecksumChunksDiscoveryResponseMessage(chunkBits, from, from + AtomSync.CHECKSUM_CHUNKS_BITSET_LIMIT), peer);
									from += AtomSync.CHECKSUM_CHUNKS_BITSET_LIMIT;
									chunkBits.clear();
								}

								long checksum = Modules.get(ShardChecksumStore.class).getChecksum(chunk, peer.getSystem().getShards().getRange());
								if ((checksum & (1 << message.getChunkMask())) != 0)
									chunkBits.set(chunk - from);
							}

							Messaging.getInstance().send(new AtomChecksumChunksDiscoveryResponseMessage(chunkBits, from, from + AtomSync.CHECKSUM_CHUNKS_BITSET_LIMIT), peer);
						}
						catch (Exception ex)
						{
							discoveryLog.error("Processing of atom.sync.checksum.chunks.discovery.request from "+peer+" failed", ex);
						}
					}
				});
			}
		});

		register("atom.sync.checksum.chunks.discovery.response", new MessageProcessor<AtomChecksumChunksDiscoveryResponseMessage>()
		{
			@Override
			public void process(AtomChecksumChunksDiscoveryResponseMessage message, Peer peer)
			{
				Executor.getInstance().submit(new Executable()
				{
					@Override
					public void execute()
					{
						try
						{
							if (discoveryLog.hasLevel(Logging.DEBUG))
								discoveryLog.debug("atom.sync.checksum.chunks.discovery.response for checksum chunks "+message.getFrom()+" -> "+message.getTo()+" from "+peer);

							synchronized(AtomSync.this.checksumSyncStates)
							{
								ChecksumSyncState checksumSyncState = AtomSync.this.checksumSyncStates.get(peer);

								if (checksumSyncState == null)
									throw new SocketException("Can not process atom.sync.checksum.chunks.discovery.response "+peer+" is gone");

								checksumSyncState.setChunkBits(message.getChunkBits(), message.getFrom(), message.getTo());
							}
						}
						catch (Exception ex)
						{
							discoveryLog.error("Processing of atom.sync.checksum.chunks.discovery.response from "+peer+" failed", ex);
						}
					}
				});
			}
		});

		register("atom.sync.checksum.discovery.request", new MessageProcessor<AtomChecksumDiscoveryRequestMessage>()
		{
			@Override
			public void process(AtomChecksumDiscoveryRequestMessage message, Peer peer)
			{
				Executor.getInstance().submit(new Executable()
				{
					@Override
					public void execute()
					{
						try
						{
							if (discoveryLog.hasLevel(Logging.DEBUG))
								discoveryLog.debug("atom.sync.checksum.delivery.request for inventory for shard chunk "+message.getIndex()+" and range "+message.getRange()+" from "+peer);

							Set<AID> atoms = Modules.get(AtomStore.class).getByShardChunkAndRange(message.getIndex(), message.getRange());
							if (!atoms.isEmpty()) {
								for (List<AID> fragmentAtoms : Iterables.partition(atoms, MAX_ATOMS_PER_CHECKSUM_DISCOVERY_RESPONSE)) {
									Messaging.getInstance().send(new AtomChecksumDiscoveryResponseMessage(fragmentAtoms), peer);
								}
							}

							if (discoveryLog.hasLevel(Logging.DEBUG))
								discoveryLog.debug("atom.sync.checksum.delivery.request collected "+atoms.size()+" inventory objects for shard chunk "+message.getIndex()+" and range "+message.getRange()+" for "+peer);
						}
						catch (Exception ex)
						{
							discoveryLog.error("Processing of atom.sync.checksum.discovery.request from "+peer+" failed", ex);
						}
					}
				});
			}
		});

		register("atom.sync.checksum.discovery.response", new MessageProcessor<AtomChecksumDiscoveryResponseMessage>()
		{
			@Override
			public void process(AtomChecksumDiscoveryResponseMessage message, Peer peer)
			{
				Executor.getInstance().submit(new Executable()
				{
					@Override
					public void execute()
					{
						try
						{
							if (discoveryLog.hasLevel(Logging.DEBUG))
								discoveryLog.debug("atom.sync.checksum.discovery.response "+message.getObjects().size()+" checksum objects from "+peer);

							if (message.getObjects().isEmpty() == false)
							{
								for (AID object : message.getObjects())
								{
									if (AtomSync.this.committing.containsKey(object) == false &&
										AtomSync.this.deliveries.containsKey(object) == false &&
										Modules.get(AtomStore.class).hasAtom(object) == false)
										AtomSync.this.addInventory(new RemoteAID(object, peer));
								}
							}
						}
						catch (Exception ex)
						{
							discoveryLog.error("Processing of atom.sync.checksum.delivery.response from "+peer+" failed", ex);
						}
					}
				});
			}
		});

		register("atom.sync.inventory.request", new MessageProcessor<AtomSyncInventoryRequestMessage>()
		{
			@Override
			public void process(AtomSyncInventoryRequestMessage message, Peer peer)
			{
				Executor.getInstance().submit(new Executable()
				{
					@Override
					public void execute()
					{
						long start = SystemProfiler.getInstance().begin();

						try
						{
							AtomDiscoveryRequest atomDiscoveryRequest = new AtomDiscoveryRequest(Action.DISCOVER);
							atomDiscoveryRequest.setLimit((short) 64);
							atomDiscoveryRequest.setCursor(message.getCursor());
							atomDiscoveryRequest.setShards(message.getShards());
							Modules.get(AtomSyncStore.class).discovery(atomDiscoveryRequest);

							Messaging.getInstance().send(new AtomSyncInventoryResponseMessage(atomDiscoveryRequest.getInventory(), atomDiscoveryRequest.getCursor()), peer);
							if (discoveryLog.hasLevel(Logging.DEBUG))
								discoveryLog.debug("Sent atom.sync.inventory.response "+message+" to "+peer);
						}
						catch (Exception ex)
						{
							atomsLog.error("Processing of atom.sync.inventory.request "+message+" from "+peer+" failed", ex);
						}
						finally
						{
							Modules.ifAvailable(SystemProfiler.class, a -> a.increment("ATOM_SYNC:INVENTORY_REQUEST_TASK", start));
						}
					}
				});
			}
		});

		register("atom.sync.inventory.response", new MessageProcessor<AtomSyncInventoryResponseMessage>()
		{
			@Override
			public void process(AtomSyncInventoryResponseMessage message, Peer peer)
			{
				Executor.getInstance().submit(new Executable()
				{
					@Override
					public void execute()
					{
						long start = SystemProfiler.getInstance().begin();

						try
						{
							if (message.getInventory().isEmpty() == false)
							{
								for (AID object : message.getInventory())
									if (AtomSync.this.committing.containsKey(object) == false &&
										AtomSync.this.deliveries.containsKey(object) == false &&
										Modules.get(AtomStore.class).hasAtom(object) == false)
										addInventory(new RemoteAID(object, peer));
							}

							synchronized(AtomSync.this.inventorySyncStates)
							{
								InventorySyncState inventorySyncState = AtomSync.this.inventorySyncStates.get(peer);
								inventorySyncState.setState(new State(State.DISCOVERED));
								inventorySyncState.setCursor(message.getCursor());
								AtomSync.this.inventorySyncQueue.add(inventorySyncState);
							}
						}
						catch (Exception ex)
						{
							atomsLog.error("Processing of atom.sync.inventory.response "+message+" from "+peer+" failed", ex);
						}
						finally
						{
							Modules.ifAvailable(SystemProfiler.class, a -> a.increment("ATOM_SYNC:INVENTORY_RESPONSE_TASK", start));
						}
					}
				});
			}
		});

		register("atom.sync.delivery.request", new MessageProcessor<AtomSyncDeliveryRequestMessage>()
		{
			@Override
			public void process(AtomSyncDeliveryRequestMessage message, Peer peer)
			{
				Executor.getInstance().submit(new Executable()
				{
					@Override
					public void execute()
					{
						long start = SystemProfiler.getInstance().begin();

						try
						{
							Collection<Atom> atoms = Modules.get(AtomStore.class).getAtoms(message.getInventory());
							if (atoms != null) {
								for (Atom atom : atoms)
								{
									if (Modules.get(RuntimeProperties.class).get("debug.atoms.sync.delivery_error", 0.0d) <= ThreadLocalRandom.current().nextDouble())
									{
										byte[] atomBytes = Modules.get(Serialization.class).toDson(atom, Output.WIRE);
										int numFragments = (atomBytes.length + FRAGMENT_SIZE - 1) / FRAGMENT_SIZE;

										if (numFragments > FRAGMENT_COUNT_THRESHOLD && atomsLog.hasLevel(Logging.DEBUG)) {
											atomsLog.debug("Discovered large atom "+atom.getAID()+" "+atomBytes.length+" bytes");
										}

										for (int fragment = 0 ; fragment < numFragments ; fragment++) {
											final int fragmentOffset = fragment * FRAGMENT_SIZE;
											final int fragmentUpperBound = Math.min(atomBytes.length, fragmentOffset + FRAGMENT_SIZE);
											byte[] fragmentBytes = Arrays.copyOfRange(atomBytes, fragmentOffset, fragmentUpperBound);
											Messaging.getInstance().send(new AtomSyncDeliveryResponseMessage(atom.getAID(), fragmentBytes, fragment, numFragments), peer);
										}
									}
								}

								if (discoveryLog.hasLevel(Logging.DEBUG))
									discoveryLog.debug("atom.sync.delivery.request collected "+atoms.size()+" atoms to send to "+peer);
							}
						}
						catch (Exception ex)
						{
							atomsLog.error("Processing of atom.sync.delivery.request "+message+" from "+peer+" failed", ex);
						}
						finally
						{
							Modules.ifAvailable(SystemProfiler.class, a -> a.increment("ATOM_SYNC:DELIVERY_REQUEST_TASK", start));
						}
					}
				});
			}
		});

		register("atom.sync.delivery.response", new MessageProcessor<AtomSyncDeliveryResponseMessage>()
		{
			@Override
			public void process(AtomSyncDeliveryResponseMessage message, Peer peer)
			{
				Executor.getInstance().submit(new Executable()
				{
					@Override
					public void execute()
					{
						long start = SystemProfiler.getInstance().begin();

						try
						{
							if (discoveryLog.hasLevel(Logging.DEBUG))
								discoveryLog.debug("atom.delivery.response "+message+" from "+peer);

							Atom atom = processFragment(message);
							if (atom != null) {
								AtomSync.this.removeDelivery(atom.getAID());
								queue(atom);
							}
						}
						catch (Throwable ex)
						{
							atomsLog.error("Processing of atom.delivery.response "+message+" from "+peer+" failed", ex);
						}
						finally
						{
							Modules.ifAvailable(SystemProfiler.class, a -> a.increment("ATOM_SYNC:DELIVERY_RESPONSE_TASK", start));
						}
					}

					private Atom processFragment(AtomSyncDeliveryResponseMessage message) throws IOException {
						if (message.getFragments() == 1) {
							return Modules.get(Serialization.class).fromDson(message.getAtomFragment(), Atom.class);
						} else {
							TreeSet<AtomSyncDeliveryResponseMessage> fragments = AtomSync.this.deliveryFragments.computeIfAbsent(message.getAtom(), k -> new TreeSet<>(FRAGMENT_ORDER_COMPARATOR));

							synchronized(fragments) {
								fragments.add(message);
								if (fragments.size() == message.getFragments()) {
									int atomBytesSize = 0;
									for (AtomSyncDeliveryResponseMessage f : fragments) {
										atomBytesSize += f.getAtomFragment().length;
									}
									byte[] atomBytes = new byte[atomBytesSize];
									int index = 0;
									for (AtomSyncDeliveryResponseMessage f : fragments) {
										byte[] atomFragment = f.getAtomFragment();
										System.arraycopy(atomFragment, 0, atomBytes, index, atomFragment.length);
										index += atomFragment.length;
									}

									if (message.getFragments() > FRAGMENT_COUNT_THRESHOLD && atomsLog.hasLevel(Logging.DEBUG))
										atomsLog.debug("Reconstructing large atom "+message.getAtom()+" "+atomBytesSize+" bytes");

									return Modules.get(Serialization.class).fromDson(atomBytes, Atom.class);
								}
							}
						}
						return null;
					}
				});
			}
		});

		register("atom.submit", new MessageProcessor<AtomSubmitMessage>()
		{
			@Override
			public void process(AtomSubmitMessage message, Peer peer)
			{
				try
				{
					if (atomsLog.hasLevel(Logging.DEBUG))
						atomsLog.debug("atom.submit "+message.getAtom()+" from "+peer);

					store(message.getAtom());
				}
				catch (Throwable ex)
				{
					atomsLog.error("Processing of atom.submit "+message.getAtom()+" from "+peer+" failed", ex);
				}
			}
		});

		Events.getInstance().register(PeerEvent.class, this.peerListener);
		Events.getInstance().register(AtomEvent.class, this.atomListener);
		Events.getInstance().register(ParticleEvent.class, this.particleListener);
		Events.getInstance().register(AtomExceptionEvent.class, this.exceptionListener);

		this.broadcastProcessor = new Executable()
		{
			@Override
			public void execute()
			{
				while (!isTerminated())
				{
					Atom atom = null;

					try { atom = AtomSync.this.broadcastQueue.poll(1, TimeUnit.SECONDS); }
					catch (Exception ex)
					{
						// DONT CARE //
					}

					if (atom == null)
						continue;

					try
					{
						TemporalVertex temporalVertex = atom.getTemporalProof().getVertexByNID(LocalSystem.getInstance().getNID());

						if (temporalVertex == null)
							throw new IllegalStateException("TemporalVertex for "+LocalSystem.getInstance().getNID()+" not found!");

						if (temporalVertex.getEdges().isEmpty())
							continue;

						List<Peer> broadcastPeers = Modules.get(PeerHandler.class).getPeers(PeerDomain.NETWORK, temporalVertex.getEdges(), null, null);
						if (atomsLog.hasLevel(Logging.DEBUG))
							atomsLog.debug("Broadcasting Atom "+atom.getHID()+" to "+broadcastPeers.size()+" broadcast peers "+broadcastPeers);

						for (Peer broadcastPeer : broadcastPeers)
						{
							try
							{
								broadcastPeer = Network.getInstance().connect(broadcastPeer.getURI(), Protocol.UDP);
								Messaging.getInstance().send(new AtomBroadcastMessage(atom.getAID()), broadcastPeer);
							}
							catch (IOException ioex)
							{
								atomsLog.error("Failed to broadcast Atom "+atom+" to "+broadcastPeer, ioex);
							}
						}
					}
					catch (Throwable t)
					{
						atomsLog.error("Unable to broadcast and route Atom "+atom.getAID(), t);
					}
				}
			}
		};

		this.broadcastThread = new Thread(this.broadcastProcessor);
		this.broadcastThread.setDaemon (true);
		this.broadcastThread.setName("Broadcast Processor");
		this.broadcastThread.start();

		this.checksumProcessor = new Executable()
		{
			@Override
			public void execute()
			{
				if (Modules.get(RuntimeProperties.class).get("debug.atoms.sync.disable_checksum", false) == true)
					return;

				while (!isTerminated())
				{
					// TODO needs optimising, will be super slow with larger networks
					for (Peer peer : Network.getInstance().get(Protocol.UDP, State.CONNECTED))
					{
						synchronized(AtomSync.this.syncPeers)
						{
							if (AtomSync.this.syncPeers.contains(peer) == false)
								continue;
						}

						synchronized(AtomSync.this.checksumSyncStates)
						{
							ChecksumSyncState checksumSyncState = AtomSync.this.checksumSyncStates.get(peer);

							if (checksumSyncState == null)
							{
								checksumSyncState = new ChecksumSyncState(peer, LocalSystem.getInstance().getShards(), (int) (System.nanoTime() % ShardSpace.SHARD_CHUNKS));
								AtomSync.this.checksumSyncStates.put(peer, checksumSyncState);
								AtomSync.this.checksumSyncQueue.add(checksumSyncState);
							}
						}
					}

					ChecksumSyncState checksumState = null;

					try
					{
						if (AtomSync.this.inventories.size() >= AtomSync.COMMIT_QUEUE_LIMIT)
						{
							discoveryLog.error("Delaying next inventory, inventory size of "+AtomSync.this.inventories.size()+" is greater than max of "+AtomSync.COMMIT_QUEUE_LIMIT);
							Thread.sleep(1000);
							continue;
						}

						if ((AtomSync.this.deliveries.size()+AtomSync.this.committing.size()) >= AtomSync.COMMIT_QUEUE_LIMIT)
						{
							Events.getInstance().broadcast(new QueueFullEvent());
							if (discoveryLog.hasLevel(Logging.DEBUG))
								discoveryLog.debug("Delaying next checksum, commit queue size of "+(AtomSync.this.deliveries.size()+AtomSync.this.committing.size())+" is greater than max of "+AtomSync.COMMIT_QUEUE_LIMIT);
							Thread.sleep(1000);
							continue;
						}

						checksumState = AtomSync.this.checksumSyncQueue.poll(1, TimeUnit.SECONDS);
					}
					catch (InterruptedException e)
					{
						/* DONT CARE, so re-interrupt and let someone else handle it */
						Thread.currentThread().interrupt();
					}

					if (checksumState == null)
						continue;

					try
					{
						if (Modules.get(RuntimeProperties.class).get("debug.atoms.sync.disable_inventory", false) == false)
						{
							if (checksumState.getState().in(State.NONE) &&
								AtomSync.this.inventorySyncStates.containsKey(checksumState.getPeer()) == false)
							{
								discoveryLog.error("Postponing checksum sync for "+checksumState.getPeer()+" as inventory sync has not yet started");
								Executor.getInstance().schedule(new PostponedChecksumTimeout(checksumState, 10, TimeUnit.SECONDS));
								continue;
							}

							if (checksumState.getState().in(State.NONE) &&
								LocalSystem.getInstance().isSynced(checksumState.getPeer().getSystem()) == false) // &&
//								checksumState.getPeer().getSystem().getClock().get() - AtomSync.this.inventorySyncStates.get(checksumState.getPeer()).getCursor().getPosition() > Math.sqrt(checksumState.getPeer().getSystem().getClock().get()) == true)
							{
								discoveryLog.error("Postponing checksum sync for "+checksumState.getPeer()+" as inventory sync is underway");
								Executor.getInstance().schedule(new PostponedChecksumTimeout(checksumState, 10, TimeUnit.SECONDS));
								continue;
							}
						}

						// TODO need timeouts
						if (checksumState.getState().in(State.NONE))
						{
							if (discoveryLog.hasLevel(Logging.DEBUG))
								discoveryLog.debug("Initiating checksum discovery for "+checksumState.getPeer());
							checksumState.setState(new State(State.DISCOVERING));
							AtomChecksumChunksDiscoveryRequestMessage m = new AtomChecksumChunksDiscoveryRequestMessage(checksumState.getChunk(), checksumState.getChunkMask());
							Messaging.getInstance().send(m, checksumState.getPeer());
							Executor.getInstance().schedule(new PostponedChecksumTimeout(checksumState, 10, TimeUnit.SECONDS));
						}
						else if (checksumState.getState().in(State.DISCOVERING))
						{
							for (int chunk = checksumState.getChunk() ; chunk < Math.min(checksumState.getChunk() + AtomSync.CHECKSUM_CHUNKS_LIMIT, ShardSpace.SHARD_CHUNKS) ; chunk++)
							{
								long checksum = Modules.get(ShardChecksumStore.class).getChecksum(chunk, checksumState.getShards().getRange());
								if (checksumState.getChunkBits().get(chunk) == ((checksum & (1 << checksumState.getChunkMask())) == 0))
								{
									if (discoveryLog.hasLevel(Logging.DEBUG))
										discoveryLog.debug("Checksum discovery request of "+chunk+":"+checksumState.getShards().getRange()+" from "+checksumState.getPeer());
									AtomChecksumDiscoveryRequestMessage m = new AtomChecksumDiscoveryRequestMessage(chunk, checksumState.getShards().getRange());
									Messaging.getInstance().send(m, checksumState.getPeer());
								}
							}

							checksumState.setState(new State(State.DISCOVERED));
							AtomSync.this.checksumSyncQueue.add(checksumState);
						}
						else if (checksumState.getState().in(State.DISCOVERED))
						{
							if (checksumState.next(AtomSync.CHECKSUM_CHUNKS_LIMIT) == false)
							{
								if (discoveryLog.hasLevel(Logging.DEBUG))
									discoveryLog.debug("Checksum inventory delivery completed for "+checksumState.getPeer());
								checksumState.setChunkMask((byte) ((checksumState.getChunkMask()+1) % Long.SIZE));
								checksumState.setState(new State(State.NONE));
								Executor.getInstance().schedule(new PostponedChecksumTimeout(checksumState, 10, TimeUnit.SECONDS));
							}
							else
							{
								AtomChecksumChunksDiscoveryRequestMessage m = new AtomChecksumChunksDiscoveryRequestMessage(checksumState.getChunk(), checksumState.getChunkMask());
								Messaging.getInstance().send(m, checksumState.getPeer());
								checksumState.setState(new State(State.DISCOVERING));
								Executor.getInstance().schedule(new PostponedChecksumTimeout(checksumState, 10, TimeUnit.SECONDS));
							}
						}
						else
						{
							if (discoveryLog.hasLevel(Logging.DEBUG))
								discoveryLog.debug("Checksum for "+checksumState.getPeer()+" is in state "+checksumState.getState());
						}
					}
					catch (Exception ex)
					{
						discoveryLog.error("Failed checksum sync for "+checksumState.getPeer()+" ... postponing", ex);
						Executor.getInstance().schedule(new PostponedChecksumTimeout(checksumState, 10, TimeUnit.SECONDS));
					}
				}
			}
		};

		this.checksumThread = new Thread(this.checksumProcessor);
		this.checksumThread.setDaemon (true);
		this.checksumThread.setName("Checksum Processor");
		this.checksumThread.start();

		// TODO convert this to a queue type workflow as the ChecksumState stuff
		this.inventoryProcessor = new Executable()
		{
			@Override
			public void execute()
			{
				if (Modules.get(RuntimeProperties.class).get("debug.atoms.sync.disable_inventory", false) == true)
					return;

				while (!isTerminated())
				{
					// TODO needs optimising, will be super slow with larger networks
					for (Peer peer : Network.getInstance().get(Protocol.UDP, State.CONNECTED))
					{
						synchronized(AtomSync.this.syncPeers)
						{
							if (AtomSync.this.syncPeers.contains(peer) == false)
								continue;
						}

						synchronized(AtomSync.this.inventorySyncStates)
						{
							InventorySyncState inventorySyncState = AtomSync.this.inventorySyncStates.get(peer);

							if (inventorySyncState == null)
							{
								try
								{
									// Fetch any historic inventory cursor from the AtomSyncStore.
									DiscoveryCursor inventoryCursor = Modules.get(AtomSyncStore.class).getSyncState(peer.getSystem().getNID());

									// Check the form of the inventory cursor against the peer's system information it provided
									// Reset the cursor if a discrepancy is found
									if (inventoryCursor.getPosition() > (peer.getSystem().getClock().get() + 1))
										inventoryCursor.setPosition(0l);

									inventorySyncState = new InventorySyncState(peer, inventoryCursor);
									AtomSync.this.inventorySyncStates.put(peer, inventorySyncState);
									AtomSync.this.inventorySyncQueue.add(inventorySyncState);
								}
								catch (DatabaseException dbex)
								{
									discoveryLog.error("Can not retreive InventorySyncState from AtomSyncStore for "+peer, dbex);
								}
							}
						}
					}

					InventorySyncState inventorySyncState = null;

					try
					{
						if (AtomSync.this.inventories.size() >= AtomSync.COMMIT_QUEUE_LIMIT)
						{
							discoveryLog.error("Delaying next inventory, inventory size of "+AtomSync.this.inventories.size()+" is greater than max of "+AtomSync.COMMIT_QUEUE_LIMIT);
							Thread.sleep(1000);
							continue;
						}

						if ((AtomSync.this.deliveries.size()+AtomSync.this.committing.size()) >= AtomSync.COMMIT_QUEUE_LIMIT)
						{
							Events.getInstance().broadcast(new QueueFullEvent());
							discoveryLog.error("Delaying next inventory, commit queue size of "+(AtomSync.this.deliveries.size()+AtomSync.this.committing.size())+" is greater than max of "+AtomSync.COMMIT_QUEUE_LIMIT);
							Thread.sleep(1000);
							continue;
						}

						inventorySyncState = AtomSync.this.inventorySyncQueue.poll(1, TimeUnit.SECONDS);
					}
					catch (InterruptedException e)
					{
						// Exit on interrupt
						Thread.currentThread().interrupt();
						break;
					}

					if (inventorySyncState == null)
						continue;

					try
					{
						if (LocalSystem.getInstance().isAhead(inventorySyncState.getPeer().getSystem()))
						{
							discoveryLog.error(inventorySyncState.getPeer()+" is not synchronized");
							Executor.getInstance().schedule(new PostponedInventoryTimeout(inventorySyncState, inventorySyncState.incrementDelay(250, 10000), TimeUnit.MILLISECONDS));
							continue;
						}

						if (LocalSystem.getInstance().getShards().intersects(inventorySyncState.getPeer().getSystem().getShards()) == false)
						{
							discoveryLog.error(inventorySyncState.getPeer()+" does not intersect shard space");
							Executor.getInstance().schedule(new PostponedInventoryTimeout(inventorySyncState, 60000, TimeUnit.MILLISECONDS));
							continue;
						}

						// TODO need timeouts
						if (inventorySyncState.getState().in(State.NONE))
						{
							if (discoveryLog.hasLevel(Logging.DEBUG))
								discoveryLog.debug("Initiating inventory discovery for "+inventorySyncState.getPeer());
							inventorySyncState.setState(new State(State.DISCOVERING));
							AtomSync.this.inventorySyncQueue.add(inventorySyncState);
						}
						else if (inventorySyncState.getState().in(State.DISCOVERING))
						{
							if (discoveryLog.hasLevel(Logging.DEBUG))
								discoveryLog.debug("Inventory discovery from "+inventorySyncState.getPeer()+" at cursor position "+inventorySyncState.getCursor().getPosition());
							Messaging.getInstance().send(new AtomSyncInventoryRequestMessage(LocalSystem.getInstance().getShards(), inventorySyncState.getCursor()), inventorySyncState.getPeer());
							AtomSync.this.schedule(new InventoryTimeout(inventorySyncState, 10, TimeUnit.SECONDS));
							if (discoveryLog.hasLevel(Logging.DEBUG))
								discoveryLog.debug("Sent atom.sync.discovery.request for shards "+LocalSystem.getInstance().getShards()+" at cursor "+inventorySyncState.getCursor()+" to "+inventorySyncState.getPeer());
						}
						else if (inventorySyncState.getState().in(State.DISCOVERED))
						{
							if (inventorySyncState.getCursor().hasNext())
							{
								inventorySyncState.setCursor(inventorySyncState.getCursor().getNext());
								inventorySyncState.setDelay(0);
								inventorySyncState.setState(new State(State.DISCOVERING));
								AtomSync.this.inventorySyncQueue.add(inventorySyncState);
							}
							else if (inventorySyncState.getCursor().hasNext() == false && inventorySyncState.getCursor().getPosition() > 0)
							{
								inventorySyncState.setState(new State(State.DISCOVERING));
								Executor.getInstance().schedule(new PostponedInventoryTimeout(inventorySyncState, inventorySyncState.incrementDelay(1000, 10000), TimeUnit.MILLISECONDS));
								if (discoveryLog.hasLevel(Logging.DEBUG))
									discoveryLog.debug("Delaying next inventory for "+inventorySyncState.getPeer()+" for "+inventorySyncState.getDelay());
							}

							Modules.get(AtomSyncStore.class).storeSyncState(inventorySyncState.getPeer().getSystem().getNID(), inventorySyncState.getCursor());
						}
						else
						{
							if (discoveryLog.hasLevel(Logging.DEBUG))
								discoveryLog.debug("Inventory for "+inventorySyncState.getPeer()+" is in state "+inventorySyncState.getState());
						}
					}
					catch (Throwable t)
					{
						atomsLog.error("Unable to request inventory", t);
					}
				}
			}
		};

		this.inventoryThread = new Thread(this.inventoryProcessor);
		this.inventoryThread.setDaemon(true);
		this.inventoryThread.setName("Inventory Processor");
		this.inventoryThread.start();

		this.deliveryProcessor = new Executable()
		{
			@Override
			public void execute()
			{
				while (!isTerminated())
				{
					try
					{
						if ((AtomSync.this.deliveries.size()+AtomSync.this.committing.size()) >= AtomSync.COMMIT_QUEUE_LIMIT)
						{
							Events.getInstance().broadcast(new QueueFullEvent());
							if (discoveryLog.hasLevel(Logging.DEBUG))
								discoveryLog.debug("Delaying next delivery, commit queue size of "+(AtomSync.this.deliveries.size()+AtomSync.this.committing.size())+" is greater than max of "+AtomSync.COMMIT_QUEUE_LIMIT);
							Thread.sleep(1000);
							continue;
						}


						RemoteAID remoteAID = null;

						if ((remoteAID = AtomSync.this.inventories.poll(1, TimeUnit.SECONDS)) == null)
							continue;

						if (remoteAID.getPeer().getState().getDefinition().equals(State.CONNECTED) == false)
							continue;

						Peer peer = remoteAID.getPeer();
						Set<AID> deliveryInventory = new HashSet<>();

						try
						{
							AtomDiscoveryRequest deliveryRequest = new AtomDiscoveryRequest(Action.DELIVER);

							do
							{
								if (AtomSync.this.committing.containsKey(remoteAID.getAID()) == true ||
									AtomSync.this.deliveries.containsKey(remoteAID.getAID()) == true ||
									Modules.get(AtomStore.class).hasAtom(remoteAID.getAID()) == true)
									continue;

								if (peer == null)
									peer = remoteAID.getPeer();
								else if (peer.equals(remoteAID.getPeer()) == false)
									break;

								deliveryInventory.add(remoteAID.getAID());
								addDelivery(remoteAID);
								if (atomsLog.hasLevel(Logging.DEBUG))
									atomsLog.debug("Atom "+ remoteAID.getAID()+" requested in "+deliveryRequest.getSession()+" from "+peer);

								if (deliveryInventory.size() >= AtomSync.INVENTORY_SYNC_LIMIT)
									break;
							}

							while (AtomSync.this.inventories.peek() != null &&
								   AtomSync.this.inventories.peek().getPeer().equals(peer) &&
								   (remoteAID = AtomSync.this.inventories.poll()) != null);

							if (deliveryInventory.isEmpty() == false)
							{
								deliveryRequest.setInventory(deliveryInventory);
								Messaging.getInstance().send(new AtomSyncDeliveryRequestMessage(deliveryInventory), peer);
								AtomSync.this.schedule(new DeliveryTimeout(peer, deliveryInventory, 10, TimeUnit.SECONDS));
								if (discoveryLog.hasLevel(Logging.DEBUG))
									discoveryLog.debug("atom.delivery.request "+deliveryRequest+" to "+peer);
								SystemProfiler.getInstance().increment("ATOM_SYNCER:ATOMS_REQUESTED", deliveryInventory.size());
							}
						}
						catch(Throwable t)
						{
							for (AID object : deliveryInventory)
								removeDelivery(object);

							throw t;
						}
					}
					catch (Throwable t)
					{
						atomsLog.error("Unable to request delivery of inventory", t);
					}
				}
			}
		};

		this.deliveryThread = new Thread(this.deliveryProcessor);
		this.deliveryThread.setDaemon(true);
		this.deliveryThread.setName("Delivery Processor");
		this.deliveryThread.start();

		// Manage a thread "pool" ourselves.  If we allow the Executors service to do it for us, we have to implement
		// some complicated flow control.  TODO maybe we can investigate in the future
		this.prepareProcessorThreads = new Thread[Modules.get(RuntimeProperties.class).get("ledger.sync.threads", Math.max(1, Runtime.getRuntime().availableProcessors()))]; // FIXME causes issues with witnessed(atom) producing empty TPs when more than one thread

		for (int thread = 0 ; thread < this.prepareProcessorThreads.length ; thread++)
		{
			int shardThreshold = thread == (this.prepareProcessorThreads.length-1) ? Integer.MAX_VALUE : Math.min(4096, (AtomSync.SHARD_THRESHOLD_STEP << (thread*2)));
			PrepareProcessor prepareProcessor = new PrepareProcessor(shardThreshold);
			this.prepareProcessors.put(shardThreshold, prepareProcessor);
			this.prepareProcessorThreads[thread] = new Thread(prepareProcessor);
			this.prepareProcessorThreads[thread].setDaemon(true);
			this.prepareProcessorThreads[thread].setName("Atom Prepare Processor - "+thread+":"+shardThreshold);
			this.prepareProcessorThreads[thread].start();
		}

		RadixEngine<SimpleRadixEngineAtom> engine = Modules.get(ValidationHandler.class).getRadixEngine();

		final boolean skipAtomFeeCheck = Modules.isAvailable(RuntimeProperties.class)
			&& Modules.get(RuntimeProperties.class).get("debug.nopow", false);

		engine.addCMSuccessHook(
			new AtomCheckHook(
				() -> Modules.get(Universe.class),
				Time::currentTimestamp,
				skipAtomFeeCheck,
				Time.MAXIMUM_DRIFT
			)
		);

		engine.addCMSuccessHook((cmAtom -> {
			// TODO is this good here?
			// All atoms will be witnessed, even invalid ones.  If flooded with invalid atoms, it may make it harder for
			// remote nodes to determine if this node saw a particular atom vs a commitment stream that only includes
			// committed atoms.
			try {
				witnessed(cmAtom);
				return Result.success();
			} catch (Exception e) {
				atomsLog.error(e);
				Events.getInstance().broadcast(new AtomExceptionEvent(e, (Atom) cmAtom.getAtom()));
				return Result.error(e.getMessage());
			}
		}));

		// SYNC DISCOVERY //
		scheduleAtFixedRate(new ScheduledExecutable(10, 10, TimeUnit.SECONDS)
		{
			@Override
			public void execute()
			{
				// Get our preferred neighbour nodes to open TCP connections too
				Collection<URI> discovered = SyncDiscovery.getInstance().discover(new TCPPeerFilter());

				synchronized(AtomSync.this.syncPeers)
				{
					AtomSync.this.syncPeers.clear();

					for (URI uri : discovered)
					{
						try
						{
							if (discoveryLog.hasLevel(Logging.DEBUG))
								discoveryLog.debug("Discovered sync peer "+uri);
							Peer syncPeer = Network.getInstance().connect(uri, Protocol.UDP);
							AtomSync.this.syncPeers.add(syncPeer);
						}
						catch (Exception ex)
						{
							discoveryLog.error("Could not connect to sync peer "+uri, ex);
						}
					}
				}
			}
		});

		try {
			LinkedList<AID> atomIds = new LinkedList<>();
			for (ImmutableAtom immutableAtom : Modules.get(Universe.class).getGenesis()) {

				final Atom atom = (Atom) immutableAtom;
				if (!Modules.get(AtomStore.class).hasAtom(atom.getAID())) {
					final SimpleRadixEngineAtom cmAtom;
					try {
						cmAtom = RadixEngineUtils.toCMAtom(atom);
					} catch (CMAtomConversionException e) {
						throw new ConstraintMachineValidationException(atom, e.getMessage(), e.getDataPointer());
					}

					Modules.get(ValidationHandler.class).getRadixEngine().store(cmAtom,
						new AtomEventListener<SimpleRadixEngineAtom>() {
							@Override
							public void onCMError(SimpleRadixEngineAtom cmAtom, Set<CMError> errors) {
								CMError cmError = errors.iterator().next();
								ConstraintMachineValidationException e = new ConstraintMachineValidationException(cmAtom.getAtom(), cmError.getErrorDescription(), cmError.getDataPointer());
								log.fatal("Failed to process genesis Atom", e);
								System.exit(-1);
							}

							@Override
							public void onStateConflict(SimpleRadixEngineAtom cmAtom, DataPointer dp, SimpleRadixEngineAtom conflictAtom) {
								log.fatal("Failed to process genesis Atom");
								System.exit(-1);
							}

							@Override
							public void onStateMissingDependency(SimpleRadixEngineAtom cmAtom, DataPointer dp) {
								log.fatal("Failed to process genesis Atom");
								System.exit(-1);
							}
						});
				}
			}
			waitForAtoms(atomIds);
		}
		catch (Exception ex)
		{
			log.fatal("Failed to process genesis Atom", ex);
			System.exit(-1);
		}
	}

	private void waitForAtoms(LinkedList<AID> atomHashes) throws DatabaseException, InterruptedException {
		for (AID atomID : atomHashes) {
			while (!Modules.get(AtomStore.class).hasAtom(atomID)) {
				TimeUnit.MILLISECONDS.sleep(100);
			}
		}
	}

	@Override
	public void stop_impl()
	{
		this.inventoryProcessor.terminate(true);
		this.inventoryThread = null;

		this.deliveryProcessor.terminate(true);
		this.deliveryThread = null;

		this.checksumProcessor.terminate(true);
		this.checksumThread = null;

		this.broadcastProcessor.terminate(true);
		this.broadcastThread = null;

		this.prepareProcessors.forEach((threshold, processor) -> processor.terminate(true));
		this.prepareProcessors.clear();
		this.prepareProcessorThreads = null;

		Events.getInstance().deregister(ExceptionEvent.class, this.exceptionListener);
		Events.getInstance().deregister(ParticleEvent.class, this.particleListener);
		Events.getInstance().deregister(AtomEvent.class, this.atomListener);
		Events.getInstance().deregister(PeerEvent.class, this.peerListener);
	}

	@Override
	public String getName() { return "Atom Syncer"; }

	@Override
	public Map<String, Object> getMetaData()
	{
		Map<String, Object> metadata = MapHelper.mapOf(
			"broadcasting", this.broadcastQueue.size(),
			"deliveries", this.deliveries.size(),
			"checksumSyncStates", this.checksumSyncStates.size(),
			"checksumSyncQueue", this.checksumSyncQueue.size(),
			"inventories", this.inventories.size(),
			"inventorySyncQueue", this.inventorySyncQueue.size(),
			"preparing", this.prepareQueue.size(),
			"commitQueue", Modules.get(ValidationHandler.class).getRadixEngine().getCommitQueueSize(),
			"committing", this.committing.size()
		);
		metadata.put("complex", this.complexAtomsInCommitting.get());
		metadata.put("nonComplex", this.nonComplexAtomsInCommitting.get());

		Map<String, Object> syncMetaData = Maps.newHashMap();

		for (Peer syncPeer : this.syncPeers)
		{
			Map<String, Object> syncPeerMetaData = Maps.newHashMap();

			synchronized(AtomSync.this.inventorySyncStates)
			{
				if (AtomSync.this.inventorySyncStates.containsKey(syncPeer) == true)
				{
					Map<String, Object> inventorySyncPeerMetaData = Maps.newHashMap();
					inventorySyncPeerMetaData.put("cursor", AtomSync.this.inventorySyncStates.get(syncPeer).getCursor().getPosition());
					inventorySyncPeerMetaData.put("state", AtomSync.this.inventorySyncStates.get(syncPeer).getState().getName());
					syncPeerMetaData.put("inventory", inventorySyncPeerMetaData);
				}
			}

			synchronized(AtomSync.this.checksumSyncStates)
			{
				if (AtomSync.this.checksumSyncStates.containsKey(syncPeer) == true)
				{
					Map<String, Object> checksumSyncPeerMetaData = Maps.newHashMap();
					checksumSyncPeerMetaData.put("chunk", AtomSync.this.checksumSyncStates.get(syncPeer).getChunk());
					checksumSyncPeerMetaData.put("state", AtomSync.this.checksumSyncStates.get(syncPeer).getState().getName());
					syncPeerMetaData.put("checksum", checksumSyncPeerMetaData);
				}
			}

			syncMetaData.put(syncPeer.getSystem().getNID().toString(), syncPeerMetaData);
		}

		metadata.put("sync", syncMetaData);

		return metadata;
	}

	private boolean addInventory(RemoteAID remoteAID)
	{
		return this.inventories.offer(remoteAID);
	}

	private boolean addDelivery(RemoteAID remoteAID)
	{
		return this.deliveries.putIfAbsent(remoteAID.getAID(), remoteAID) == null ? true : false;
	}

	private RemoteAID removeDelivery(AID id)
	{
		RemoteAID remoteAID = this.deliveries.remove(id);
		this.deliveryFragments.remove(id);
		return remoteAID;
	}

	public int committingQueueSize(AtomComplexity complexity) {

		if (AtomComplexity.ALL.equals(complexity) == true || complexity == null)
			return this.deliveries.size() + this.committing.size();
		else if (AtomComplexity.COMPLEX.equals(complexity) == true)
			return this.deliveries.size() + this.complexAtomsInCommitting.get();
		else
			return this.deliveries.size() + this.nonComplexAtomsInCommitting.get();
	}

	public void store(Atom atom)
	{
		int queueSize = committingQueueSize(AtomComplexity.ALL);
		if (queueSize >= AtomSync.COMMIT_QUEUE_LIMIT)
		{
			Events.getInstance().broadcast(new QueueFullEvent());
			throw new IllegalStateException("Commit queue size of "+queueSize+" is greater than max of "+AtomSync.COMMIT_QUEUE_LIMIT);
		}

		queue(atom);
	}

	/**
	 * Retrieves the current status of an atom given it's aid
	 *
	 * @param aid the aid of the atom to retrieve the status for
	 * @return the status of an atom in the pipeline, otherwise null
	 */
	public AtomStatus getAtomStatus(AID aid) {
		return this.committing.get(aid);
	}

	private void queue(Atom atom)
	{
		long start = SystemProfiler.getInstance().begin();

		try
		{
			if (this.committing.containsKey(atom.getAID()))
			{
				if (atomsLog.hasLevel(Logging.DEBUG)) {
					atomsLog.debug("Atom "+atom.getAID()+" is already being processed");
					atomsLog.debug(ExceptionUtils.toString(Thread.currentThread().getStackTrace()));
				}
				throw new AtomAlreadyInProcessingException(atom);
			}

			if (Modules.get(AtomStore.class).hasAtom(atom.getAID()))
			{
				if (atomsLog.hasLevel(Logging.DEBUG)) {
					atomsLog.debug("Atom "+atom.getAID()+" is already committed");
					atomsLog.debug(ExceptionUtils.toString(Thread.currentThread().getStackTrace()));
				}
				throw new AtomAlreadyStoredException(atom);
			}

			this.committing.put(atom.getAID(), AtomStatus.PENDING_CM_VERIFICATION);
		}
		catch (AtomAlreadyStoredException | AtomAlreadyInProcessingException e)
		{
			throw e;
		}
		catch (Throwable t)
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_SYNCER:QUEUE:FAIL", start);
			atomsLog.error("Failed to queue " + atom.getAID(), t);
		}

		if (atomsLog.hasLevel(Logging.DEBUG))
			atomsLog.debug("Store Atom "+atom.getHID());
		Events.getInstance().broadcast(new AtomStoreEvent(atom));
	}

	private void witnessed(RadixEngineAtom radixEngineAtom) throws DatabaseException, ValidationException, CryptoException
	{
		final Atom atom = (Atom) ((SimpleRadixEngineAtom) radixEngineAtom).getAtom();
		TemporalVertex existingNIDVertex = atom.getTemporalProof().getVertexByNID(LocalSystem.getInstance().getNID());

		if (existingNIDVertex != null)
		{
			if (existingNIDVertex.getClock() > LocalSystem.getInstance().getClock().get())
			{
				LocalSystem.getInstance().set(existingNIDVertex.getClock(), existingNIDVertex.getCommitment(), atom.getTimestamp());
				if (atomsLog.hasLevel(Logging.DEBUG))
					atomsLog.debug("Discovered existing TemporalVertex "+existingNIDVertex+" for Atom "+atom.getHID());
			}

			return;
		}

		try
		{
			Pair<Long, Hash> update = LocalSystem.getInstance().update(atom.getAID(), atom.getTimestamp());

			NodeAddressGroupTable nodeGroupTable = null;
			Set<EUID> filteredNIDs = new HashSet<>();

			if (Modules.isAvailable(PeerHandler.class) == true)
			{
				// FIXME If PeerHandler is not available yet, causes a real mess when genesis atoms are committed.
				// Filter out the live peers with shards we need that are within sync bounds
				filteredNIDs.addAll(Modules.get(PeerHandler.class).getPeers(PeerDomain.NETWORK, PeerFilter.getInstance()).stream().
													 filter(peer -> peer.getSystem().isSynced(LocalSystem.getInstance()) == true). 					// Gossip to nodes that are in sync TODO isAhead is better?
													 filter(peer -> peer.getSystem().getShards().intersects(atom.getShards()) == true). 			// Gossip to nodes that serve the atom shards
													 filter(peer -> peer.getSystem().getNID().equals(LocalSystem.getInstance().getNID()) == false).	// Don't gossip to the local node
													 map(peer -> peer.getSystem().getNID()).
													 collect(Collectors.toSet()));
			}

			/*
			 * Broadcasts about new / updated Atoms should propagate via UDP up the RoutingTable groups.
			 */
			if (atom.getTemporalProof().isEmpty() == false)
			{
				// TODO check atom specifies the correct origin

				nodeGroupTable = new NodeAddressGroupTable(atom.getTemporalProof().getOrigin().getOwner().getUID(), filteredNIDs);

				List<EUID> broadcastNIDs = nodeGroupTable.getNext(LocalSystem.getInstance().getNID(), true).stream().limit(TemporalProof.BRANCH_VERTEX_NIDS).collect(Collectors.toList());
				if (broadcastNIDs.isEmpty() == true && atomsLog.hasLevel(Logging.DEBUG))
					atomsLog.debug("Broadcast NIDs from "+LocalSystem.getInstance().getNID()+" for "+atom.getAID()+" @ "+Modules.get(NtpService.class).getUTCTimeSeconds()+":"+Modules.get(Universe.class).toPlanck(Modules.get(NtpService.class).getUTCTimeMS(), Offset.NONE)+" are empty");

				List<EUID> previousNIDs = nodeGroupTable.getPrevious(LocalSystem.getInstance().getNID());
				TemporalVertex previousVertex = null;

				for (TemporalVertex vertex : atom.getTemporalProof().getVertices())
				{
					if (previousNIDs.contains(vertex.getOwner().getUID()) && vertex.getEdges().contains(LocalSystem.getInstance().getNID()))
					{
						previousVertex = vertex;
						break;
					}
					else if (previousVertex == null)
						previousVertex = vertex;
				}

				// TODO need to handle this better due to RoutingTable poisoning?
				if (previousVertex == null)
					throw new TemporalProofNotValidException(atom.getTemporalProof());

				TemporalProof branch = atom.getTemporalProof().getBranch(previousVertex, true);
				ECKeyPair nodeKey = LocalSystem.getInstance().getKeyPair();
				TemporalVertex vertex = new TemporalVertex(nodeKey.getPublicKey(),
														   update.getFirst(), Time.currentTimestamp(),
														   update.getSecond(),
		 					  							   previousVertex.getHID(), broadcastNIDs);
				branch.add(vertex, nodeKey);
				atom.getTemporalProof().add(vertex, nodeKey);
			}
			else
			{
				// TODO check atom specifies local node as the origin
				nodeGroupTable = new NodeAddressGroupTable(LocalSystem.getInstance().getNID(), filteredNIDs);

				List<EUID> broadcastNIDs = nodeGroupTable.getNext(LocalSystem.getInstance().getNID(), true).stream().limit(TemporalProof.ROOT_VERTEX_NIDS).collect(Collectors.toList());
				if (broadcastNIDs.isEmpty() == true && atomsLog.hasLevel(Logging.DEBUG))
					atomsLog.debug("Broadcast NIDs from origin "+LocalSystem.getInstance().getNID()+" for "+atom.getAID()+" @ "+Modules.get(NtpService.class).getUTCTimeSeconds()+":"+Modules.get(Universe.class).toPlanck(Modules.get(NtpService.class).getUTCTimeMS(), Offset.NONE)+" are empty");

				ECKeyPair nodeKey = LocalSystem.getInstance().getKeyPair();
				TemporalVertex vertex = new TemporalVertex(nodeKey.getPublicKey(),
						   								   update.getFirst(), Time.currentTimestamp(),
						   								   update.getSecond(),
	 					  							   	   EUID.ZERO, broadcastNIDs);
				atom.getTemporalProof().add(vertex, nodeKey);
			}

			atom.getTemporalProof().setState(StateDomain.VALIDATION, new State(State.COMPLETE));

			if (atomsLog.hasLevel(Logging.DEBUG))
				atomsLog.debug("Appended to TemporalProof for Atom "+atom.getHID());
		}
		catch (Throwable t)
		{
			LocalSystem.getInstance().revert();
			atomsLog.error("Reverted System for Atom "+atom.getHID(), t);
			throw t;
		}
	}

	// ATOM LISTENERS //
	private AtomListener atomListener = new AtomListener()
	{
		@Override
		public int getPriority()
		{
			return EventPriority.HIGH.priority();
		}

		@Override
		public Syncronicity getSyncronicity()
		{
			return Syncronicity.ASYNCRONOUS;
		}

		@Override
		public void process(AtomEvent event)
		{
			if (event instanceof AtomStoreEvent)
			{
				if (AtomSync.this.committing.containsKey(event.getAtom().getAID()) == false)
					discoveryLog.error("Atom STORE event for Atom "+event.getAtom().getAID()+" must be signalled by the AtomSyncer only");
					// TODO throw and exception here and ensure proper clean up
				else
				{
					if (event.getAtom().getParticleGroup(0).getParticleCount() > AtomSync.SHARD_THRESHOLD_STEP)
						AtomSync.this.complexAtomsInCommitting.incrementAndGet();
					else
						AtomSync.this.nonComplexAtomsInCommitting.incrementAndGet();

					AtomSync.this.prepareQueue.add(event.getAtom());
					if (atomsLog.hasLevel(Logging.DEBUG))
						atomsLog.debug("Storing Atom "+event.getAtom().getHID());//+": "+event.getAtom().getParticles().stream().map(Particle::toString).collect(Collectors.joining(",")));
				}
			}

			if (event instanceof AtomStoredEvent)
			{
				if (Modules.get(NtpService.class).getUTCTimeMS() - event.getAtom().getTimestamp() < Modules.get(Universe.class).getPlanck())
					AtomSync.this.broadcastQueue.add(event.getAtom());

				AtomSync.this.commitAttempts.remove(event.getAtom().getAID());

				if (AtomSync.this.committing.remove(event.getAtom().getAID()) != null)
				{
					if (event.getAtom().getParticleGroup(0).getParticleCount() > AtomSync.SHARD_THRESHOLD_STEP)
						AtomSync.this.complexAtomsInCommitting.decrementAndGet();
					else
						AtomSync.this.nonComplexAtomsInCommitting.decrementAndGet();
				}

				if (atomsLog.hasLevel(Logging.DEBUG))
					atomsLog.debug("Stored Atom "+event.getAtom().getHID());//+": "+event.getAtom().getParticles().stream().map(Particle::toString).collect(Collectors.joining(",")));
			}

			if (event instanceof AtomDeletedEvent)
			{
				AtomSync.this.committing.remove(event.getAtom().getAID());
				AtomSync.this.commitAttempts.remove(event.getAtom().getAID());

				if (atomsLog.hasLevel(Logging.DEBUG))
					atomsLog.debug("Deleted Atom "+event.getAtom().getHID());//+": "+event.getAtom().getParticles().stream().map(Particle::toString).collect(Collectors.joining(",")));
			}

			if (event instanceof AtomUpdatedEvent)
			{
				AtomSync.this.committing.remove(event.getAtom().getAID());
				AtomSync.this.commitAttempts.remove(event.getAtom().getAID());

				if (atomsLog.hasLevel(Logging.DEBUG))
					atomsLog.debug("Updated Atom "+event.getAtom().getHID());
			}
		}
	};

	// PARTICLE LISTENER //
	private ParticleListener particleListener = new ParticleListener()
	{
		@Override
		public int getPriority()
		{
			return EventPriority.HIGH.priority();
		}

		@Override
		public Syncronicity getSyncronicity()
		{
			return Syncronicity.SYNCRONOUS;
		}

		@Override
		public void process(ParticleEvent event)
		{
			try
			{
				if (event instanceof ConflictConcurrentEvent)
				{
					if (atomsLog.hasLevel(Logging.DEBUG))
						atomsLog.debug("ConflictConcurrentEvent for conflict "+((ConflictConcurrentEvent)event).getConflict().getUID());
					for (Atom atom : ((ConflictConcurrentEvent)event).getConflict().getAtoms())
					{
						if (!((ConflictConcurrentEvent)event).getConcurrentAtoms().contains(atom))
						{
							AtomSync.this.committing.remove(atom.getAID());
							if (atomsLog.hasLevel(Logging.DEBUG))
								atomsLog.debug("Removed committing Atom "+atom.getHID()+" due to ConflictConcurrentEvent for conflict "+((ConflictConcurrentEvent)event).getConflict().getUID());
						}
					}
				}

				if (event instanceof ConflictUpdatedEvent)
				{
					if (atomsLog.hasLevel(Logging.DEBUG))
						atomsLog.debug("ConflictUpdatedEvent for conflict "+((ConflictUpdatedEvent)event).getConflict().getUID());
					for (Atom atom : ((ConflictUpdatedEvent)event).getConflict().getAtoms())
					{
						AtomSync.this.committing.put(atom.getAID(), AtomStatus.CONFLICT_LOSER);
						if (atomsLog.hasLevel(Logging.DEBUG))
							atomsLog.debug("Added committing Atom "+atom.getHID()+" due to ConflictUpdatedEvent for conflict "+((ConflictUpdatedEvent)event).getConflict().getUID());
					}
				}

				if (event instanceof ConflictResolvedEvent)
				{
					for (Atom atom : ((ConflictResolvedEvent)event).getConflict().getAtoms())
					{
						AtomSync.this.committing.remove(atom.getAID());

						final SimpleRadixEngineAtom cmAtom;
						try {
							cmAtom = RadixEngineUtils.toCMAtom(atom);
						} catch (CMAtomConversionException e) {
							throw new IllegalStateException(e);
						}
						if (!atom.getAID().equals(((ConflictResolvedEvent)event).getConflict().getResult())) {
							Modules.get(ValidationHandler.class).getRadixEngine().delete(cmAtom);
						}
					}

					Atom resultAtom = ((ConflictResolvedEvent)event).getConflict().getAtom(((ConflictResolvedEvent)event).getConflict().getResult());
					if (!Modules.get(AtomStore.class).hasAtom(resultAtom.getAID()))
						queue(resultAtom);
					else
					{
						Modules.get(AtomStore.class).updateAtom(resultAtom);	// TODO does this need to go through the validation pipe again?
						AtomSync.this.broadcastQueue.add(resultAtom);
					}
				}

				if (event instanceof ConflictFailedEvent)
				{
					for (Atom atom : ((ConflictFailedEvent)event).getConflict().getAtoms())
						AtomSync.this.committing.remove(atom.getAID());
				}
			}
			catch (DatabaseException dbex)
			{
				for (Atom atom : ((ConflictEvent)event).getConflict().getAtoms())
					AtomSync.this.committing.remove(atom.getAID());

				atomsLog.error("Could not process "+event.getClass().getName(), dbex);
			}
		}
	};

	// EXCEPTION LISTENERS //
	private ExceptionListener exceptionListener = new ExceptionListener()
	{
		@Override
		public int getPriority()
		{
			return EventPriority.HIGH.priority();
		}

		@Override
		public void process(ExceptionEvent event)
		{
			if (!(event instanceof AtomExceptionEvent))
				return;

			if (event.getException() instanceof ParticleConflictException)
				Events.getInstance().broadcast(new ConflictDetectedEvent(((ParticleConflictException)event.getException()).getConflict()));
			else if (event.getException() instanceof ValidationException)
			{
				AtomSync.this.committing.remove(((AtomExceptionEvent)event).getAtom().getAID());

				if (event.getException() instanceof AtomDependencyNotFoundException)
				{
					int attempts = AtomSync.this.commitAttempts.getOrDefault(((AtomExceptionEvent)event).getAtom().getAID(), 1);
					AtomSync.this.commitAttempts.remove(((AtomExceptionEvent)event).getAtom().getAID());

					if (attempts < AtomSync.COMMIT_ATTEMPTS)
					{
						try
						{
							if (atomsLog.hasLevel(Logging.DEBUG))
								atomsLog.debug("Atom "+((AtomExceptionEvent)event).getAtom().getAID()+" commit reattempt "+attempts);
							AtomSync.this.queue(((AtomExceptionEvent)event).getAtom());
							AtomSync.this.commitAttempts.put(((AtomExceptionEvent)event).getAtom().getAID(), attempts+1);
						}
						catch (Exception ex)
						{
							if (atomsLog.hasLevel(Logging.DEBUG))
								atomsLog.debug("Atom "+((AtomExceptionEvent)event).getAtom().getAID()+" commit reattempt "+attempts+" failed:", ex);
						}
					}
					else if (atomsLog.hasLevel(Logging.DEBUG))
						atomsLog.debug("Atom "+((AtomExceptionEvent)event).getAtom().getAID()+" exceeded commit reattempts of "+attempts);
				}
			}
			else if (event.getException() instanceof DatabaseException)
			{
				AtomSync.this.committing.remove(((AtomExceptionEvent)event).getAtom().getAID());

				// FIXME this is now incorrect (but works) as AtomStore primaries are no longer the clock value but the HID and clock values are unique secondaries.
				// This current way of recovering violates the clock rules and corrupts the clock when syncing from scratch!
				if (event.getException() instanceof KeyExistsDatabaseException)
				{
					try
					{
						PreparedAtom preparedAtom = Modules.get(AtomStore.class).getAtom(((AtomExceptionEvent)event).getAtom().getTemporalProof().getVertexByNID(LocalSystem.getInstance().getNID()).getClock());
						if (preparedAtom != null)
						{
							final Atom existingAtom = preparedAtom.getAtom();
							atomsLog.error("Atom "+((AtomExceptionEvent)event).getAtom()+" has conflicting database slot "+AtomStore.IDType.toEUID(((KeyExistsDatabaseException)event.getException()).getKey().getData())+" with "+existingAtom);

							TemporalVertex temporalVertex = ((AtomExceptionEvent)event).getAtom().getTemporalProof().getVertexByNID(LocalSystem.getInstance().getNID());
							TemporalVertex existingTemporalVertex = existingAtom.getTemporalProof().getVertexByNID(LocalSystem.getInstance().getNID());

							if (existingTemporalVertex.getTimestamp() > temporalVertex.getTimestamp())
							{
								Modules.get(AtomStore.class).deleteAtoms(existingAtom);
								if (discoveryLog.hasLevel(Logging.DEBUG))
									atomsLog.debug("Resolved clock slot conflict by deleting existing Atom "+existingAtom);
							}
							else
							{
								((AtomExceptionEvent)event).getAtom().getTemporalProof().removeVertexByNID(LocalSystem.getInstance().getNID());
								queue(((AtomExceptionEvent)event).getAtom());
								if (discoveryLog.hasLevel(Logging.DEBUG))
									atomsLog.debug("Resolved clock slot conflict by re-witnessing Atom "+((AtomExceptionEvent)event).getAtom());
							}
						}
					}
					catch (Exception ex)
					{
						atomsLog.error("Failed to resolve clock "+AtomStore.IDType.ATOM.toEUID(((KeyExistsDatabaseException)event.getException()).getKey().getData())+" conflict involving Atom "+((AtomExceptionEvent)event).getAtom().getAID(), ex);
					}
				}
			}
			else
				AtomSync.this.committing.remove(((AtomExceptionEvent)event).getAtom().getAID());
		}
	};

	// PEER LISTENER //
	private PeerListener peerListener = new PeerListener()
	{
		@Override
		public void process(PeerEvent event)
		{
			if (event instanceof PeerDisconnectedEvent)
			{
				synchronized(AtomSync.this.syncPeers)
				{
					AtomSync.this.syncPeers.remove(event.getPeer());
				}

				synchronized(AtomSync.this.inventorySyncStates)
				{
					AtomSync.this.inventorySyncStates.remove(event.getPeer());
				}

				synchronized(AtomSync.this.checksumSyncStates)
				{
					AtomSync.this.checksumSyncStates.remove(event.getPeer());
				}
			}
		}
	};
}
