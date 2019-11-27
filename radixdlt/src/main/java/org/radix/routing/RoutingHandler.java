package org.radix.routing;

import com.radixdlt.common.EUID;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Offset;
import org.radix.atoms.events.AtomEvent;
import org.radix.common.executors.ScheduledExecutable;
import org.radix.database.exceptions.DatabaseException;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.modules.Service;
import org.radix.modules.exceptions.ModuleException;
import org.radix.network.peers.PeerListener;
import org.radix.network.peers.events.PeerAvailableEvent;
import org.radix.network.peers.events.PeerEvent;
import org.radix.properties.RuntimeProperties;
import org.radix.time.NtpService;
import org.radix.universe.system.LocalSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class RoutingHandler extends Service
{
	private static final Logger log = Logging.getLogger ();

	private Map<Integer, Set<EUID>> NIDSets = new LinkedHashMap<Integer, Set<EUID>>(32, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, Set<EUID>> eldest)
		{
			if (size() > Modules.get(RuntimeProperties.class).get("routing.max_cache", 64))
			{
				try
				{
					Modules.get(RoutingStore.class).storeNIDs(eldest.getKey(), eldest.getValue());
				}
				catch (Exception ex)
				{
					log.error("Could not flush NID set from routing cache", ex);
				}

				return true;
			}

			return false;
		}
	};

	private ScheduledExecutable nidExecutable = null;

	public RoutingHandler()
	{
		super();
	}

	@Override
	public void start_impl() throws ModuleException
	{
		scheduleAtFixedRate(this.nidExecutable = new ScheduledExecutable(0, 60, TimeUnit.SECONDS)
		{
			@Override
			public void execute()
			{
				// Add the local node to the routing tables for the next planck period
				int planck = Modules.get(Universe.class).toPlanck(Modules.get(NtpService.class).getUTCTimeMS(), Offset.NEXT);

				try
				{
					synchronized(RoutingHandler.this.NIDSets)
					{
						Set<EUID> NIDs = RoutingHandler.this.NIDSets.get(planck);

						if (NIDs == null)
						{
							NIDs = Modules.get(RoutingStore.class).getNIDs(planck);
							RoutingHandler.this.NIDSets.put(planck, NIDs);
						}

						NIDs.add(LocalSystem.getInstance().getNID());
					}
				}
				catch (Exception ex)
				{
					log.error("Failed to update local NID for RoutingTable in planck "+planck, ex);
				}

				synchronized(RoutingHandler.this.NIDSets)
				{
					// Take a copy of the current NIDSet so as to not disrupt the LRU order of the main NIDSet map
					Map<Integer, Set<EUID>> NIDSetsCopy = new HashMap<Integer, Set<EUID>>(RoutingHandler.this.NIDSets);

					for (int period : NIDSetsCopy.keySet())
					{
						try
						{
							Set<EUID> NIDs = Modules.get(RoutingStore.class).getNIDs(period);
							NIDs.addAll(NIDSetsCopy.get(period));
							Modules.get(RoutingStore.class).storeNIDs(period, NIDs);
						}
						catch (Exception ex)
						{
							log.error("Update of cached NIDs to routing period "+period+" failed.", ex);
						}
					}
				}
			}
		});

		Events.getInstance().register(PeerEvent.class, this.peerListener);
	}

	@Override
	public void stop_impl()
	{
		if (this.nidExecutable != null) {
			this.nidExecutable.terminate(true);
			this.nidExecutable = null;
		}
		Events.getInstance().deregister(PeerEvent.class, this.peerListener);
	}

	@Override
	public String getName() { return "Routing Handler"; }

	public List<EUID> getNIDS(int planck) throws DatabaseException
	{
		Set<EUID> NIDS;

		synchronized(RoutingHandler.this.NIDSets)
		{
			NIDS = this.NIDSets.get(planck);

			if (NIDS == null)
			{
				NIDS = Modules.get(RoutingStore.class).getNIDs(planck);
				this.NIDSets.put(planck, NIDS);
			}
		}

		return new ArrayList<EUID>(NIDS);
	}

	public NodeAddressGroupTable getNodeAddressGroupTable(EUID origin, int planck) throws DatabaseException
	{
		Set<EUID> NIDs;

		synchronized(RoutingHandler.this.NIDSets)
		{
			NIDs = this.NIDSets.get(planck);

			if (NIDs == null)
			{
				NIDs = Modules.get(RoutingStore.class).getNIDs(planck);
				this.NIDSets.put(planck, NIDs);
			}

			return new NodeAddressGroupTable(origin, NIDs, planck);
		}
	}

	// PEER LISTENER //
	private PeerListener peerListener = new PeerListener()
	{
		@Override
		public void process(PeerEvent event)
		{
			if (!(event instanceof PeerAvailableEvent))
				return;

			int period = Modules.get(Universe.class).toPlanck(Modules.get(NtpService.class).getUTCTimeMS(), Offset.NEXT);

			synchronized(RoutingHandler.this.NIDSets)
			{
				Set<EUID> NIDs = RoutingHandler.this.NIDSets.get(period);

				if (NIDs == null)
				{
					NIDs = new HashSet<EUID>();
					RoutingHandler.this.NIDSets.put(period, NIDs);
				}

				NIDs.add(event.getPeer().getNID());
			}
		}
	};
}
