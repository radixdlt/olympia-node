package org.radix.routing;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import com.radixdlt.common.EUID;
import com.radixdlt.utils.UInt128;

public class RoutingTest
{
//	@Test
	public void NIDXORDistance() throws Exception
	{
		List<EUID> NIDS = new ArrayList<>();

		NIDS.add(EUID.valueOf("26312855503943789100071475713"));
		NIDS.add(EUID.valueOf("38035442755579741557188277613"));
		NIDS.add(EUID.valueOf("39314043162796213988676552817"));
		NIDS.add(EUID.valueOf("-21376458623754612835463485761"));
		NIDS.add(EUID.valueOf("-11384458115968203875461238488"));
		NIDS.add(EUID.valueOf("19347106186833276442378678426"));
		NIDS.add(EUID.valueOf("-994589625208078929951108408"));
		NIDS.add(EUID.valueOf("-6224091741315758884315419177"));
		NIDS.sort(new Routing.NIDDistanceComparator(EUID.valueOf("-6224091741315758884315419177")));
		System.out.println(NIDS.toString());
	}

//	@Test
	public void NIDDelegates() throws Exception
	{
		List<EUID> NIDS = new ArrayList<>();

		NIDS.add(euidFromSigned("26312855503943789100071475713"));
		NIDS.add(euidFromSigned("38035442755579741557188277613"));
		NIDS.add(euidFromSigned("39314043162796213988676552817"));
		NIDS.add(euidFromSigned("-21376458623754612835463485761"));
		NIDS.add(euidFromSigned("-11384458115968203875461238488"));
		NIDS.add(euidFromSigned("19347106186833276442378678426"));
		NIDS.add(euidFromSigned("-994589625208078929951108408"));
		NIDS.add(euidFromSigned("-6224091741315758884315419177"));

		for (EUID NID : NIDS)
		{
			// TODO is a NodeAddressGroupTable accurate enough or do we need to extract this information from the TP verts
			// TODO dos using the XOR distance pose a security threat where dishonest nodes can "eclipse" my delegate set?
			NodeAddressGroupTable nodeAddressGroupTable = new NodeAddressGroupTable(NID, NIDS, 0);

			List<EUID> delegated = new ArrayList<EUID>();
			int requiredDelegates = (int) Math.ceil(Math.sqrt(nodeAddressGroupTable.size()));

			for (int group : nodeAddressGroupTable.getGroups(true))
			{
				if (group == nodeAddressGroupTable.getGroup(nodeAddressGroupTable.getOrigin()))
					continue;

				delegated.addAll(nodeAddressGroupTable.getGroup(group));

				if (delegated.size() >= requiredDelegates)
					break;
			}

			Collections.sort(delegated, new Routing.NIDDistanceComparator(nodeAddressGroupTable.getOrigin()));
			delegated = delegated.subList(0, Math.min(delegated.size(), requiredDelegates));
			System.out.println("Delegates for NID "+nodeAddressGroupTable.getOrigin()+" = "+delegated);
		}
	}

	@Test
	public void NIDDistanceGroupCheck() throws Exception
	{
		List<EUID> NIDS = new ArrayList<>();

		NIDS.add(euidFromSigned("26312855503943789100071475713"));
		NIDS.add(euidFromSigned("38035442755579741557188277613"));
		NIDS.add(euidFromSigned("-21376458623754612835463485761"));
		NIDS.add(euidFromSigned("39314043162796213988676552817"));
		NIDS.add(euidFromSigned("-11384458115968203875461238488"));
		NIDS.add(euidFromSigned("19347106186833276442378678426"));
		NIDS.add(euidFromSigned("-994589625208078929951108408"));
		NIDS.add(euidFromSigned("-6224091741315758884315419177"));

		for (EUID NID : NIDS)
		{
			NodeAddressGroupTable nodeAddressGroupTable = new NodeAddressGroupTable(NID, NIDS, 0, 2);
			for (int group : nodeAddressGroupTable.getGroups(true))
				System.out.println(group+": "+nodeAddressGroupTable.getGroup(group));

			List<EUID> sorted = new ArrayList<EUID>(NIDS);
			sorted.sort(new Routing.NIDDistanceComparator(NID));
			System.out.println(sorted.toString());
		}
	}

	private static EUID euidFromSigned(String value) {
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
