package test.integration;

import com.radixdlt.universe.Universe;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.radixdlt.common.AID;
import org.junit.Ignore;
import org.junit.Test;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atoms.Atom;
import com.radixdlt.atomos.RRI;
import com.radixdlt.atoms.Spin;
import org.radix.atoms.sync.AtomSync;
import com.radixdlt.common.EUID;
import com.radixdlt.common.Pair;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import org.radix.exceptions.ValidationException;
import org.radix.integration.RadixTest;
import org.radix.mass.NodeMass;
import org.radix.modules.Modules;
import org.radix.routing.RoutingTable;
import org.radix.time.TemporalProof;
import org.radix.time.TemporalProofNotValidException;
import org.radix.time.TemporalVertex;
import org.radix.time.Time;
import org.radix.universe.system.RadixSystem;
import com.radixdlt.utils.UInt384;

public class AtomMassConflictTests extends RadixTest
{
	private static final int NUM_NODES = 100000;

	private Random random = null;
	private Atom atom = null;
	private Map<EUID, RadixSystem> nodes = null;
	private Map<EUID, ECKeyPair> nodeKeys = null;
	private Map<EUID, NodeMass> nodeMasses = null;
	private Map<EUID, TemporalProof> nodeTPs = null;
	private Map<AID, RoutingTable> routingTables = null;

	int nodesCompleted = 0;
	int globalAssistsResponse = 0;
	int globalAssistsNoResponse = 0;
	int localAssistsResponse = 0;
	int localAssistsNoResponse = 0;

	@Test
	@Ignore // Test currently does not appear to finish
	public void macroMassConflict() throws Exception
	{
		initNodes();

		atom = new Atom(Time.currentTimestamp());
		atom.addParticleGroupWith(new RRIParticle(RRI.of(RadixAddress.from(Modules.get(Universe.class), new ECKeyPair().getPublicKey()), "test")), Spin.DOWN);

		System.out.println("Started gossip simulation of "+atom.getAID()+" at "+System.currentTimeMillis());
		gossip(atom.getHash(), atom.getAID());

		Map<EUID, Integer> TPNIDCounts = new LinkedHashMap<>();
		for (EUID NID : nodes.keySet())
		{
			if (nodeTPs.get(NID) == null)
				continue;

			System.out.print("NID TP:");

			TemporalProof temporalProof = nodeTPs.get(NID);
			for (TemporalVertex vertex : temporalProof.getVertices())
			{
				TPNIDCounts.put(vertex.getOwner().getUID(), TPNIDCounts.getOrDefault(vertex.getOwner().getUID(), 0)+1);
				System.out.print(" "+vertex.getOwner().getUID()+" ");
			}

			System.out.println();
		}

		for (int group : routingTables.get(atom.getAID()).getGroups(true))
		{
			for (EUID NID : routingTables.get(atom.getAID()).getGroup(group))
				System.out.println("Group "+group+" -> "+NID+": Present in "+TPNIDCounts.get(NID)+" TPs");
		}

		for (EUID NID : nodes.keySet())
		{
			nodesCompleted++;

			if (nodeTPs.get(NID) == null)
				continue;

			localAssistsResponse = 0;
			localAssistsNoResponse = 0;
			TemporalProof temporalProof = nodeTPs.get(NID);

			if (hasSuperMajority(NID, temporalProof))
				continue;

			for (EUID assistNID : routingTables.get(atom.getAID()).getGroup((routingTables.get(atom.getAID()).numGroups() - ((routingTables.get(atom.getAID()).numGroups()/2)+1)), true))
			{
				if (nodeTPs.containsKey(assistNID))
				{
					localAssistsResponse++;
					globalAssistsResponse++;

					nodeTPs.get(assistNID).merge(temporalProof);
					temporalProof.merge(nodeTPs.get(assistNID));

					if (hasSuperMajority(NID, temporalProof))
						break;
				}
				else
				{
					localAssistsNoResponse++;
					globalAssistsNoResponse++;
				}
			}
		}

		System.out.println("Finished");
	}

	private boolean hasSuperMajority(EUID NID, TemporalProof temporalProof) throws ValidationException
	{
		boolean gotSuperMajority = false;
//		for (int group : routingTables.get(atom.getAID()).getGroups(true))
		{
			List<EUID> groupNIDs = routingTables.get(atom.getAID()).getGroup((routingTables.get(atom.getAID()).numGroups() - ((routingTables.get(atom.getAID()).numGroups()/2)+1)));

//			if (temporalProof.size() < groupNIDs.size())
//				continue;

//			if (groupNIDs.size() == 1)
//				continue;

			int groupNIDsMatched = 0;
			for (EUID groupNID : groupNIDs)
				if (temporalProof.hasVertexByNID(groupNID))
					groupNIDsMatched++;

			if (groupNIDsMatched >= groupNIDs.size() * 0.666)
			{
				gotSuperMajority = true;
				System.out.println(nodesCompleted+": Discovered Group-"+(routingTables.get(atom.getAID()).numGroups() - ((routingTables.get(atom.getAID()).numGroups()/2)+1))+" with Local: "+localAssistsResponse+"/"+localAssistsNoResponse+" and Global: "+globalAssistsResponse+"/"+globalAssistsNoResponse);
//				break;
			}
		}

		if (gotSuperMajority)
		{
			List<EUID> NIDList = new ArrayList<>(nodes.keySet());
			Collections.shuffle(NIDList);

			for (EUID nextNID : NIDList.stream().limit(AtomSync.BROADCAST_LIMIT).collect(Collectors.toSet()))
				if (nodeTPs.containsKey(nextNID))
					nodeTPs.get(nextNID).merge(temporalProof);

			return true;
		}

		return false;
	}

	private void initNodes() throws Exception
	{
		random = new Random(123456789l);

		nodes = new ConcurrentHashMap<>();
		nodeKeys = new ConcurrentHashMap<>();
		nodeMasses = new ConcurrentHashMap<>();
		nodeTPs = new ConcurrentHashMap<>();
		routingTables = new ConcurrentHashMap<>();

		// FIXME: This constructor no longer exists
		Method systemNewInstanceMethod = RadixSystem.class.getDeclaredMethod("newInstance", ECPublicKey.class);
		systemNewInstanceMethod.setAccessible(true);

		Field allowUnownedUpdatesField = RadixSystem.class.getDeclaredField("ALLOW_UNOWNED_UPDATES");
		allowUnownedUpdatesField.setAccessible(true);
		Field modifiersField = Field.class.getDeclaredField("modifiers");
	    modifiersField.setAccessible(true);
	    modifiersField.setInt(allowUnownedUpdatesField, allowUnownedUpdatesField.getModifiers() & ~Modifier.FINAL);
	    allowUnownedUpdatesField.set(null, Boolean.TRUE);

		for (int i=0 ; i < NUM_NODES ; i++)
		{
			byte[] bytes = new byte[32];
			random.nextBytes(bytes);
			ECKeyPair nodeKey = new ECKeyPair(bytes);
			RadixSystem system = (RadixSystem) systemNewInstanceMethod.invoke(null, nodeKey);
			nodes.put(system.getNID(), system);
			nodeKeys.put(system.getNID(), nodeKey);
			nodeMasses.put(system.getNID(), new NodeMass(system.getNID(), UInt384.from(random.nextInt(1<<20)), 0));
		}
	}

	public void gossip(Hash hash, AID atomID) throws Exception
	{
		TemporalProof temporalProof = new TemporalProof(AID.from(hash.toByteArray()));
		EUID originNID = null;

		for (EUID NID : this.nodes.keySet())
		{
			synchronized(NID)
			{
				if (this.nodeTPs.containsKey(NID))
					continue;

				if (random.nextInt(NUM_NODES) < NUM_NODES / 10)
				{
					originNID = NID;
					break;
				}
			}
		}

		this.routingTables.put(atomID, new RoutingTable(originNID, this.nodes.keySet()));

		List<Pair<EUID, TemporalProof>>  nextNIDs = new ArrayList<>();

		for (Pair<EUID, TemporalProof> next : gossip(temporalProof, originNID))
			nextNIDs.addAll(gossip(next.getSecond(), next.getFirst()));

		while (!nextNIDs.isEmpty())
		{
			Collections.shuffle(nextNIDs);
			Pair<EUID, TemporalProof> next = nextNIDs.get(0);
			nextNIDs.remove(0);
			nextNIDs.addAll(gossip(next.getSecond(), next.getFirst()));
		}
	}

	private Set<Pair<EUID, TemporalProof>> gossip(TemporalProof temporalProof, EUID NID) throws Exception
	{
		TemporalProof thisTemporalProof = null;
		Set<Pair<EUID, TemporalProof>> nextNIDs = new HashSet<>();

		synchronized(NID)
		{
			if (this.nodeTPs.containsKey(NID))
				return nextNIDs;

			thisTemporalProof = new TemporalProof(temporalProof.getAID(), temporalProof.getVertices());
			temporalProofAppend(thisTemporalProof, NID);
			this.nodeTPs.put(NID, thisTemporalProof);

			int numNextNIDs = 8;
			if (this.routingTables.get(thisTemporalProof.getAID()).getOrigin().equals(NID))
				numNextNIDs = this.routingTables.get(thisTemporalProof.getAID()).getGroup(this.routingTables.get(thisTemporalProof.getAID()).numGroups()-2).size();

			for (EUID nextNID : routingTables.get(thisTemporalProof.getAID()).getNext(NID).stream().limit(numNextNIDs).collect(Collectors.toSet()))
				nextNIDs.add(new Pair<>(nextNID, thisTemporalProof));
		}

		return nextNIDs;
	}

	private void temporalProofAppend(TemporalProof temporalProof, EUID NID) throws Exception
	{
		TemporalVertex previousVertex = null;

		if (!temporalProof.isEmpty())
		{
			// TODO check if the origin is this node and that the atom specifies the correct origin
			Set<EUID> previousNIDs = this.routingTables.get(temporalProof.getAID()).getPrevious(NID);

			for (TemporalVertex vertex : temporalProof.getVertices())
			{
				if (previousNIDs.contains(vertex.getOwner().getUID()))
				{
					previousVertex = vertex;
					break;
				}
			}

			// TODO need to handle this better due to RoutingTable poisoning?
			if (previousVertex == null)
				throw new TemporalProofNotValidException(temporalProof);
		}

		TemporalProof branch = (previousVertex == null ? new TemporalProof(temporalProof.getAID()) : temporalProof.getBranch(previousVertex, true));
		TemporalVertex vertex = new TemporalVertex(this.nodes.get(NID).getKey(),
	 			  								   this.nodes.get(NID).getClock().get(),
	 			  								   System.currentTimeMillis(),
	 			  								   this.nodes.get(NID).getCommitment(),
	 			  								   previousVertex == null ? EUID.ZERO : previousVertex.getHID());
		branch.add(vertex, nodeKeys.get(NID));
		temporalProof.add(vertex, nodeKeys.get(NID));
	}
}
