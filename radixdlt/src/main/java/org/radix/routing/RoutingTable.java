package org.radix.routing;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.radix.collections.WireableList;
import com.radixdlt.common.EUID;
import com.radixdlt.utils.Offset;
import org.radix.containers.BasicContainer;
import org.radix.modules.Modules;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.universe.Universe;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("routing.routing_table")
public class RoutingTable extends BasicContainer
{
	public final static int MIN_GROUP_SIZE = 8;

	@Override
	public short VERSION() { return 100; }

	@JsonProperty("period")
	@DsonOutput(Output.ALL)
	private int  	period;

	@JsonProperty("origin")
	@DsonOutput(Output.ALL)
	private EUID 	origin;

	@JsonProperty("groups")
	@DsonOutput(Output.ALL)
	private final Map<Integer, WireableList<EUID>> groups = new HashMap<Integer, WireableList<EUID>>();

	private transient boolean constructing = true;

	RoutingTable() {
		// Used by JSON Serialization - should not be visible to others
	}

	public RoutingTable(EUID origin)
	{
		this(origin, null, Long.MAX_VALUE);
	}

	public RoutingTable(EUID origin, Collection<EUID> NIDS)
	{
		this(origin, NIDS, Long.MAX_VALUE);
	}

	public RoutingTable(EUID origin, Collection<EUID> NIDS, long timestamp)
	{
		this(origin, NIDS, Modules.get(Universe.class).toPlanck(timestamp, Offset.NONE));
	}

	public RoutingTable(EUID origin, Collection<EUID> NIDS, int period)
	{
		super();

		this.period = period;
		this.origin = origin;

		if (NIDS != null)
		{
			for (EUID NID : NIDS)
				if (!NID.equals(this.origin))
					add(NID);

			reorganize();

			add(this.origin);
		}

		constructing = false;
	}

	public EUID getOrigin()
	{
		return this.origin;
	}

	public int getPeriod()
	{
		return this.period;
	}

	public int numGroups()
	{
		return this.groups.size();
	}

	public Set<EUID> getNIDs()
	{
		Set<EUID> NIDs = new HashSet<EUID>();

		synchronized(this.groups)
		{
			for (List<EUID> rowNIDs : this.groups.values())
				NIDs.addAll(rowNIDs);
		}

		return NIDs;
	}

	public List<Integer> getGroups()
	{
		return getGroups(false);
	}

	public List<Integer> getGroups(boolean sortDescending)
	{
		List<Integer> groups = new ArrayList<Integer>(this.groups.keySet());

		if (!sortDescending)
			Collections.sort(groups);
		else
			Collections.sort(groups, Collections.reverseOrder());

		return groups;
	}

	public List<EUID> getGroup(int group)
	{
		return getGroup(group, false);
	}

	public List<EUID> getGroup(int group, boolean shuffle)
	{
		synchronized(this.groups)
		{
			List<EUID> groupNIDs = null;

			if (this.groups.containsKey(group))
				groupNIDs = this.groups.get(group);
			else
				return new ArrayList<EUID>();

			if (shuffle)
				Collections.shuffle(groupNIDs);

			return Collections.unmodifiableList(groupNIDs);
		}
	}

	public List<EUID> getGroup(int group, Comparator<EUID> sorter)
	{
		synchronized(this.groups)
		{
			List<EUID> groupNIDs = null;

			if (this.groups.containsKey(group))
				groupNIDs = this.groups.get(group);
			else
				return new ArrayList<EUID>();

			Collections.sort(groupNIDs, sorter);
			return Collections.unmodifiableList(groupNIDs);
		}
	}

	private int calculateGroup(EUID NID)
	{
		BitSet targetBitSet = BitSet.valueOf(this.origin.toByteArray());
		BitSet candidateBitSet = BitSet.valueOf(NID.toByteArray());
		candidateBitSet.xor(targetBitSet);
		int nextSetBit = candidateBitSet.nextSetBit(0);

		if (constructing == false && nextSetBit >= this.groups.size()-1)
			return this.groups.size()-2;
		else if (constructing == true && nextSetBit == -1)
			return this.groups.size();
		else if (constructing == false && nextSetBit == -1)
			return this.groups.size()-1;

		return nextSetBit;
	}

	public int getGroup(EUID NID)
	{
		synchronized(this.groups)
		{
			return calculateGroup(NID);
		}
	}

	private boolean add(EUID NID)
	{
		synchronized(this.groups)
		{
			int group = calculateGroup(NID);

			if (!groups.containsKey(group))
				groups.put(group, new WireableList<EUID>());

			if (!groups.get(group).contains(NID))
				return groups.get(group).add(NID);

			return false;
		}
	}

	/**
	 * Determines if the provided NID is present in any group.
	 * <br>
	 * <br>
	 * <b>TODO</b> Not very efficient for large RoutingTables with many groups...optimize
	 *
	 * @param NID
	 * @return boolean
	 */
	public boolean contains(EUID NID)
	{
		synchronized(this.groups)
		{
			for (int g = 0 ; g < this.groups.size() ; g++)
				if (this.groups.get(g).contains(NID))
					return true;

			return false;
		}
	}

	private boolean remove(EUID NID)
	{
		if (NID.equals(this.origin))
			throw new IllegalArgumentException("Can not remove origin "+NID+"from RoutingTable");

		synchronized(this.groups)
		{
			int group = calculateGroup(NID);

			if (!groups.containsKey(group))
				return false;

			return groups.get(group).remove(NID);
		}
	}

	private void reorganize()
	{
		synchronized(this.groups)
		{
			List<Integer> groups = new ArrayList<Integer>(this.groups.keySet());
			Collections.sort(groups);
			Iterator<Integer> groupsIterator = groups.iterator();
			WireableList<EUID> compactedGroupNIDs = new WireableList<EUID>();

			int nextGroup = 0;
			while(groupsIterator.hasNext())
			{
				Integer group = groupsIterator.next();

				if (group != nextGroup)
					break;

				if (this.groups.get(group).size() >= MIN_GROUP_SIZE)
					groupsIterator.remove();
				else
					break;

				nextGroup++;
			}

			groupsIterator = groups.iterator();

			while(groupsIterator.hasNext())
			{
				Integer row = groupsIterator.next();
				WireableList<EUID> groupNIDs = this.groups.remove(row);
				compactedGroupNIDs.addAll(groupNIDs);
			}

			if (!compactedGroupNIDs.isEmpty())
			{
				if (compactedGroupNIDs.size() >= MIN_GROUP_SIZE || this.groups.isEmpty())
					this.groups.put(nextGroup, compactedGroupNIDs);
				else
					this.groups.get(Math.min(0, nextGroup-1)).addAll(compactedGroupNIDs);
			}
		}
	}

	public Set<EUID> getNext(EUID NID)
	{
		LinkedHashSet<EUID> nextNIDs = new LinkedHashSet<EUID>();
		int nextNIDGroup = calculateGroup(NID)-1;

		synchronized(this.groups)
		{
			if (this.groups.containsKey(nextNIDGroup))
			{
				Collections.shuffle(this.groups.get(nextNIDGroup));
				nextNIDs.addAll(this.groups.get(nextNIDGroup));
			}
		}

		return nextNIDs;
	}

	public Set<EUID> getPrevious(EUID NID)
	{
		Set<EUID> previousNIDs = new LinkedHashSet<EUID>();
		int previousNIDGroup = calculateGroup(NID)+1;

		synchronized(this.groups)
		{
			if (this.groups.containsKey(previousNIDGroup))
				previousNIDs.addAll(this.groups.get(previousNIDGroup));
		}

		return previousNIDs;
	}
}
