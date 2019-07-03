package org.radix.routing;

@SuppressWarnings("serial")
public class AddressException extends Exception
{
	public AddressException () { super (); }

	public AddressException (String arg0, Throwable arg1) { super (arg0, arg1); }

	public AddressException (String arg0) { super (arg0); }

	public AddressException (Throwable arg0) { super (arg0); }
}
