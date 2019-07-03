package org.radix.network;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Service;
import org.radix.modules.exceptions.ModuleException;

public class Interfaces extends Service
{
	private static final Logger networklog = Logging.getLogger ("network");

	public enum Domain
	{
		LAN, WAN
	}

/*	private static Interfaces instance = null;

	public Interfaces getInstance()
	{
		if (instance == null)
			instance = new Interfaces();

		return instance;
	}*/

	private Map<Domain, Set<InetAddress>> addresses = new HashMap<Domain, Set<InetAddress>>();

	public Interfaces() throws SocketException
	{
		super();

		Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
		for (NetworkInterface networkInterface : Collections.list(networkInterfaces))
		{
			List<InetAddress> networkInterfaceAddresses = getInterfaceInformation(networkInterface);
			for (InetAddress networkInterfaceAddress : networkInterfaceAddresses)
				addInterfaceAddress(networkInterfaceAddress);
		}
	}

	@Override
	public void start_impl() throws ModuleException
	{
	}

	@Override
	public void stop_impl() throws ModuleException
	{
	}

	public boolean isSelf(InetAddress address) throws SocketException
	{
		if (addresses.containsKey(Domain.LAN) && addresses.get(Domain.LAN).contains(address))
			return true;

		if (addresses.containsKey(Domain.WAN) && addresses.get(Domain.WAN).contains(address))
			return true;

		return false;
	}

	public Domain getInterfaceAddressDomain(InetAddress address)
	{
		if (address.isLoopbackAddress() || address.isLinkLocalAddress() ||
			address.isAnyLocalAddress() || address.isSiteLocalAddress())
    		return Domain.LAN;
    	else
    		return Domain.WAN;
	}

	public boolean hasInterfaceAddress(InetAddress address)
	{
		if ((addresses.containsKey(Domain.LAN) && addresses.get(Domain.LAN).contains(address)) ||
			(addresses.containsKey(Domain.WAN) && addresses.get(Domain.WAN).contains(address)))
			return true;

		return false;
	}

	public List<InetAddress> getInterfaceAddresses(Domain domain)
	{
		if (addresses.containsKey(domain))
			return new ArrayList<InetAddress>(addresses.get(domain));

		return Collections.emptyList();
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

	public List<InetAddress> getAllInterfaceAddresses()
	{
		List<InetAddress> interfaceAddresses = new ArrayList<InetAddress>();

		try
		{
			Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

			while (networkInterfaces.hasMoreElements())
				interfaceAddresses.addAll(Collections.list(networkInterfaces.nextElement().getInetAddresses()));
		}
		catch (Exception ex)
		{
			networklog.error("Could not get interface addresses", ex);
		}

	    return interfaceAddresses;
	}

	public List<InetAddress> getInterfaceAddresses(NetworkInterface networkInterface)
	{
		return Collections.list(networkInterface.getInetAddresses());
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
