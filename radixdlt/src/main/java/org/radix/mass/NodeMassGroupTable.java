package org.radix.mass;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.radixdlt.common.EUID;
import org.radix.routing.NodeGroupTable;
import com.radixdlt.utils.UInt384;

public final class NodeMassGroupTable extends NodeGroupTable<NodeMass>
{
	public final static Comparator<NodeMass> NODE_MASS_COMPARATOR = new Comparator<NodeMass>()
	{
		@Override
		public int compare(NodeMass arg0, NodeMass arg1)
		{
			return arg0.getMass().compareTo(arg1.getMass());
		}
	};

	public NodeMassGroupTable(EUID origin, Collection<NodeMass> nodeMasses, int planck)
	{
		super(origin, nodeMasses, planck);

		UInt384 lastNodeMass = UInt384.ZERO;
		for (NodeMass nodeMass : nodeMasses)
		{
			if (nodeMass.getMass().compareTo(lastNodeMass) < 0)
				throw new IllegalStateException("NodeMasses are not sorted in ascending mass order");

			lastNodeMass = nodeMass.getMass();
		}
	}

	@Override
	protected int map(NodeMass nodeMass)
	{
		if (nodeMass.getNID().equals(getOrigin()))
			return groups()-1;

		List<NodeMass> source = getSource();
		int size = getSource().size()-1;
		int groups = 31 - Integer.numberOfLeadingZeros(size);
		int index = source.indexOf(nodeMass); // TODO optimize, SUPER inefficient over large sets
		int group = 31 - Integer.numberOfLeadingZeros(index);
		group = groups - group;
		return group;
	}

	@Override
	protected EUID extract(NodeMass nodeMass)
	{
		return nodeMass.getNID();
	}
}
