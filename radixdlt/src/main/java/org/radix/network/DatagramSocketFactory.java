package org.radix.network;

import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;

@FunctionalInterface
public interface DatagramSocketFactory {

	DatagramSocket create(SocketAddress address) throws SocketException;

}
