package org.radix.modules.exceptions;

import org.radix.modules.Module;

@SuppressWarnings("serial")
public class ModuleStartException extends ModuleException 
{
	public ModuleStartException(Module module)
	{
		super (module);
	}

	public ModuleStartException(String message, Throwable ex, Module module)
	{
		super (message, ex, module);
	}

	public ModuleStartException(String message, Module module)
	{
		super (message, module);
	}

	public ModuleStartException(Throwable ex, Module module)
	{
		super (ex, module);
	}
}