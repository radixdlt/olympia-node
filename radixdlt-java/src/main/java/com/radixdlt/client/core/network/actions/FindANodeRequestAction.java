package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNodeAction;
import java.util.Set;

public interface FindANodeRequestAction extends RadixNodeAction {
	Set<Long> getShards();
}
