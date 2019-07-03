package org.radix.modules;

public abstract class Service extends Module
{
	@Override
	public ModuleStatus getStatus()
	{
		return new ModuleStatus(ModuleStatus.Status.AVAILABLE);
	}
}
