package org.radix.network;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.radix.logging.Logger;
import org.radix.logging.Logging;

public class Interfaces
{
	private static final Logger networklog = Logging.getLogger ("network");

	public enum Domain
	{
		LAN, WAN
	}

	private Map<Domain, Set<InetAddress>> addresses = new HashMap<Domain, Set<InetAddress>>();

	public Interfaces()
	{
		try {
			Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
			for (NetworkInterface networkInterface : Collections.list(networkInterfaces))
			{
				List<InetAddress> networkInterfaceAddresses = getInterfaceInformation(networkInterface);
				for (InetAddress networkInterfaceAddress : networkInterfaceAddresses)
					addInterfaceAddress(networkInterfaceAddress);
			}
		} catch (SocketException e) {
			throw new RuntimeException("while adding interfaces", e);
		}
	}

	public boolean isSelf(InetAddress address)
	{
		if (addresses.containsKey(Domain.LAN) && addresses.get(Domain.LAN).contains(address))
			return true;

		if (addresses.containsKey(Domain.WAN) && addresses.get(Domain.WAN).contains(address))
			return true;

		return false;
	}


	public boolean addInterfaceAddress(InetAddress address)
	{
		if (address.isLoopbackAddress() || address.isLinkLocalAddress() ||
			address.isAnyLocalAddress() || address.isSiteLocalAddress())
    	{
    		addresses.putIfAbsent(Domain.LAN, new HashSet<InetAddress>());
    		return addresses.get(Domain.LAN).add(address);
    	}
    	else
    	{
    		addresses.putIfAbsent(Domain.WAN, new HashSet<InetAddress>());
    		return addresses.get(Domain.WAN).add(address);
    	}
	}

	public List<InetAddress> getInterfaceInformation(NetworkInterface networkInterface) throws SocketException
	{
		networklog.info("Display name: "+networkInterface.getDisplayName());
		networklog.info("Name: "+networkInterface.getName());
		Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
		List<InetAddress> inetAddressList = Collections.list(inetAddresses);
		for (InetAddress inetAddress : inetAddressList)
			networklog.info("InetAddress: "+inetAddress);

		return inetAddressList;
	}
}
