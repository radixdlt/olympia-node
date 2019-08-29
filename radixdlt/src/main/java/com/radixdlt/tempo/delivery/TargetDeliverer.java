package com.radixdlt.tempo.delivery;

import com.radixdlt.common.AID;
import org.radix.network.peers.Peer;

import java.util.Collection;

public interface TargetDeliverer {
	void deliver(Collection<AID> aids, Peer peer);
}
