package test.integration;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import com.radixdlt.common.EUID;
import org.radix.integration.RadixTest;
import org.radix.modules.Modules;
import org.radix.routing.RoutingTable;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;

public class RoutingTableTest extends RadixTest
{

//	@Test
	public void benchmark()
	{
		Random random = new Random();
		List<EUID> NIDS = new ArrayList<EUID>();

		for (int i=0 ; i < 10000 ; i++)
		{
			byte[] bytes = new byte[12];
			random.nextBytes(bytes);
			NIDS.add(new EUID(bytes));
		}

		long start = System.currentTimeMillis();

		for (EUID NID : NIDS)
		{
			RoutingTable routingTable = new RoutingTable(NID, NIDS);
			System.out.println(NID);

			for (Integer row : routingTable.getGroups())
				System.out.println(row+": "+routingTable.getGroup(row).size());
		}

		System.out.println("Took "+(System.currentTimeMillis()-start)+" to build "+NIDS.size()+" routing tables");
	}

	@Test
	public void EmptyGroupFailureTest()
	{
		List<EUID> NIDS = new ArrayList<EUID>();
		NIDS.add(EUID.valueOf(asHex("-6224091741315758884315419177")));
		NIDS.add(EUID.valueOf(asHex("38035442755579741557188277613")));
		NIDS.add(EUID.valueOf(asHex("19347106186833276442378678426")));
		NIDS.add(EUID.valueOf(asHex("-28400575019630574652102087428")));

		RoutingTable routingTable = new RoutingTable(NIDS.get(0), NIDS);
		Assert.assertEquals(2, routingTable.numGroups());
	}

	@Test
	public void ValidateReverseRoute()
	{
		List<EUID> NIDS = new ArrayList<EUID>();
		NIDS.add(EUID.valueOf(asHex("-6224091741315758884315419177")));
		NIDS.add(EUID.valueOf(asHex("38035442755579741557188277613")));
		NIDS.add(EUID.valueOf(asHex("-28400575019630574652102087428")));
		NIDS.add(EUID.valueOf(asHex("19347106186833276442378678426")));
		NIDS.add(EUID.valueOf(asHex("-994589625208078929951108408")));
		NIDS.add(EUID.valueOf(asHex("-11384458115968203875461238488")));
		NIDS.add(EUID.valueOf(asHex("26312855503943789100071475713")));
		NIDS.add(EUID.valueOf(asHex("39314043162796213988676552817")));

		RoutingTable routingTable = new RoutingTable(NIDS.get(0), NIDS);

		for (EUID NID : NIDS)
		{
			int NIDGroup = routingTable.getGroup(NID);
			Set<EUID> previousNIDs = routingTable.getPrevious(NID);
			Assert.assertTrue(routingTable.getGroup(NIDGroup+1).containsAll(previousNIDs));
		}
	}

	@Test
	public void ValidateForwardRoute()
	{
		List<EUID> NIDS = new ArrayList<EUID>();
		NIDS.add(EUID.valueOf(asHex("-6224091741315758884315419177")));
		NIDS.add(EUID.valueOf(asHex("38035442755579741557188277613")));
		NIDS.add(EUID.valueOf(asHex("-28400575019630574652102087428")));
		NIDS.add(EUID.valueOf(asHex("19347106186833276442378678426")));
		NIDS.add(EUID.valueOf(asHex("-994589625208078929951108408")));
		NIDS.add(EUID.valueOf(asHex("-11384458115968203875461238488")));
		NIDS.add(EUID.valueOf(asHex("26312855503943789100071475713")));
		NIDS.add(EUID.valueOf(asHex("39314043162796213988676552817")));

		RoutingTable routingTable = new RoutingTable(NIDS.get(0), NIDS);

		for (EUID NID : NIDS)
		{
			int NIDGroup = routingTable.getGroup(NID);
			Set<EUID> nextNIDs = routingTable.getNext(NID);
			Assert.assertTrue(routingTable.getGroup(NIDGroup-1).containsAll(nextNIDs));
		}
	}

	@Test
	public void BuildSuperMicroRoutingTable()
	{
		List<EUID> NIDS = new ArrayList<EUID>();
		NIDS.add(EUID.valueOf(asHex("-6224091741315758884315419177")));
		NIDS.add(EUID.valueOf(asHex("-28400575019630574652102087428")));

		RoutingTable routingTable = new RoutingTable(NIDS.get(0), NIDS);
		for (Integer row : routingTable.getGroups())
			System.out.println(row+": "+routingTable.getGroup(row).size()+" -> "+routingTable.getGroup(row).toString());
	}

	@Test
	public void BuildMacroRoutingTable()
	{
		long seed = System.nanoTime();
		System.out.println("Seed: "+seed);

		Random random = new Random(1074630250579104l);
		List<EUID> NIDS = new ArrayList<EUID>();

		for (int i=0 ; i < 512 ; i++)
		{
			byte[] bytes = new byte[12];
			random.nextBytes(bytes);
			NIDS.add(new EUID(bytes));
		}

		RoutingTable routingTable = new RoutingTable(NIDS.get(0), NIDS);
		for (Integer row : routingTable.getGroups())
			System.out.println(row+": "+routingTable.getGroup(row).size()+" -> "+routingTable.getGroup(row).toString());
	}

	@Test
	public void GetNextFromMacroRoutingTable()
	{
		Random random = new Random(1074630250579104l);
		List<EUID> NIDS = new ArrayList<EUID>();

		for (int i=0 ; i < 512 ; i++)
		{
			byte[] bytes = new byte[12];
			random.nextBytes(bytes);
			NIDS.add(new EUID(bytes));
		}

		RoutingTable routingTable = new RoutingTable(NIDS.get(0), NIDS);

		for (Integer group : routingTable.getGroups())
			System.out.println(group+": "+routingTable.getGroup(group).size()+" -> "+routingTable.getGroup(group).toString());

		Set<EUID> nextNIDs = routingTable.getNext(routingTable.getOrigin());
		System.out.println("Next From Origin -> "+nextNIDs.toString());

		while (!nextNIDs.isEmpty())
		{
			System.out.print("Next From "+nextNIDs.iterator().next()+" -> ");
			nextNIDs = routingTable.getNext(nextNIDs.iterator().next());
			System.out.println(nextNIDs.toString());
		}
	}

	@Test
	public void GetPreviousFromMacroRoutingTable()
	{
		Random random = new Random(1074630250579104l);
		List<EUID> NIDS = new ArrayList<EUID>();

		for (int i=0 ; i < 512 ; i++)
		{
			byte[] bytes = new byte[12];
			random.nextBytes(bytes);
			NIDS.add(new EUID(bytes));
		}

		RoutingTable routingTable = new RoutingTable(NIDS.get(0), NIDS);

		for (Integer group : routingTable.getGroups())
			System.out.println(group+": "+routingTable.getGroup(group).size()+" -> "+routingTable.getGroup(group).toString());

		Set<EUID> previousNIDs = routingTable.getPrevious(routingTable.getGroup(0).get(0));
		System.out.println("Previous From "+routingTable.getGroup(0).get(0)+" -> "+previousNIDs.toString());

		while (!previousNIDs.isEmpty())
		{
			EUID previousNID = previousNIDs.iterator().next();
			System.out.print("Previous From "+(previousNID == routingTable.getOrigin() ? "Origin" : previousNID)+" -> ");
			previousNIDs = routingTable.getPrevious(previousNID);
			System.out.println(previousNIDs.toString());
		}
	}

	@Test
	@Ignore // This test seems useful but takes loooong to run.
	// Profiling shows that 90%+ of the time is spent in the constructor "new RoutingTable(NIDs.get(i), NIDs)".
	public void groupControlProbabilityTest()
	{
		Random random = new Random(1074630250579104l);
		List<EUID> NIDs = new ArrayList<EUID>();
		Set<EUID> attackerNIDs = new HashSet<EUID>();

		for (int i=0 ; i < 600 ; i++)
		{
			byte[] bytes = new byte[12];
			random.nextBytes(bytes);
			NIDs.add(new EUID(bytes));
		}

		for (int i=0 ; i < 9400 ; i++)
		{
			byte[] bytes = new byte[12];
			random.nextBytes(bytes);
			EUID NID = new EUID(bytes);
			NIDs.add(NID);
			attackerNIDs.add(NID);
		}

		int attackable = 0;
		for (int i = 0 ; i < NIDs.size() ; i++)
		{
			if (i % 100 == 0)
				System.out.println("Progress ... "+i);

			RoutingTable routingTable = new RoutingTable(NIDs.get(i), NIDs);

			int midGroup = routingTable.numGroups() - ((routingTable.numGroups() / 2)+1);
			List<EUID> groupNIDs = routingTable.getGroup(midGroup);

			if (attackerNIDs.containsAll(groupNIDs))
			{
				attackable++;
				System.out.println("Discovered attackable RoutingTable at configuration "+i);
			}
		}

		if (attackable < 2)
			System.out.println("No attackable RoutingTable configurations found");
		else
			Assert.fail("Attackable RoutingTable configurations found");
	}

	@Test
	public void testSerialization() throws IOException
	{
		Random random = new Random(1074630250579104l);
		List<EUID> NIDS = new ArrayList<EUID>();

		for (int i=0 ; i < 512 ; i++)
		{
			byte[] bytes = new byte[12];
			random.nextBytes(bytes);
			NIDS.add(new EUID(bytes));
		}

		RoutingTable routingTable = new RoutingTable(NIDS.get(0), NIDS);
		byte[] serialized = Modules.get(Serialization.class).toDson(routingTable, Output.WIRE);
		RoutingTable deserialized = Modules.get(Serialization.class).fromDson(serialized, RoutingTable.class);

		for (Integer group : routingTable.getGroups())
		{
			List<EUID> NIDs = routingTable.getGroup(group);
			List<EUID> deserializedNIDs = deserialized.getGroup(group);

			for (int i = 0 ; i < NIDs.size() ; i++)
				Assert.assertEquals(NIDs.get(i), deserializedNIDs.get(i));
		}
	}

	private static String asHex(String decimal) {
		BigInteger bi = new BigInteger(decimal);
		byte[] bytes = bi.toByteArray();
		String lead = bi.signum() < 0 ? "ff" : "00";
		StringBuilder sb = new StringBuilder();
		for (int i = bytes.length; i < 16; ++i) {
			sb.append(lead);
		}
		for (int i = 0; i < bytes.length; ++i) {
			sb.append(String.format("%02x", bytes[i] & 0xFF));
		}
		System.out.format("%s (dec) -> %s (hex)%n", decimal, sb.toString());
		return sb.toString();
	}
}
