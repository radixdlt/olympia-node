package org.radix.time;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.radixdlt.common.EUID;
import com.radixdlt.common.AID;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.crypto.CryptoException;
import org.radix.exceptions.ValidationException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.utils.SystemProfiler;
import org.radix.validation.ValidatableObject;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;

@SerializerId2("tempo.temporal_proof")
public final class TemporalProof extends ValidatableObject
{
	private static final Logger log = Logging.getLogger ();

	public static final int ROOT_VERTEX_NIDS = 1024;
	public static final int BRANCH_VERTEX_NIDS = 2;

	@Override
	public short VERSION() { return 100; }

	@JsonProperty("aid")
	@DsonOutput(Output.ALL)
	private AID aid;

	private final Map<EUID, TemporalVertex> vertices = new LinkedHashMap<>();

	private final Map<EUID, TemporalVertex> owners = new LinkedHashMap<>();

	public TemporalProof()
	{
		super();
	}

	public TemporalProof(AID aid) {
		this.aid = Objects.requireNonNull(aid, "aid is required");
	}

	public TemporalProof(AID aid, Iterable<TemporalVertex> vertices) {
		this.aid = Objects.requireNonNull(aid, "aid is required");

		for (TemporalVertex vertex : vertices) {
			add_internal(vertex);
		}
	}

	public TemporalProof(List<TemporalVertex> vertices)
	{
		for (TemporalVertex vertex : vertices) {
			add_internal(vertex);
		}
	}

	public boolean add(TemporalVertex vertex, ECKeyPair nodeKey) throws ValidationException, CryptoException
	{
		long start = SystemProfiler.getInstance().begin();

		try
		{
			if (hasVertexByNID(vertex.getOwner().getUID()))
				log.warn("A vertex owned by "+vertex.getOwner().getUID()+" is already present");

			if (vertex.getPrevious().equals(EUID.ZERO) && !this.vertices.isEmpty())
				throw new ValidationException("A vertex with ZERO reference is already present");

			if (!vertex.getPrevious().equals(EUID.ZERO))
			{
				if (vertex.getNIDS().size() > TemporalProof.BRANCH_VERTEX_NIDS)
					throw new ValidationException("The vertex "+vertex+" specifies more than "+TemporalProof.BRANCH_VERTEX_NIDS+" branch vertex NIDs");

				if (!this.vertices.containsKey(vertex.getPrevious()))
					throw new ValidationException("The referenced parent vertex "+vertex.getPrevious()+" can not be found");
			}
			else
			{
				if (vertex.getNIDS().size() > TemporalProof.ROOT_VERTEX_NIDS)
					throw new ValidationException("The vertex "+vertex+" specifies more than "+TemporalProof.ROOT_VERTEX_NIDS+" branch vertex NIDs");
			}

			if (vertex.getSignature() == null)
			{
				this.reset(null);

				TemporalProof branch = getBranch(vertex, false);

				byte[] hashBuffer = new byte[Hash.BYTES * 2];
				System.arraycopy(branch.getHash().toByteArray(), 0, hashBuffer, 0, Hash. BYTES); // Wrong, need to do it on the BRANCH hash the vertex is added to!
				System.arraycopy(vertex.getHash().toByteArray(), 0, hashBuffer, Hash.BYTES, Hash.BYTES);
				byte[] hash = Hash.hash256(hashBuffer);
				vertex.setSignature(nodeKey.sign(hash));
			}

			return add_internal(vertex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("TEMPORAL_PROOF:ADD_VERTEX", start);
		}
	}

	private boolean add_internal(TemporalVertex vertex)
	{
		this.vertices.put(vertex.getHID(), vertex);
		this.owners.put(vertex.getOwner().getUID(), vertex);
		return true;
	}

	private boolean remove_internal(TemporalVertex vertex)
	{
		this.vertices.remove(vertex.getHID());
		this.owners.remove(vertex.getOwner().getUID());
		return true;
	}

	public List<TemporalVertex> getVertices()
	{
		return Collections.unmodifiableList(new ArrayList<TemporalVertex>(this.vertices.values()));
	}

	public List<TemporalVertex> getVerticesByNIDAssociations()
	{
		Set<EUID> NIDS = new HashSet<EUID>();
		List<TemporalVertex> vertices = new ArrayList<TemporalVertex>();

		for (TemporalVertex vertex : this.vertices.values())
		{
			if (vertex.getPrevious().equals(EUID.ZERO) || NIDS.contains(vertex.getOwner().getUID()))
			{
				NIDS.addAll(vertex.getNIDS());
				vertices.add(vertex);
			}
		}

		return Collections.unmodifiableList(vertices);
	}

	public TemporalVertex getOrigin()
	{
		for (TemporalVertex vertex : this.vertices.values())
			if (vertex.getPrevious().equals(EUID.ZERO))
				return vertex;

		return null;
	}

	public TemporalVertex getVertexByNID(EUID NID)
	{
		return this.owners.get(NID);
	}

	public List<TemporalVertex> removeVertexByNID(EUID NID)
	{
		Map<EUID, TemporalVertex> removals = new LinkedHashMap<EUID, TemporalVertex>();

		for (TemporalVertex vertex : this.vertices.values())
			if (vertex.getOwner().getUID().equals(NID) || removals.containsKey(vertex.getPrevious()))
				removals.put(vertex.getHID(), vertex);

		for (TemporalVertex vertex : removals.values())
			remove_internal(vertex);

		return new ArrayList<>(removals.values());
	}

	public boolean hasVertexByNID(EUID NID)
	{
		return this.owners.containsKey(NID);
	}

	public Collection<? extends EUID> getNIDs()
	{
		Set<EUID> NIDS = new HashSet<EUID>();

		for (TemporalVertex vertex : this.vertices.values())
			NIDS.add(vertex.getOwner().getUID());

		return Collections.unmodifiableSet(NIDS);
	}

	public void merge(TemporalProof other) throws ValidationException
	{
		if (other == null)
			return;

		if (!aid.equals(other.aid))
			throw new ValidationException("TemporalProofs reference different objects");

		if (other.isEmpty())
			return;

		TemporalVertex otherOrigin = other.getOrigin();
		if (otherOrigin == null || !otherOrigin.equals(getOrigin()))
			throw new TemporalProofCommonOriginException(this, other);

		for (TemporalVertex vertex : other.vertices.values())
		{
			if (this.vertices.containsKey(vertex.getHID()))
				continue;

			if (this.vertices.containsKey(vertex.getPrevious()))
				add_internal(vertex);
			else
				throw new ValidationException("TemporalProofs do not converge");
		}

		reset(null);
	}

	public TemporalProof discardBrokenBranches()
	{
		Set<TemporalVertex> vertices = new LinkedHashSet<TemporalVertex>();
		Set<TemporalProof> branches = getBranches();
		Iterator<TemporalProof> branchesIterator = branches.iterator();

		while (branchesIterator.hasNext())
		{
			TemporalVertex previousVertex = null;
			TemporalProof branch = branchesIterator.next();

			for (TemporalVertex vertex : branch.getVertices())
			{
				if (previousVertex == null)
				{
					vertices.add(vertex);
					previousVertex = vertex;	// Origin vertex
				}
				else
				{
					if (previousVertex.getNIDS().contains(vertex.getOwner().getUID()))
					{
						vertices.add(vertex);
						previousVertex = vertex;
					}
					else
						break;
				}
			}
		}

		return new TemporalProof(this.aid, vertices);
	}

	public TemporalProof getSubTemporalProof(int toLevel)
	{
		Set<TemporalVertex> vertices = new LinkedHashSet<TemporalVertex>();
		Set<TemporalProof> branches = getBranches();
		Iterator<TemporalProof> branchesIterator = branches.iterator();

		while (branchesIterator.hasNext())
		{
			TemporalProof branch = branchesIterator.next();
			int l = 0;
			for (TemporalVertex vertex : branch.getVertices())
			{
				vertices.add(vertex);
				l++;

				if (l == toLevel)
					break;
			}
		}

		return new TemporalProof(this.aid, vertices);
	}

	public int size()
	{
		return this.vertices.size();
	}

	public boolean isEmpty()
	{
		return this.vertices.isEmpty();
	}

	@Override
	public String toString()
	{
		return "HID: "+getHID()+" AID: "+getAID()+" Vertices: "+this.vertices.size();
	}

	public int getLevel(TemporalVertex temporalVertex)
	{
		TemporalProof branch = getBranch(temporalVertex, true);
		return branch.size()-1;
	}

	public TemporalProof getBranch(TemporalVertex vertex, boolean inclusive)
	{
		TemporalProof branchTemporalProof = new TemporalProof(this.aid);
		List<TemporalVertex> vertices = new ArrayList<TemporalVertex>();

		if (inclusive) {
			vertices.add(vertex);
		}

		do
		{
			if (this.vertices.containsKey(vertex.getPrevious()))
			{
				TemporalVertex prevVertex = this.vertices.get(vertex.getPrevious());
				vertices.add(prevVertex);
				vertex = prevVertex;
			}
			else
				vertex = null;
		}
		while(vertex != null);

		Collections.reverse(vertices);

		for (TemporalVertex v : vertices)
			branchTemporalProof.add_internal(v);

		return branchTemporalProof;
	}

	public TemporalProof getLongestBranch()
	{
		TemporalProof longestBranch = null;

		for (TemporalProof branch : getBranches())
			// TODO want to include mass if the branch sizes are the same?
			// probably not the right place to do this if we do, so do it externally to this function
			if (longestBranch == null || branch.size() > longestBranch.size())
				longestBranch = branch;

		return longestBranch;
	}

	public Set<TemporalProof> getBranches()
	{
		Set<TemporalProof> branches = new HashSet<TemporalProof>();
		Set<TemporalVertex> tips = getBranchTips();

		for (TemporalVertex tip : tips)
			branches.add(getBranch(tip, true));

		return branches;
	}

	public Set<TemporalVertex> getBranchTips()
	{
		Set<TemporalVertex> tips = new HashSet<TemporalVertex>();

		// TODO can this be made more efficient?
		for (TemporalVertex tip : this.vertices.values())
		{
			boolean isTip = true;

			for (TemporalVertex vertex : this.vertices.values())
				if (vertex.getPrevious().equals(tip.getHID()))
				{
					isTip = false;
					break;
				}

			if (isTip)
				tips.add(tip);
		}

		return tips;
	}

	public AID getAID() {
		return this.aid;
	}

	@JsonProperty("vertices")
	@DsonOutput(Output.ALL)
	private List<TemporalVertex> getJsonVertices() {
		return Lists.newArrayList(vertices.values());
	}

	@JsonProperty("vertices")
	private void setJsonVertices(List<TemporalVertex> vertices) {
		for (TemporalVertex vertex : vertices) {
			add_internal(vertex);
		}
	}
}
