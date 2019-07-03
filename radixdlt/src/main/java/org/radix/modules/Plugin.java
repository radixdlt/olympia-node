package org.radix.modules;

import org.radix.logging.Logger;
import org.radix.logging.Logging;

/**
 * Provides basic setup and start up for plugins
 *
 * @author Dan Hughes
 *
 */
public abstract class Plugin extends Module
{
	private static final Logger log = Logging.getLogger();

	@Override
	public ModuleStatus getStatus()
	{
		return new ModuleStatus(ModuleStatus.Status.AVAILABLE);
	}
}
