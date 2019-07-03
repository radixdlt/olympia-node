package org.radix.network.exceptions;

@SuppressWarnings("serial")
public class BanException extends Exception 
{
	public BanException () { super (); }

	public BanException (String arg0, Throwable arg1) { super (arg0, arg1); }

	public BanException (String arg0) { super (arg0); }

	public BanException (Throwable arg0) { super (arg0); }
}
