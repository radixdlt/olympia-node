package org.radix.routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.radixdlt.common.EUID;
import com.radixdlt.utils.Offset;
import org.radix.modules.Modules;
import com.radixdlt.universe.Universe;

public abstract class NodeGroupTable<T> //extends BasicContainer
{
	public static final int DEFAULT_COLLATION = 16;  // FIXME change to 16 for production

	private int  	planck;
	private EUID 	origin;
	private final 	List<T>	source;
	private final 	Map<Integer, List<EUID>> groups = new HashMap<>();

	private final int collation;

	public NodeGroupTable(EUID origin, Collection<T> source)
	{
		this(origin, source, Long.MAX_VALUE);
	}

	public NodeGroupTable(EUID origin, Collection<T> source, long timestamp)
	{
		this(origin, source, Modules.get(Universe.class).toPlanck(timestamp, Offset.NONE), DEFAULT_COLLATION);
	}

	public NodeGroupTable(EUID origin, Collection<T> source, int planck)
	{
		this(origin, source, planck, DEFAULT_COLLATION);
	}

	public NodeGroupTable(EUID origin, Collection<T> source, int planck, int collation)
	{
		super();

		this.collation = collation;
		this.planck = planck;
		this.origin = origin;
		this.source = Collections.unmodifiableList(new ArrayList<T>(source));

		if (source != null)
		{
			for (T object : this.source)
			{
				EUID NID = this.extract(object);
				if (NID.equals(this.origin) == false)
					add(NID, this.map(object));
			}

			reorganize();

			add(this.origin, this.groups.size());
		}
	}

	private void reorganize()
	{
		synchronized(this.groups)
		{
			if (this.groups.isEmpty())
				return;

			List<Integer> groups = new ArrayList<Integer>(this.groups.keySet());
			Collections.sort(groups, Collections.reverseOrder());

			int lastGroup = groups.get(0);
			List<EUID> collatedGroupNIDS = new ArrayList<EUID>();
			int collateFromGroup = 0;

			for (int group = lastGroup ; group >= 0 ; group--)
			{
				if (this.groups.containsKey(group) == false ||
					this.groups.get(group).size() < this.collation)
					collateFromGroup = group;
			}

			Collections.reverse(groups);
			for (int group : groups)
			{
				if (group < collateFromGroup)
					continue;

				if (this.groups.containsKey(group) == true)
					collatedGroupNIDS.addAll(this.groups.remove(group));
			}

			if (collatedGroupNIDS.isEmpty() == false)
				this.groups.put(collateFromGroup, collatedGroupNIDS);
		}
	}

	public EUID getOrigin()
	{
		return this.origin;
	}

	public List<T> getSource()
	{
		return this.source;
	}

	public int getPlanck()
	{
		return this.planck;
	}

	public int groups()
	{
		return this.groups.size();
	}

	public int size()
	{
		synchronized(this.groups)
		{
			int size = 0;

			for (List<EUID> NIDs : this.groups.values())
				size += NIDs.size();

			return size;
		}
	}

	protected abstract int map(T NID);

	protected abstract EUID extract(T NID);

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
				return Collections.emptyList();

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
				return Collections.emptyList();

			Collections.sort(groupNIDs, sorter);
			return Collections.unmodifiableList(groupNIDs);
		}
	}

	public int getGroup(EUID NID)
	{
		synchronized(this.groups)
		{
			for (Integer group : this.groups.keySet())
				if (this.groups.get(group).contains(NID))
					return group;

			return -1;
		}
	}

	private boolean add(EUID NID, int group)
	{
		synchronized(this.groups)
		{
			if (!groups.containsKey(group))
				groups.put(group, new ArrayList<EUID>());

			if (!groups.get(group).contains(NID))
				return groups.get(group).add(NID);

			return false;
		}
	}

	/**
	 * Determines if the provided NID is present in any group.
	 * <br>
	 * <br>
	 * <b>TODO</b> Not very efficient for large NodeGroupTables with many groups...optimize
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

	public List<EUID> getNext(EUID nid, boolean shuffle)
	{
		List<EUID> nextNIDS = getNext(nid);
		if (shuffle) {
			Collections.shuffle(nextNIDS);
		}
		return nextNIDS;
	}

	public List<EUID> getNext(EUID NID, Comparator<EUID> sorter)
	{
		List<EUID> nextNIDS = getNext(NID);
		Collections.sort(nextNIDS, sorter);
		return nextNIDS;
	}

	public List<EUID> getNext(EUID NID)
	{
		List<EUID> nextNIDS = new ArrayList<EUID>();
		int group = getGroup(NID)-1;

		synchronized(this.groups)
		{
			if (this.groups.containsKey(group))
				nextNIDS.addAll(this.groups.get(group));
		}

		return nextNIDS;
	}

	public List<EUID> getPrevious(EUID nid, boolean shuffle)
	{
		List<EUID> previousNIDS = getPrevious(nid);
		if (shuffle) {
			Collections.shuffle(previousNIDS);
		}
		return previousNIDS;
	}

	public List<EUID> getPrevious(EUID NID, Comparator<EUID> sorter)
	{
		List<EUID> previousNIDS = getPrevious(NID);
		Collections.sort(previousNIDS, sorter);
		return previousNIDS;
	}

	public List<EUID> getPrevious(EUID NID)
	{
		List<EUID> previousNIDS = new ArrayList<EUID>();

		if (NID.equals(this.origin))
			return previousNIDS;

		int group = getGroup(NID)+1;

		synchronized(this.groups)
		{
			if (this.groups.containsKey(group))
				previousNIDS.addAll(this.groups.get(group));
		}

		return previousNIDS;
	}

	@Override
	public String toString()
	{
		return "Period: "+this.planck+" Origin: "+this.origin+" Num Groups: "+this.groups()+" Num NIDS: "+this.size();
	}
}
