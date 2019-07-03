package org.radix.atoms.particles.conflict;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.radixdlt.atoms.Atom;
import org.radix.atoms.AtomStore;
import org.radix.atoms.events.ParticleEvent;
import org.radix.atoms.events.ParticleListener;
import org.radix.atoms.particles.conflict.events.ConflictConcurrentEvent;
import org.radix.atoms.particles.conflict.events.ConflictDetectedEvent;
import org.radix.atoms.particles.conflict.events.ConflictFailedEvent;
import org.radix.atoms.particles.conflict.events.ConflictResolvedEvent;
import org.radix.atoms.particles.conflict.events.ConflictUpdatedEvent;
import org.radix.atoms.particles.conflict.messages.ConflictAssistRequestMessage;
import org.radix.atoms.particles.conflict.messages.ConflictAssistResponseMessage;
import org.radix.common.Criticality;
import com.radixdlt.common.EUID;
import com.radixdlt.common.AID;
import com.radixdlt.utils.Offset;
import org.radix.common.executors.Executable;
import org.radix.common.executors.Executor;
import org.radix.common.executors.ScheduledExecutable;
import org.radix.database.exceptions.DatabaseException;
import org.radix.events.Events;
import org.radix.exceptions.QueueFullException;
import org.radix.exceptions.ValidationException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.mass.NodeMass;
import org.radix.mass.NodeMassStore;
import org.radix.modules.Modules;
import org.radix.modules.Service;
import org.radix.modules.exceptions.ModuleException;
import org.radix.network.Network;
import org.radix.network.Protocol;
import org.radix.network.messaging.MessageProcessor;
import org.radix.network.messaging.Messaging;
import org.radix.network.peers.Peer;
import org.radix.network.peers.PeerHandler;
import org.radix.network.peers.PeerHandler.PeerDomain;
import org.radix.network.peers.filters.PeerFilter;
import org.radix.properties.RuntimeProperties;
import com.radixdlt.serialization.MapHelper;
import org.radix.state.State;
import org.radix.time.TemporalProof;
import org.radix.time.TemporalProofCommonOriginException;
import org.radix.time.TemporalVertex;
import com.radixdlt.universe.Universe;
import org.radix.universe.system.RadixSystem;
import org.radix.universe.system.events.QueueFullEvent;
import org.radix.utils.MathUtils;
import org.radix.utils.SystemMetaData;
import com.radixdlt.utils.UInt384;

public class ParticleConflictHandler extends Service
{
	private static final Logger conflictsLog = Logging.getLogger("conflicts");
	private static final Logger conflictResultsLog = Logging.getLogger("conflictResults");

	private final Map<AID, EUID>					atoms = new HashMap<>();
	private final Map<EUID, ParticleConflict>		conflicts = new HashMap<>();
	private final BlockingQueue<ParticleConflict> 	conflictQueue = new LinkedBlockingQueue<>(Modules.get(RuntimeProperties.class).get("ledger.conflicts.max",  8192));

	private final Map<EUID, HashSet<EUID>>					assistRequests = new HashMap<>();
	private final Map<EUID, ArrayList<ParticleConflict>>	assistResponses = new HashMap<>();

	private Executable 	conflictProcessor = null;
	private Thread		conflictProcessorThread = null;

	private class AssistTimeout extends ScheduledExecutable
	{
		private final ParticleConflict conflict;

		AssistTimeout(final ParticleConflict conflict, long delay, TimeUnit unit)
		{
			super(delay, 0, unit);

			this.conflict = conflict;
		}

		@Override
		public void execute()
		{
			try
			{
				synchronized(this.conflict)
				{
					if (this.conflict.getState().in(State.PENDING))
					{
						synchronized(ParticleConflictHandler.this.assistResponses)
						{
							if (ParticleConflictHandler.this.assistResponses.containsKey(this.conflict.getUID()))
							{
								Iterator<ParticleConflict> assistResponsesIterator = ParticleConflictHandler.this.assistResponses.get(this.conflict.getUID()).iterator();

								while(assistResponsesIterator.hasNext())
								{
									ParticleConflict assistResponse = assistResponsesIterator.next();
									assistResponsesIterator.remove();

									synchronized(ParticleConflictHandler.this.atoms)
									{
										Set<Atom> concurrentAtoms = getConcurrentAtoms(assistResponse);

										for (Atom concurrentAtom : concurrentAtoms)
										{
											assistResponse.removeAtom(concurrentAtom);
											conflictsLog.debug(conflict.getUID()+": Removed concurrent Atom "+concurrentAtom.getAID()+" from conflict assist response");
										}
									}

									if (!assistResponse.getAtoms().isEmpty())
									{
										// FIXME this is a bodge due to the fact that selected origin nodes are not currently encoded into the atom via fees
										// without it, identical atoms can be created with different origins which breaks the standard merge in the event of conflict
										// this.conflict.merge(assistResponse);

										Map<AID, Atom> atoms = new HashMap<>();
										for (Atom atom : this.conflict.getAtoms())
											atoms.put(atom.getAID(), atom);

										for (Atom atom : assistResponse.getAtoms())
										{
											if (!atoms.containsKey(atom.getAID()))
												this.conflict.addAtom(atom);
											else
											{
												try
												{
													atoms.get(atom.getAID()).getTemporalProof().merge(atom.getTemporalProof());
												}
												catch (TemporalProofCommonOriginException tpcoex)
												{
													conflictsLog.error(tpcoex);

													// FIXME this is WAY more complicated than originally expected to implement a proper fix.
													// For now a simple patch for 1M TPS to not use mass, only NID distance from 0, as mass presents an unsolvable (currently) information visibility issue.
													//
													// It should be sufficient for our immediate purpose, if not I can revisit it after our shake down tests.
													if (tpcoex.getInvoker().getOrigin().getOwner().getUID().getShard() < tpcoex.getConflictor().getOrigin().getOwner().getUID().getShard())
														atoms.get(atom.getAID()).setTemporalProof(tpcoex.getInvoker());
												}
											}
										}
									}
								}

								ParticleConflictHandler.this.assistResponses.remove(this.conflict.getUID());
								Events.getInstance().broadcastWithException(new ConflictUpdatedEvent(this.conflict));
							}
						}

						if (conflictQueue.offer(this.conflict))
							this.conflict.setState(new State(State.RESOLVING));
					}
				}
			}
			catch (Throwable t)
			{
				conflictsLog.error(this.conflict.getUID()+": Unable to process assists for conflict", t);
				Events.getInstance().broadcast(new ConflictFailedEvent(this.conflict));
			}
		}
	}

	public ParticleConflictHandler()
	{
		super();
	}

	@Override
	public void start_impl() throws ModuleException
	{
		register("conflict.assist.request", new MessageProcessor<ConflictAssistRequestMessage>()
		{
			@Override
			public void process(ConflictAssistRequestMessage conflictAssistMessage, Peer peer)
			{
				try
				{
					conflictsLog.debug(conflictAssistMessage.getUID()+": Conflict assist request from "+peer);

					Atom committedAtom = Modules.get(AtomStore.class).getAtomContaining(conflictAssistMessage.getSpunParticle().getParticle(), conflictAssistMessage.getSpunParticle().getSpin());

					ParticleConflict existingConflict;
					synchronized(ParticleConflictHandler.this.conflicts)
					{
						existingConflict = ParticleConflictHandler.this.conflicts.getOrDefault(conflictAssistMessage.getUID(), Modules.get(ParticleConflictStore.class).getConflict(conflictAssistMessage.getUID()));
					}

					// FIXME needs to be improved due to 65kb UDP limit.  The following only mitigates it temporarily.  Large atoms may still cause oversized UDP packets.
					Set<Atom> assistAtoms = new HashSet<Atom>();

					if (committedAtom != null)
						assistAtoms.add(committedAtom);

					if (existingConflict != null)
					{
						synchronized(existingConflict)
						{
							assistAtoms.addAll(existingConflict.getAtoms());
						}
					}

					if (assistAtoms.isEmpty() == false)
					{
						for (Atom assistAtom : assistAtoms)
						{
							ParticleConflict assistConflict = new ParticleConflict(conflictAssistMessage.getSpunParticle(), Collections.singleton(assistAtom));
							Messaging.getInstance().send(new ConflictAssistResponseMessage(assistConflict), peer);
						}
					}
				}
				catch (Exception ex)
				{
					conflictsLog.error("conflict.assist.request "+peer, ex);
				}
			}
		});

		register("conflict.assist.response", new MessageProcessor<ConflictAssistResponseMessage>()
		{
			@Override
			public void process(ConflictAssistResponseMessage conflictAssistResponseMessage, Peer peer)
			{
				try
				{
					// TODO want to disconnect peer on these errors?
					synchronized(ParticleConflictHandler.this.conflicts)
					{
						if (ParticleConflictHandler.this.conflicts.containsKey(conflictAssistResponseMessage.getConflict().getUID()) == false)
						{
							conflictsLog.debug(conflictAssistResponseMessage.getConflict().getUID()+": Got conflict assist response from "+peer+" but conflict does not exist");
							return;
						}
					}

					synchronized(ParticleConflictHandler.this.assistRequests)
					{
						if (ParticleConflictHandler.this.assistRequests.containsKey(conflictAssistResponseMessage.getConflict().getUID()) == false)
						{
							conflictsLog.debug(conflictAssistResponseMessage.getConflict().getUID()+": Got conflict assist response from "+peer+" but no conflict assist requests have been sent");
							return;
						}

						if (ParticleConflictHandler.this.assistRequests.get(conflictAssistResponseMessage.getConflict().getUID()).contains(peer.getSystem().getNID()) == false)
						{
							conflictsLog.debug(conflictAssistResponseMessage.getConflict().getUID()+": Got conflict assist response from "+peer+" but no conflict assist requests was sent");
							return;
						}
					}

					synchronized(ParticleConflictHandler.this.assistResponses)
					{
						if (ParticleConflictHandler.this.assistResponses.containsKey(conflictAssistResponseMessage.getConflict().getUID()) == false)
						{
							conflictsLog.debug(conflictAssistResponseMessage.getConflict().getUID()+": Got conflict assist response from "+peer+" but not accepting conflict assist responses");
							return;
						}

						conflictsLog.debug(conflictAssistResponseMessage.getConflict().getUID()+": Conflict assist response containing "+conflictAssistResponseMessage.getConflict().getAtoms()+" from "+peer);

						ParticleConflictHandler.this.assistResponses.get(conflictAssistResponseMessage.getConflict().getUID()).add(conflictAssistResponseMessage.getConflict());
					}
				}
				catch (Throwable t)
				{
					conflictsLog.error("conflict.assist.response "+peer, t);
				}
			}
		});

		Events.getInstance().register(ParticleEvent.class, this.particleListener);

		this.conflictProcessor = new Executable()
		{
			@Override
			public void execute()
			{
				while (!isTerminated())
				{
					ParticleConflict conflict = null;

					try {
						conflict = conflictQueue.poll(1, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						// Exit if interrupted
						Thread.currentThread().interrupt();
						break;
					}

					if (conflict == null)
						continue;

					synchronized(conflict)
					{
						try
						{
							conflictsLog.debug(conflict.getUID()+": Processing conflict "+conflict.toString());

							if (conflict.getState().in(State.PENDING))
							{
								requestAssist(conflict);
							}
							else if (conflict.getState().in(State.RESOLVING))
							{
								resolveCausally(conflict);
								resolveMassfully(conflict);
							}
							else
								throw new IllegalStateException(conflict.getUID()+": State "+conflict.getState()+" is invalid!");

							apply(conflict);
						}
						catch (Throwable t)
						{
							conflictsLog.error(conflict.getUID()+": Processing of conflict failed", t);
							Events.getInstance().broadcast(new ConflictFailedEvent(conflict));
						}
					}
				}
			}
		};

		conflictProcessorThread = new Thread(conflictProcessor);
		conflictProcessorThread.setDaemon(true);
		conflictProcessorThread.setName("Particle Conflict Processor");
		conflictProcessorThread.start();
	}

	@Override
	public void stop_impl()
	{
		conflictProcessor.terminate(true);
		conflictProcessorThread = null;
		Events.getInstance().deregister(ParticleEvent.class, this.particleListener);
	}

	@Override
	public String getName()
	{
		return "Particle Conflict Handler";
	}

	@Override
	public Map<String, Object> getMetaData()
	{
		return MapHelper.mapOf(
			"atoms", this.atoms.size(),
			"conflicts", this.conflicts.size());
	}

	public int numConflictingAtoms()
	{
		return this.atoms.size();
	}

	public int numResolvingConflicts()
	{
		return this.conflicts.size();
	}

	public int numQueuedConflicts()
	{
		return this.conflictQueue.size();
	}

	private synchronized void queue(ParticleConflict conflict)
	{
		try
		{
			conflictsLog.debug(conflict.getUID()+": Queuing particle "+conflict.getSpunParticle().getParticle().getHID()+" conflict involving "+conflict.getAtoms().stream().map(a -> a.toString()).collect(Collectors.joining(",")));

			synchronized(conflict)
			{
				Set<Atom> concurrentAtoms = getConcurrentAtoms(conflict);

				if (!concurrentAtoms.isEmpty())
				{
					conflictsLog.debug(conflict.getUID()+": Concurrent conflicts detected involving Atoms "+concurrentAtoms.toString());
					Events.getInstance().broadcast(new ConflictConcurrentEvent(conflict, concurrentAtoms));
					return;
				}

				validateConflict(conflict);

				ParticleConflict existingConflict = null;

				synchronized(ParticleConflictHandler.this.conflicts)
				{
					existingConflict = ParticleConflictHandler.this.conflicts.putIfAbsent(conflict.getUID(), conflict);
				}

				// Prepare conflict for resolution //
				if (existingConflict == null)
				{
					try
					{
						conflictsLog.debug(conflict.getUID()+": Preparing conflict "+conflict);

						ParticleConflict resolvedConflict = Modules.get(ParticleConflictStore.class).getConflict(conflict.getUID());

						if (resolvedConflict != null)
						{
							conflictsLog.debug(conflict.getUID()+": Merging existing resolved conflict "+resolvedConflict);
							conflict.merge(resolvedConflict);
						}

						Modules.ifAvailable(SystemMetaData.class, a -> a.increment("ledger.faults.tears"));
						Modules.get(ParticleConflictStore.class).storeConflict(conflict);
						Events.getInstance().broadcast(new ConflictUpdatedEvent(conflict));
					}
					catch (Throwable t)
					{
						conflictsLog.error(conflict.getUID()+": Preparation of conflict failed", t);

						synchronized(ParticleConflictHandler.this.conflicts)
						{
							ParticleConflictHandler.this.conflicts.remove(conflict.getUID());
						}

						throw t;
					}
				}
				// Merge conflict with existing conflict //
				else if (existingConflict != conflict)
				{
					try
					{
						synchronized(existingConflict)
						{
							if (existingConflict.getState().in(State.PENDING) == false)
							{
								conflictsLog.debug(conflict.getUID()+": Conflict is already queued but is not in PENDING state");
								Set<Atom> commonAtoms = getCommonAtoms(conflict, existingConflict);
								conflictsLog.debug(conflict.getUID()+": Discovered common Atoms "+commonAtoms.toString());
								Events.getInstance().broadcast(new ConflictConcurrentEvent(conflict, commonAtoms));
								return;
							}
							else
							{
								conflictsLog.debug(conflict.getUID()+": Conflict is already queued, merging");
								existingConflict.merge(conflict);
								Modules.get(ParticleConflictStore.class).storeConflict(existingConflict);
								Events.getInstance().broadcast(new ConflictUpdatedEvent(existingConflict));
								return;
							}
						}
					}
					catch (Throwable t)
					{
						conflictsLog.error(conflict.getUID()+": Merging of conflicts failed", t);
						throw t;
					}
				}

				add(conflict);

				if (this.conflictQueue.offer(conflict))
					conflictsLog.debug(conflict.getUID()+": Conflict queued");
				else
				{
					remove(conflict);
					Events.getInstance().broadcast(new QueueFullEvent(this.conflictQueue));
					throw new QueueFullException(conflict.getUID()+": Conflict queue has reached capacity of "+Modules.get(RuntimeProperties.class).get("ledger.conflicts.max",  8192));
				}
			}
		}
		catch (Throwable t)
		{
			conflictsLog.error(conflict.getUID()+": Queueing of conflict failed", t);
			Events.getInstance().broadcast(new ConflictFailedEvent(conflict));
		}
	}

	private void validateConflict(ParticleConflict conflict) throws ValidationException {
		boolean haveAtomsWithTemporalProofs = false;
		for (Atom atom : conflict.getAtoms()) {
			if (!atom.getTemporalProof().isEmpty()) {
				haveAtomsWithTemporalProofs = true;
				break;
			}
		}
		if (!haveAtomsWithTemporalProofs) {
			throw new ValidationException(this.getUID()+":  Conflict is void of Atoms with a TemporalProof or mass", Criticality.FATAL);
		}
	}

	private boolean requestAssist(ParticleConflict conflict) throws DatabaseException
	{
		synchronized(conflict)
		{
			synchronized(this.assistRequests)
			{
				if (!this.assistRequests.containsKey(conflict.getUID()))
					this.assistRequests.put(conflict.getUID(), new HashSet<EUID>());

				List<Peer> livePeers = Modules.get(PeerHandler.class).getPeers(PeerDomain.NETWORK, PeerFilter.getInstance());
				if (livePeers.isEmpty() == true)
				{
					conflictsLog.debug(conflict.getUID()+": No peer connections are currently available");
					return false;
				}

				List<EUID> assistNIDs = livePeers.stream().collect(Collectors.collectingAndThen(Collectors.toList(), collected -> {
																																	Collections.shuffle(collected);
																																	return collected.stream();
																																  })).
														   map(Peer::getSystem).
														   map(RadixSystem::getNID).
														   limit(Math.min(2, MathUtils.log2(livePeers.size()))).
														   collect(Collectors.toList());

				assistNIDs.removeAll(this.assistRequests.get(conflict.getUID()));
				if (assistNIDs.isEmpty() == true)
				{
					conflictsLog.debug(conflict.getUID()+": No nodes remaining to ask for assist");
					return false;
				}

				conflictsLog.debug(conflict.getUID()+": Collected "+assistNIDs.size()+" NIDs for assist request");

				List<Peer> assistPeers = Modules.get(PeerHandler.class).getPeers(PeerDomain.NETWORK, assistNIDs, PeerFilter.getInstance(), PeerHandler.SHUFFLER).stream().collect(Collectors.toList());
				if (assistPeers.isEmpty() == true)
				{
					conflictsLog.debug(conflict.getUID()+": Found nodes to ask for assist, but peer connections are not available");
					return false;
				}

				conflictsLog.debug(conflict.getUID()+": Requesting assistance from "+assistPeers.stream().map(Peer::getSystem).map(RadixSystem::getNID).collect(Collectors.toSet()));

				for (Peer assistPeer : assistPeers)
				{
					try
					{
						assistPeer = Network.getInstance().connect(assistPeer.getURI(), Protocol.UDP);
						Modules.get(Messaging.class).send(new ConflictAssistRequestMessage(conflict.getSpunParticle()), assistPeer);
					}
					catch (IOException ioex)
					{
						conflictsLog.debug(conflict.getUID()+": Failed to request assistance from "+assistPeer, ioex);
					}
				}

				Modules.ifAvailable(SystemMetaData.class, a -> a.increment("ledger.faults.assists"));
				this.assistRequests.get(conflict.getUID()).addAll(assistPeers.stream().map(Peer::getSystem).map(RadixSystem::getNID).collect(Collectors.toSet()));
				conflict.setState(new State(State.PENDING));
			}

			synchronized(this.assistResponses)
			{
				this.assistResponses.put(conflict.getUID(), new ArrayList<ParticleConflict>());
			}

			return true;
		}
	}

	private void resolveCausally(ParticleConflict conflict)
	{
		// TODO need to re-implement
	}

	private void resolveMassfully(final ParticleConflict conflict)  {
		// TODO base masses around invoker timestamp not invoker?  or something else entirely? (combination, mean, average)

		Map<AID, UInt384> masses = calculateMasses(conflict);
		if (conflictsLog.hasLevel(Logging.DEBUG)) {
			for (Map.Entry<AID, UInt384> entry : masses.entrySet()) {
				AID aid = entry.getKey();
				conflictsLog.debug(conflict.getUID() + " Atom: " + aid + "/" + conflict.getAtom(aid).getHash() + " Mass: " + entry.getValue());
			}
		}

		// Check for equal masses on all conflicting Atoms
		// TODO needs handling MUCH better than currently (testing for PoC)
		boolean equalMasses = false;

		if (masses.size() > 1)
		{
			equalMasses = true;
			boolean previousMassFound = false;
			UInt384 previousMass = UInt384.ZERO;
			for (UInt384 mass : masses.values())
			{
				if (!previousMassFound) {
					previousMass = mass;
					previousMassFound = true;
				} else if (!mass.equals(previousMass)) {
					equalMasses = false;
					break;
				}
			}
		}

		if (equalMasses == true)
		{
			final Comparator<AID> aidComparator = AID.lexicalComparator();
			for (Atom atom : conflict.getAtoms()) {
				if (conflict.getResult() == null || aidComparator.compare(atom.getAID(), conflict.getResult()) < 0)
					conflict.setResult(atom.getAID());
			}

			conflictsLog.debug(conflict.getUID()+": Could not resolve result, masses are equal, selected lowest Atom HID "+conflict.getResult());
		}
		else
		{
			for (Atom atom : conflict.getAtoms())
			{
				if ((conflict.getResult() == null && masses.containsKey(atom.getAID())) ||
					(conflict.getResult() != null && masses.containsKey(atom.getAID()) && masses.get(atom.getAID()).compareTo(masses.get(conflict.getResult())) > 0))
					conflict.setResult(atom.getAID());
			}

			conflictsLog.debug(conflict.getUID()+":  Result is "+conflict.getResult());
		}
	}

	private Map<AID, UInt384> calculateMasses(ParticleConflict conflict) {
		int planck = Modules.get(Universe.class).toPlanck(conflict.getTimestamp(), Offset.NONE);

		Map<AID, UInt384> masses = new HashMap<>();
/*		Map<EUID, Pair<Long, TemporalProof>> assigned = new HashMap<EUID, Pair<Long, TemporalProof>>();
		Map<EUID, TemporalProof> temporalProofs = new HashMap<EUID, TemporalProof>();

		for (Atom atom : conflict.getAtoms())
		{
			if (atom.getTemporalProof().isEmpty())
			{
				conflictsLog.debug(conflict.getUID()+": Atom "+atom.getAID()+" TemporalProof is empty");
				continue;
			}

//			int toLevel = Math.max(1, MathUtils.log2(liveNIDS.size()) - MathUtils.log2(NodeGroupTable.MIN_GROUP_SIZE>>1));
//			System.out.println(conflict.getUID()+": Atom "+atom.getAID()+" TemporalProof expected levels: "+(Math.ceil(MathUtils.log2(liveNIDS.size())) - MathUtils.log2(NodeGroupTable.MIN_GROUP_SIZE>>1))+" TemporalProof actual levels: "+atom.getTemporalProof().getLongestBranch().size()+ " Trimmed To level: "+toLevel);
//			TemporalProof temporalProof = atom.getTemporalProof().discardBrokenBranches(); //getSubTemporalProof(toLevel);
//			explicits.addAll(temporalProof.getNIDs());
			temporalProofs.put(atom.getAID(), atom.getTemporalProof());
		}*/

		Map<EUID, TemporalProof> nids = new HashMap<>();
		for (Atom atom : conflict.getAtoms())
		{
			if (atom.getTemporalProof().isEmpty())
				continue;

			for (TemporalVertex temporalVertex : atom.getTemporalProof().getVerticesByNIDAssociations())
			{
				EUID nid = temporalVertex.getOwner().getUID();
				if (!nids.containsKey(nid) || nids.get(nid).getVertexByNID(temporalVertex.getOwner().getUID()).getClock() > temporalVertex.getClock()) {
					nids.put(nid, atom.getTemporalProof());
				}
			}
		}

		// NON-DELEGATED MASS //
		for (Map.Entry<EUID, TemporalProof> entry : nids.entrySet())
		{
			EUID nid = entry.getKey();
			AID aid = entry.getValue().getAID();
			NodeMass vertexMass = Modules.get(NodeMassStore.class).getNodeMass(planck, nid);
			masses.put(aid, masses.getOrDefault(aid, UInt384.ZERO).add(vertexMass.getMass()));
			conflictsLog.debug(conflict.getUID() + ": Assigning " + nid + " with " + vertexMass + " to " + aid);
		}

		// DELEGATED MASS //
/*		List<EUID> delegated = new ArrayList<EUID>();
		for (EUID NID : NIDS.keySet())
		{
			// TODO is a NodeAddressGroupTable accurate enough or do we need to extract this information from the TP verts
			// TODO dos using the XOR distance pose a security threat where dishonest nodes can "eclipse" my delegate set?
			NodeAddressGroupTable nodeAddressGroupTable = Modules.get(RoutingHandler.class).getNodeAddressGroupTable(NID, planck);

			int requiredDelegates = (int) Math.ceil(Math.sqrt(nodeAddressGroupTable.size()));
			delegated.clear();

			for (int group : nodeAddressGroupTable.getGroups(true))
			{
				if (group == nodeAddressGroupTable.getGroup(nodeAddressGroupTable.getOrigin()))
					continue;

				delegated.addAll(nodeAddressGroupTable.getGroup(group));

				if (delegated.size() >= requiredDelegates)
					break;
			}

			Collections.sort(delegated, new Routing.NIDDistanceComparator(NID));
			delegated = delegated.subList(0, Math.min(delegated.size(), requiredDelegates));
			conflictsLog.debug(conflict.getUID()+": Delegates for NID "+NID+" @ "+conflict.getTimestamp()+"/"+planck+" = "+delegated);
			// Enable to use explicit votes over delegated votes
			delegated.removeAll(NIDS.keySet());
			delegated.add(NID);

			for (EUID delegate : delegated)
			{
				NodeMass delegateMass = Modules.get(NodeMassStore.class).getNodeMassTo(delegate, planck);
				NodeMass vertexMass = Modules.get(NodeMassStore.class).getNodeMassTo(NID, planck);

				if (assigned.containsKey(delegate) && assigned.get(delegate).getValue0() >= vertexMass.getMass())
					continue;
				else if (assigned.containsKey(delegate) && assigned.get(delegate).getValue0() < vertexMass.getMass())
					masses.put(assigned.get(delegate).getValue1().getAID(), (long) (masses.getOrDefault(assigned.get(delegate).getValue1().getAID(), 0l) - delegateMass.getMass()));

				assigned.put(delegate, new Pair<>(vertexMass.getMass(), NIDS.get(NID)));
				masses.put(NIDS.get(NID).getAID(), (long) (masses.getOrDefault(NIDS.get(NID).getAID(), 0l) + delegateMass.getMass()));

				conflictsLog.debug(conflict.getUID()+": Assigning "+delegateMass+" to "+NIDS.get(NID).getAID()+" via "+vertexMass);
			}
		}*/

		return masses;
	}

	private void apply(final ParticleConflict conflict) throws DatabaseException
	{
		if (conflict.getState().in(State.RESOLVED))
		{
			if (conflict.getResult() == null)
				throw new IllegalStateException(conflict.getUID()+": Conflict is not resolved");

			conflictsLog.debug(conflict.getUID()+":  Conflict result is "+conflict.getResult());
			conflictResultsLog.debug(conflict.getUID()+":  Conflict result is "+conflict.getResult());
			Events.getInstance().broadcast(new ConflictResolvedEvent(conflict));
			Modules.get(ParticleConflictStore.class).storeConflict(conflict);
		}
		else if (conflict.getState().in(State.FAILED))
		{
			conflictsLog.debug(conflict.getUID()+":  Conflict failed");
			conflictResultsLog.debug(conflict.getUID()+":  Conflict failed");
			Events.getInstance().broadcast(new ConflictFailedEvent(conflict));
		}
		else if (conflict.getState().in(State.PENDING))
		{
			Executor.getInstance().schedule(new AssistTimeout(conflict, 1, TimeUnit.SECONDS));
			conflictsLog.debug(conflict.getUID()+":  Conflict pending");
		}
	}

	private void add(ParticleConflict conflict)
	{
		synchronized(conflict)
		{
			synchronized(this.atoms)
			{
				for (Atom atom : conflict.getAtoms())
				{
					if (this.atoms.containsKey(atom.getAID()) && !this.atoms.get(atom.getAID()).equals(conflict.getUID()))
						throw new IllegalStateException("Atom "+atom.getAID()+" is already associated with conflict "+this.atoms.get(atom.getAID()));

					this.atoms.put(atom.getAID(), conflict.getUID());
				}
			}
		}

		synchronized(this.conflicts)
		{
			this.conflicts.put(conflict.getUID(), conflict);
		}
	}

	private void remove(ParticleConflict conflict)
	{
		synchronized(this.conflicts)
		{
			this.conflicts.remove(conflict.getUID());
		}

		synchronized(this.assistRequests)
		{
			this.assistRequests.remove(conflict.getUID());
		}

		synchronized(this.assistResponses)
		{
			this.assistResponses.remove(conflict.getUID());
		}

		synchronized(conflict)
		{
			synchronized(this.atoms)
			{
				for (Atom atom : conflict.getAtoms())
					this.atoms.remove(atom.getAID());
			}
		}
	}

	private Set<Atom> getCommonAtoms(ParticleConflict reference, ParticleConflict conflict)
	{
		Set<Atom> commonAtoms = new HashSet<>();

		for (Atom referenceAtom : reference.getAtoms())
		{
			if (conflict.hasAtom(referenceAtom.getAID()))
				commonAtoms.add(referenceAtom);
		}

		return commonAtoms;
	}

	private Set<Atom> getConcurrentAtoms(ParticleConflict conflict)
	{
		Set<Atom> concurrentAtoms = new HashSet<>();

		synchronized(this.atoms)
		{
			for (Atom atom : conflict.getAtoms())
				if (this.atoms.containsKey(atom.getAID()) &&
					!this.atoms.get(atom.getAID()).equals(conflict.getUID()))
					concurrentAtoms.add(atom);
		}

		return concurrentAtoms;
	}

	// PARTICLE LISTENER //
	private ParticleListener particleListener = new ParticleListener()
	{
		@Override
		public void process(ParticleEvent event)
		{
			if (event instanceof ConflictDetectedEvent)
				queue(((ConflictDetectedEvent)event).getConflict());

			if (event instanceof ConflictFailedEvent)
			{
				Modules.ifAvailable(SystemMetaData.class, a -> a.increment("ledger.faults.failed"));
				remove(((ConflictFailedEvent)event).getConflict());
			}

			if (event instanceof ConflictResolvedEvent)
			{
				Modules.ifAvailable(SystemMetaData.class, a -> a.increment("ledger.faults.stitched"));
				remove(((ConflictResolvedEvent)event).getConflict());
			}
		}
	};
}
