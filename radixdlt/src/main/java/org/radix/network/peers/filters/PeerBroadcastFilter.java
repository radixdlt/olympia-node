package org.radix.network.peers.filters;

import org.radix.modules.Modules;
import org.radix.network2.addressbook.Peer;
import org.radix.time.NtpService;
import org.radix.time.Timestamps;
import com.radixdlt.universe.Universe;

public class PeerBroadcastFilter extends PeerFilter
{
	@Override
	public boolean filter(Peer peer)
	{
		if (Modules.get(NtpService.class).getUTCTimeMS() - peer.getTimestamp(Timestamps.ACTIVE) > Modules.get(Universe.class).getPlanck())
			return true;

		return super.filter(peer);
	}
}
