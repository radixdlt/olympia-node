package org.radix.routing;

import java.util.Collection;

import com.radixdlt.common.EUID;
import com.radixdlt.utils.Offset;
import org.radix.modules.Modules;
import com.radixdlt.universe.Universe;

public final class NodeAddressGroupTable extends NodeGroupTable<EUID>
{
	public NodeAddressGroupTable(final EUID origin, Collection<EUID> NIDS)
	{
		super(origin, NIDS);
	}

	public NodeAddressGroupTable(EUID origin, Collection<EUID> NIDS, long timestamp)
	{
		super(origin, NIDS, Modules.get(Universe.class).toPlanck(timestamp, Offset.NONE));
	}

	public NodeAddressGroupTable(EUID origin, Collection<EUID> NIDS, int planck)
	{
		super(origin, NIDS, planck);
	}

	public NodeAddressGroupTable(EUID origin, Collection<EUID> NIDS, int planck, int collation)
	{
		super(origin, NIDS, planck, collation);
	}

	@Override
	protected int map(EUID NID)
	{
		if (NID.equals(getOrigin()))
			return groups()-1;

		return getOrigin().xorDistance(NID);
	}

	@Override
	protected EUID extract(EUID NID)
	{
		return NID;
	}
}
