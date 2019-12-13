package org.radix.integration.conflict;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.CryptoException;
import org.radix.integration.RadixTest;
import org.radix.modules.Modules;

public class ConflictMessageComplexityTest extends RadixTest
{
	private final static int NUM_NODES = 16384;
	private final static int NUM_LEADERS = (int) Math.sqrt(NUM_NODES);
	private List<EUID> nodes;
	private List<EUID> leaders;
	private Map<EUID, Set<EUID>> requests;
	private Map<EUID, Set<EUID>> data;
	private SecureRandom rng;

	@Before
	public void setup() throws CryptoException
	{
		nodes = new ArrayList<EUID>();
		leaders = new ArrayList<EUID>();
		requests = new HashMap<EUID, Set<EUID>>();
		data = new HashMap<EUID, Set<EUID>>();

		for (int r = 0 ; r < NUM_NODES ; r++)
		{
			EUID NID = new ECKeyPair().getUID();
			nodes.add(NID);
			requests.put(NID, new HashSet<EUID>());
			data.put(NID, new HashSet<EUID>());
		}

		rng = new SecureRandom();
		
		Collections.shuffle(nodes);
		leaders.addAll(nodes.subList(0, NUM_LEADERS));
		for (EUID leader : leaders)
			data.get(leader).add(new EUID(rng.nextLong()));
	}

	@Test
	public void conflictMessageComplexityLeadersTest() throws CryptoException
	{
		int completed = 0;

		while (completed < NUM_LEADERS)
		{
			Collections.shuffle(leaders);

			for (EUID leader : leaders)
			{
				if (data.get(leader).size() == NUM_LEADERS)
					continue;

				EUID leaderToAsk = leaders.get(Math.abs(rng.nextInt(NUM_LEADERS)));

				if (requests.get(leader).contains(leaderToAsk))
					continue;

				requests.get(leader).add(leaderToAsk);
				data.get(leader).addAll(data.get(leaderToAsk));
				if (data.get(leader).size() == NUM_LEADERS)
					completed++;
			}
		}

		int totalRequests = 0;
		for (Set<EUID> request : requests.values())
			totalRequests += request.size();

		System.out.println("Total requests: "+totalRequests);
		System.out.println("Requests per node: "+(totalRequests / (double)NUM_LEADERS));
	}

	@Test
	public void conflictMessageComplexityTest() throws CryptoException
	{
		int completed = 0;

		while (completed < NUM_NODES)
		{
			Collections.shuffle(nodes);

			for (EUID node : nodes)
			{
				if (data.get(node).size() == NUM_LEADERS)
					continue;

				EUID leaderToAsk = leaders.get(Math.abs(rng.nextInt(NUM_LEADERS)));

				if (requests.get(node).contains(leaderToAsk))
					continue;

				// Requestor
				requests.get(node).add(leaderToAsk);
				data.get(node).addAll(data.get(leaderToAsk));
				if (data.get(node).size() == NUM_LEADERS)
					completed++;
			}
		}

		int totalRequests = 0;
		for (Set<EUID> request : requests.values())
			totalRequests += request.size();

		System.out.println("Total requests: "+totalRequests);
		System.out.println("Requests per node: "+(totalRequests / (double)NUM_NODES));
	}

}
