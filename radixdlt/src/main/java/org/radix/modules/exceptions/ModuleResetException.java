package org.radix.modules.exceptions;

import org.radix.modules.Module;

@SuppressWarnings("serial")
public class ModuleResetException extends ModuleException
{
	public ModuleResetException(Module module)
	{
		super (module);
	}

	public ModuleResetException(String message, Throwable throwable, Module module)
	{
		super (message, throwable, module);
	}

	public ModuleResetException(String message, Module module)
	{
		super (message, module);
	}

	public ModuleResetException(Throwable throwable, Module module)
	{
		super (throwable, module);
	}
}