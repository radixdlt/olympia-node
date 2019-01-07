package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNodeAction;
import java.util.Set;

public interface FindANodeAction extends RadixNodeAction {
	Set<Long> getShards();
}
