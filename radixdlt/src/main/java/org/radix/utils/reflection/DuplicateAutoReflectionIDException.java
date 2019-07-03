package org.radix.utils.reflection;

@SuppressWarnings("serial")
public class DuplicateAutoReflectionIDException extends Exception 
{
	public DuplicateAutoReflectionIDException () { super (); }

	public DuplicateAutoReflectionIDException (String arg0, Throwable arg1) { super (arg0, arg1); }

	public DuplicateAutoReflectionIDException (String arg0) { super (arg0); }

	public DuplicateAutoReflectionIDException (Throwable arg0) { super (arg0); }
}
