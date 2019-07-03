package org.radix.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.radixdlt.common.EUID;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Module;
import org.radix.modules.Plugin;
import org.radix.modules.exceptions.ModuleException;

public class Routing extends Plugin
{
	private static final Logger log = Logging.getLogger();

	public static final class NIDDistanceComparator implements Comparator<EUID>
	{
		private final EUID origin;

		public NIDDistanceComparator(EUID origin)
		{
			this.origin = origin;
		}

		@Override
		public int compare(EUID e1, EUID e2)
		{
			return origin.compareXorDistances(e2, e1);
		}
	}

	@Override
	public List<Class<? extends Module>> getComponents()
	{
		List<Class<? extends Module>> dependencies = new ArrayList<>();
		dependencies.add(RoutingStore.class);
		dependencies.add(RoutingHandler.class);
		return Collections.unmodifiableList(dependencies);
	}

	@Override
	public void start_impl() throws ModuleException
	{ }

	@Override
	public void stop_impl() throws ModuleException
	{ }

	@Override
	public String getName() { return "Routing"; }
}
