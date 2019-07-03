package org.radix.discovery;

@SuppressWarnings("serial")
public class DiscoveryException extends Exception 
{
	public DiscoveryException (Throwable cause) { super (cause); }

	public DiscoveryException (String message, Throwable cause) { super (message, cause); }

	public DiscoveryException (String message) { super (message); }
}
