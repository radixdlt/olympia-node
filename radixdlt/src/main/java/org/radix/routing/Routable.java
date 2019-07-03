package org.radix.routing;

import java.util.Set;

import com.radixdlt.common.EUID;

public interface Routable
{
	/**
	 * Returns a set of destinations for this Routable.
	 *  
	 * @return Set<EUID>
	 */
	public Set<EUID> getDestinations();

	/**
	 * Returns a set of shards for this Routable.
	 *  
	 * @return Set<Long>
	 */
	public Set<Long> getShards();
}
