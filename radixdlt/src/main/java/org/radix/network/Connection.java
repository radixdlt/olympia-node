package org.radix.network;

import java.io.IOException;
import java.net.URI;

import org.radix.common.ID.ID;

public interface Connection extends ID
{
	public void connect() throws IOException;
	public void disconnect() throws IOException;
	public byte[] receive() throws IOException;
	public void send(byte[] bytes) throws IOException;
	public URI toURI();
}
