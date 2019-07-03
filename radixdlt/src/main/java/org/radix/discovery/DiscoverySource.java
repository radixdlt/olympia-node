package org.radix.discovery;

public interface DiscoverySource<T extends DiscoveryRequest>
{
	public abstract void discovery(T request) throws DiscoveryException;

	public abstract void query(T request) throws DiscoveryException;

	public abstract void fetch(T request) throws DiscoveryException;
}