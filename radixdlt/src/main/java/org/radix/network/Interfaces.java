/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

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

	private Map<Domain, Set<InetAddress>> addresses = new HashMap<>();

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

	public List<InetAddress> getInterfaceInformation(NetworkInterface networkInterface)
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
