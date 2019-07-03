package org.radix.integration.routing;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import com.radixdlt.common.EUID;
import org.radix.integration.RadixTest;
import org.radix.routing.NodeAddressGroupTable;
import com.radixdlt.utils.UInt128;

public class NodeAddressGroupTableTest extends RadixTest
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
			NodeAddressGroupTable nodeAddressGroupTable = new NodeAddressGroupTable(NID, NIDS);
			System.out.println(NID);

			for (Integer row : nodeAddressGroupTable.getGroups())
				System.out.println(row+": "+nodeAddressGroupTable.getGroup(row).size());
		}

		System.out.println("Took "+(System.currentTimeMillis()-start)+" to build "+NIDS.size()+" routing tables");
	}

	@Test
	public void ValidateReverseRoute()
	{
		List<EUID> NIDS = new ArrayList<EUID>();
		NIDS.add(euidOfSigned128("-6224091741315758884315419177"));
		NIDS.add(euidOfSigned128("38035442755579741557188277613"));
		NIDS.add(euidOfSigned128("-28400575019630574652102087428"));
		NIDS.add(euidOfSigned128("19347106186833276442378678426"));
		NIDS.add(euidOfSigned128("-994589625208078929951108408"));
		NIDS.add(euidOfSigned128("-11384458115968203875461238488"));
		NIDS.add(euidOfSigned128("26312855503943789100071475713"));
		NIDS.add(euidOfSigned128("39314043162796213988676552817"));

		NodeAddressGroupTable nodeAddressGroupTable = new NodeAddressGroupTable(NIDS.get(0), NIDS);

		for (EUID NID : NIDS)
		{
			int NIDGroup = nodeAddressGroupTable.getGroup(NID);
			List<EUID> previousNIDs = nodeAddressGroupTable.getPrevious(NID);
			Assert.assertTrue(nodeAddressGroupTable.getGroup(NIDGroup+1).containsAll(previousNIDs));
		}
	}

	@Test
	public void ValidatePreviousToOrigin()
	{
		List<EUID> NIDS = new ArrayList<EUID>();
		NIDS.add(euidOfSigned128("-6224091741315758884315419177"));
		NIDS.add(euidOfSigned128("38035442755579741557188277613"));
		NIDS.add(euidOfSigned128("-28400575019630574652102087428"));
		NIDS.add(euidOfSigned128("19347106186833276442378678426"));
		NIDS.add(euidOfSigned128("-994589625208078929951108408"));
		NIDS.add(euidOfSigned128("-11384458115968203875461238488"));
		NIDS.add(euidOfSigned128("26312855503943789100071475713"));
		NIDS.add(euidOfSigned128("39314043162796213988676552817"));

		NodeAddressGroupTable nodeAddressGroupTable = new NodeAddressGroupTable(NIDS.get(0), NIDS);

		EUID NID = nodeAddressGroupTable.getGroup(0).get(0);

		while (NID.equals(nodeAddressGroupTable.getOrigin()) == false)
		{
			List<EUID> previousNIDS = nodeAddressGroupTable.getPrevious(NID);

			if (previousNIDS.isEmpty() == true)
				Assert.fail("Previous NID set empty before discovering origin");

			NID = previousNIDS.get(0);
		}
	}

	@Test
	public void ValidateForwardRoute()
	{
		List<EUID> NIDS = new ArrayList<EUID>();
		NIDS.add(euidOfSigned128("-6224091741315758884315419177"));
		NIDS.add(euidOfSigned128("38035442755579741557188277613"));
		NIDS.add(euidOfSigned128("-28400575019630574652102087428"));
		NIDS.add(euidOfSigned128("19347106186833276442378678426"));
		NIDS.add(euidOfSigned128("-994589625208078929951108408"));
		NIDS.add(euidOfSigned128("-11384458115968203875461238488"));
		NIDS.add(euidOfSigned128("26312855503943789100071475713"));
		NIDS.add(euidOfSigned128("39314043162796213988676552817"));

		NodeAddressGroupTable nodeAddressGroupTable = new NodeAddressGroupTable(NIDS.get(0), NIDS);

		for (EUID NID : NIDS)
		{
			int NIDGroup = nodeAddressGroupTable.getGroup(NID);
			List<EUID> nextNIDs = nodeAddressGroupTable.getNext(NID);
			Assert.assertTrue(nodeAddressGroupTable.getGroup(NIDGroup-1).containsAll(nextNIDs));
		}
	}

	@Test
	public void BuildSuperMicroRoutingTable()
	{
		List<EUID> NIDS = new ArrayList<EUID>();
		NIDS.add(euidOfSigned128("-6224091741315758884315419177"));
		NIDS.add(euidOfSigned128("-28400575019630574652102087428"));

		NodeAddressGroupTable nodeAddressGroupTable = new NodeAddressGroupTable(NIDS.get(0), NIDS);
		for (Integer row : nodeAddressGroupTable.getGroups())
			System.out.println(row+": "+nodeAddressGroupTable.getGroup(row).size()+" -> "+nodeAddressGroupTable.getGroup(row).toString());
	}

	@Test
	public void BuildMacroRoutingTable()
	{
		long seed = System.nanoTime();
		System.out.println("Seed: "+seed);

		Random random = new Random(1074630250579104l);
		List<EUID> NIDS = new ArrayList<EUID>();

		for (int i=0 ; i < 1024 ; i++)
		{
			byte[] bytes = new byte[12];
			random.nextBytes(bytes);
			NIDS.add(new EUID(bytes));
		}

		NodeAddressGroupTable nodeAddressGroupTable = new NodeAddressGroupTable(NIDS.get(0), NIDS);
		for (Integer row : nodeAddressGroupTable.getGroups())
			System.out.println(row+": "+nodeAddressGroupTable.getGroup(row).size()+" -> "+nodeAddressGroupTable.getGroup(row).toString());
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

		NodeAddressGroupTable nodeAddressGroupTable = new NodeAddressGroupTable(NIDS.get(0), NIDS);
		for (Integer group : nodeAddressGroupTable.getGroups())
			System.out.println(group+": "+nodeAddressGroupTable.getGroup(group).size()+" -> "+nodeAddressGroupTable.getGroup(group).toString());

		List<EUID> nextNIDs = nodeAddressGroupTable.getNext(nodeAddressGroupTable.getOrigin());
		System.out.println("Next From Origin -> "+nextNIDs.toString());

		while (!nextNIDs.isEmpty())
		{
			System.out.print("Next From "+nextNIDs.iterator().next()+" -> ");
			nextNIDs = nodeAddressGroupTable.getNext(nextNIDs.iterator().next());
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

		NodeAddressGroupTable nodeAddressGroupTable = new NodeAddressGroupTable(NIDS.get(0), NIDS);
		for (Integer group : nodeAddressGroupTable.getGroups())
			System.out.println(group+": "+nodeAddressGroupTable.getGroup(group).size()+" -> "+nodeAddressGroupTable.getGroup(group).toString());

		List<EUID> previousNIDs = nodeAddressGroupTable.getPrevious(nodeAddressGroupTable.getGroup(0).get(0));
		System.out.println("Previous From "+nodeAddressGroupTable.getGroup(0).get(0)+" -> "+previousNIDs.toString());

		while (!previousNIDs.isEmpty())
		{
			EUID previousNID = previousNIDs.iterator().next();
			System.out.print("Previous From "+(previousNID == nodeAddressGroupTable.getOrigin() ? "Origin" : previousNID)+" -> ");
			previousNIDs = nodeAddressGroupTable.getPrevious(previousNID);
			System.out.println(previousNIDs.toString());
		}
	}

//	@Test
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

			NodeAddressGroupTable nodeAddressGroupTable = new NodeAddressGroupTable(NIDs.get(i), NIDs);
			int midGroup = nodeAddressGroupTable.groups() - ((nodeAddressGroupTable.groups() / 2)+1);
			List<EUID> groupNIDs = nodeAddressGroupTable.getGroup(midGroup);

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

	static EUID euidOfSigned128(String value) {
		BigInteger bi = new BigInteger(value);
		byte[] bytes = bi.toByteArray();
		if (bytes.length < UInt128.BYTES) {
			byte[] newBytes = new byte[UInt128.BYTES];
			Arrays.fill(newBytes, bi.signum() < 0 ? (byte) 0xFF : (byte) 0x00);
			System.arraycopy(bytes, 0, newBytes, newBytes.length - bytes.length, bytes.length);
			bytes = newBytes;
		}
		UInt128 val = UInt128.from(bytes);
		return new EUID(val);
	}
}
