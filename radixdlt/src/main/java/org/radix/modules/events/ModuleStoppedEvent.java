package org.radix.modules.events;

import org.radix.modules.Module;

public final class ModuleStoppedEvent extends ModuleEvent
{
	public ModuleStoppedEvent(Module module)
	{
		super(module);
	}
}
