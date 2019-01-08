package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNodeAction;
import java.util.Set;

/**
 * A dispatch action request for a connected node with some given shards
 */
public interface FindANodeRequestAction extends RadixNodeAction {

	/**
	 * A shard space which must be intersected with a node's shard space to be selected
	 *
	 * @return shards which can be picked amongst to find a matching supporting node
	 */
	Set<Long> getShards();
}
