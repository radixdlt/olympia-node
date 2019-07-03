package org.radix.integration.mass;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.radixdlt.common.AID;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.radix.Radix;
import com.radixdlt.common.EUID;
import com.radixdlt.common.Pair;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.crypto.CryptoException;
import org.radix.exceptions.ValidationException;
import org.radix.integration.RadixTest;
import org.radix.modules.Modules;
import org.radix.routing.NodeAddressGroupTable;
import org.radix.routing.NodeGroupTable;
import org.radix.shards.ShardSpace;
import org.radix.time.TemporalProof;
import org.radix.time.TemporalVertex;
import org.radix.universe.system.LocalSystem;
import org.radix.universe.system.RadixSystem;
import org.radix.utils.MathUtils;

public class DelegatedMassConflictTest extends RadixTest
{
	private int NUM_NODES = 8192;

	private List<LocalSystem> nodes;
	private Map<EUID, Long> mass;
	private Map<EUID, Set<EUID>> delegated;

	private Map<AID, TemporalProof> events;
	private Map<EUID, TemporalProof> branches;
	private Map<EUID, Conflict> conflicts;

	private static class Conflict
	{
		private Set<TemporalProof> events;

		public Conflict(Collection<TemporalProof> events)
		{
			this.events = new HashSet<>(events);
		}

		public Set<TemporalProof> getEvents()
		{
			return this.events;
		}

		public TemporalProof getEvent(AID event)
		{
			for (TemporalProof temporalProof : events)
				if (temporalProof.getAID().equals(event))
					return temporalProof;

			return null;
		}
	}

	@Before
	public void setup() throws CryptoException, SecurityException, IllegalArgumentException {
		this.nodes = new ArrayList<>();
		this.mass = new HashMap<>();
		this.delegated = new HashMap<>();
		this.events = new HashMap<>();
		this.branches = new HashMap<>();
		this.conflicts = new HashMap<>();

		int systemMass = 0;

		// Set up nodes //
		for (int s = 0 ; s < NUM_NODES ; s++)
		{
			LocalSystem system = new LocalSystem(new ECKeyPair(), Radix.AGENT, Radix.AGENT_VERSION, Radix.PROTOCOL_VERSION, ShardSpace.SHARD_CHUNK_RANGE, 1234);
			this.nodes.add(system);
			long mass = (long) Math.sqrt(Math.abs(Modules.get(SecureRandom.class).nextInt(65536)));
			this.mass.put(system.getNID(), mass);
			systemMass += mass;
		}

		System.out.println("System Mass: "+systemMass);

		// Create delegates //
		int delegatesPerNode = (int) Math.sqrt(NUM_NODES);// / (1<<((MathUtils.log2(NUM_NODES)/2)+1));
		System.out.println("Num Nodes: "+NUM_NODES+" Gravitons per Node: "+delegatesPerNode);

		Collections.shuffle(this.nodes);
		for (RadixSystem system : this.nodes)
		{
			Set<EUID> delegated = new HashSet<>();

			for (int d = 0 ; d < delegatesPerNode ; d++)
			{
				EUID delegate = null;

				while (delegate == null || delegate.equals(system.getNID()))
					delegate = this.nodes.get(Math.abs(Modules.get(SecureRandom.class).nextInt(NUM_NODES))).getNID();

				delegated.add(delegate);
			}

			this.delegated.put(system.getNID(), delegated);
		}
	}

	private void setupConflicts(int numConflicts) throws InterruptedException
	{
		this.conflicts.clear();
		this.branches.clear();
		this.events.clear();

		final Map<EUID, LocalSystem> availableNodes = new HashMap<>(NUM_NODES);
		for (LocalSystem node : this.nodes)
			availableNodes.put(node.getNID(), node);

		ExecutorService executor = Executors.newFixedThreadPool(numConflicts);
		CountDownLatch conflictLatch = new CountDownLatch(numConflicts);
		for (int c = 0 ; c < numConflicts ; c++)
		{
			executor.execute(new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						AID event = randomAID();
						LocalSystem origin = DelegatedMassConflictTest.this.nodes.get(Math.abs(Modules.get(SecureRandom.class).nextInt(NUM_NODES)));
						NodeAddressGroupTable nodeAddressGroupTable = new NodeAddressGroupTable(origin.getNID(), DelegatedMassConflictTest.this.nodes.stream().map(
							RadixSystem::getNID).collect(Collectors.toSet()));
						TemporalProof temporalProof = new TemporalProof(AID.from(event.getBytes()));

						Map<EUID, TemporalVertex> levelSet = new HashMap<>();
						Map<EUID, TemporalVertex> nextLevelSet = new HashMap<>();

						do
						{
							if (levelSet.isEmpty())
							{
								synchronized(availableNodes)
								{
									origin.update(event, System.currentTimeMillis());
									availableNodes.remove(origin.getNID());

									List<EUID> gossipNIDS = nodeAddressGroupTable.getNext(origin.getNID());
									TemporalVertex temporalVertex = new TemporalVertex(origin.getKey(), origin.getClock().get(), System.currentTimeMillis(), Hash.ZERO_HASH, EUID.ZERO, gossipNIDS);
									temporalProof.add(temporalVertex, origin.getKeyPair());
									branches.put(origin.getNID(), temporalProof.getBranch(temporalVertex, true));

									for (EUID gossipNID : gossipNIDS)
										nextLevelSet.put(gossipNID, temporalVertex);
								}
							}
							else
							{
								for (EUID NID : levelSet.keySet())
								{
									if (levelSet.isEmpty())
										break;

									synchronized(availableNodes)
									{
										if (availableNodes.isEmpty() == true)
											break;

										if (availableNodes.containsKey(NID) == false)
											continue;

										LocalSystem system = availableNodes.get(NID);
										system.update(event, System.currentTimeMillis());
										availableNodes.remove(system.getNID());

										List<EUID> gossipNIDS = nodeAddressGroupTable.getNext(NID, true).stream().limit(TemporalProof.BRANCH_VERTEX_NIDS).collect(Collectors.toList());
										TemporalVertex temporalVertex = new TemporalVertex(system.getKey(), system.getClock().get(), System.currentTimeMillis(), Hash.ZERO_HASH, levelSet.get(NID).getHID(), gossipNIDS);
										temporalProof.add(temporalVertex, system.getKeyPair());
										branches.put(system.getNID(), temporalProof.getBranch(temporalVertex, true));

										for (EUID gossipNID : gossipNIDS)
											nextLevelSet.put(gossipNID, temporalVertex);
									}
								}
							}

							levelSet.clear();
							levelSet.putAll(nextLevelSet);
							nextLevelSet.clear();
						}
						while(levelSet.isEmpty() == false);

						events.put(temporalProof.getAID(), temporalProof);
					}
					catch (Exception ex)
					{
						ex.printStackTrace();
					}
					finally
					{
						conflictLatch.countDown();
					}
				}
			});
		}

		conflictLatch.await();
	}

	@Test
	@Ignore("This test frequently does not terminate in a sensible amount of time")
	public void conflict2() throws InterruptedException, ValidationException, CryptoException
	{
		Map<AID, Integer> aggregatedResult = new HashMap<>();

		setupConflicts(2);

		for (TemporalProof temporalProof : events.values())
			System.out.println("Event: "+temporalProof.getAID()+" Size: "+temporalProof.size());

		Map<AID, Long> masses = countMasses(new Conflict(this.events.values()));
		for (AID event : masses.keySet())
			System.out.println("Conflict: "+event+" Mass: "+masses.get(event));
		outputMass(masses);

		int maxRounds = 0;
		do
		{
			aggregatedResult.clear();

			System.out.println("--== ROUND COMMENCED ==--");
			for (LocalSystem system : this.nodes)
			{
				Set<TemporalProof> events = collectAndMerge(this.branches.get(system.getNID()));
				if (events.size() < 2)
				{
					AID result = null;

					if (this.branches.containsKey(system.getNID()))
						result = this.branches.get(system.getNID()).getAID();

					if (result == null)
					{
						TemporalProof event = events.iterator().next();
						List<TemporalVertex> appendTips = new ArrayList<>(event.getBranchTips());
						Collections.shuffle(appendTips);
						system.update(event.getAID(), System.currentTimeMillis());
						TemporalVertex appendVertex = new TemporalVertex(system.getKey(), system.getClock().get(), System.currentTimeMillis(), system.getCommitment(), appendTips.get(0).getHID());
						event.add(appendVertex, system.getKeyPair());
						this.branches.put(system.getNID(), event);
						result = event.getAID();
					}

					aggregatedResult.put(result, aggregatedResult.getOrDefault(result, 0)+1);
					continue;
				}

				System.out.println("Conflict resolution at node "+system.getNID());

				this.conflicts.put(system.getNID(), new Conflict(events));

				masses = calculateMasses(this.conflicts.get(system.getNID()));
				for (AID event : masses.keySet())
					System.out.println("Conflict: "+event+" Mass: "+masses.get(event));

				long mass = 0;
				AID result = AID.ZERO;
				for (AID event : masses.keySet())
				{
					if (masses.get(event) > mass)
					{
						mass = masses.get(event);
						result = event;
					}
				}

				outputMass(masses);
				System.out.println("Conflict result: "+result);
				System.out.println("Resolved with "+this.conflicts.get(system.getNID()).getEvent(result).size()+" vertices");// from ["+this.conflicts.get(system.getNID()).getEvent(result).getVertices().stream().map(v -> v.getOwner().getUID().toString()).collect(Collectors.joining(","))+"]");
				System.out.println();

				if (this.conflicts.get(system.getNID()).getEvent(result).hasVertexByNID(system.getNID()) == false)
				{
					TemporalProof event = this.conflicts.get(system.getNID()).getEvent(result);
					List<TemporalVertex> appendTips = new ArrayList<>(event.getBranchTips());
					Collections.shuffle(appendTips);
					system.update(event.getAID(), System.currentTimeMillis());
					TemporalVertex appendVertex = new TemporalVertex(system.getKey(), system.getClock().get(), System.currentTimeMillis(), system.getCommitment(), appendTips.get(0).getHID());
					this.conflicts.get(system.getNID()).getEvent(result).add(appendVertex, system.getKeyPair());
				}

				this.branches.put(system.getNID(), this.conflicts.get(system.getNID()).getEvent(result));
				aggregatedResult.put(result, aggregatedResult.getOrDefault(result, 0)+1);
			}

			for (AID event : aggregatedResult.keySet())
				System.out.println("Conflict result: "+event+" / "+aggregatedResult.get(event));

			System.out.println("--== ROUND COMPLETED ==--");
			maxRounds++;
		}
		while(aggregatedResult.size() > 1);

		System.out.println("Completed in max "+maxRounds+" rounds");
	}

	private Map<AID, Long> countMasses(Conflict conflict)
	{
		Map<AID, Long> masses = new HashMap<>();

		for (TemporalProof temporalProof : conflict.getEvents())
		{
			long mass = 0;
			for (TemporalVertex temporalVertex : temporalProof.getVertices())
				mass += this.mass.get(temporalVertex.getOwner().getUID());
			masses.put(temporalProof.getAID(), mass);
		}

		return masses;
	}

	private Set<TemporalProof> collectAndMerge(TemporalProof branch) throws ValidationException
	{
		Map<AID, TemporalProof> events = new HashMap<>();

		if (branch != null)
			events.put(branch.getAID(), new TemporalProof(branch.getAID(), branch.getVertices()));

		Set<EUID> assisted = new HashSet<>();
		events.forEach((event, temporalProof) -> assisted.addAll(temporalProof.getNIDs()));

		int assists = 0;
		while(assists < Math.max(NodeGroupTable.DEFAULT_COLLATION, MathUtils.log2(NUM_NODES)) && assisted.size() < NUM_NODES-1)
		{
			EUID NID = this.nodes.get(Math.abs(Modules.get(SecureRandom.class).nextInt(NUM_NODES))).getNID();

			if (assisted.contains(NID))
				continue;

			TemporalProof remoteBranch = this.branches.get(NID);
			if (remoteBranch == null)
				continue;

			assists++;
			assisted.add(NID);

			// Collect ONLY the branch for the selected NID otherwise everyone ends up with the complete TP which we dont want for this test
			TemporalVertex remoteVertex = remoteBranch.getVertexByNID(NID);
			remoteBranch = remoteBranch.getBranch(remoteVertex, true);

			if (!events.containsKey(remoteBranch.getAID()))
				events.put(remoteBranch.getAID(), new TemporalProof(remoteBranch.getAID(), remoteBranch.getVertices()));
			else
				events.get(remoteBranch.getAID()).merge(remoteBranch);
		}

		return new HashSet<>(events.values());
	}

	private Map<AID, Long> calculateMasses(Conflict conflict)
	{
		Set<EUID> explicits = new HashSet<>();
		Map<AID, Long> masses = new HashMap<>();
		Map<EUID, Pair<Long, TemporalProof>> assigned = new HashMap<>();
		Set<EUID> NIDS = new HashSet<>();
		Set<TemporalProof> conflicts = new HashSet<>();

		for (TemporalProof temporalProof : conflict.getEvents())
		{
			temporalProof = temporalProof.discardBrokenBranches();//getSubTemporalProof(toLevel);
			explicits.addAll(temporalProof.getNIDs());
			conflicts.add(temporalProof);
		}

		for (TemporalProof temporalProof : conflicts)
		{
			for (TemporalVertex temporalVertex : temporalProof.getVertices())
			{
				NIDS.clear();

				if (this.delegated.containsKey(temporalVertex.getOwner().getUID()))
					NIDS.addAll(this.delegated.get(temporalVertex.getOwner().getUID()));

//				NIDS.add(temporalVertex.getOwner().getUID());

				for (EUID NID : NIDS)
				{
					long explicitMass = this.mass.get(temporalVertex.getOwner().getUID());

					if (assigned.containsKey(NID) && assigned.get(NID).getFirst() >= explicitMass)
						continue;
					else if (assigned.containsKey(NID) && assigned.get(NID).getFirst() < explicitMass)
						masses.put(assigned.get(NID).getSecond().getAID(), masses.getOrDefault(assigned.get(NID).getSecond().getAID(), 0l) - (this.mass.get(NID)));

					assigned.put(NID, new Pair<>(explicitMass, temporalProof));
					masses.put(temporalProof.getAID(), masses.getOrDefault(temporalProof.getAID(), 0l) + (this.mass.get(NID)));
				}
			}
		}

		return masses;
	}

	private void outputMass(Map<AID, Long> masses)
	{
		long mass = masses.values().stream().mapToLong(x -> x).sum();

		System.out.println("Total mass observed "+mass);
	}

	@Test
	public void checkLevelAlgorithm()
	{
		for (int n = 1 ; n < 24 ; n++)
		{
			int numNodes = 1<<n;
			int toLevel = Math.max(1, MathUtils.log2(numNodes) - MathUtils.log2(NodeGroupTable.DEFAULT_COLLATION>>1));
			System.out.println("Nodes: "+numNodes+" Level: "+toLevel);
		}
	}

	private static final Random rng = new Random();
	private static AID randomAID() {
		byte[] randomBytes = new byte[AID.BYTES];
		rng.nextBytes(randomBytes);
		return AID.from(randomBytes);
	}
}
