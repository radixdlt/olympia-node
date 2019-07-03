package org.radix.mass;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import com.radixdlt.crypto.ECKeyPair;
import org.radix.integration.RadixTest;
import org.radix.modules.Modules;
import com.radixdlt.utils.UInt384;

public class NodeMassGroupTableTest extends RadixTest
{
	@Test
	@Ignore("FIXME MPS: Test is currently not working, and was not working when copied from Alphanet branch")
	public void BuildMacroNodeMassGroupTable() throws Exception
	{
		List<NodeMass> nodeMasses = new ArrayList<>();

		for (int i = 0 ; i < 256 ; i++)
			nodeMasses.add(new NodeMass(new ECKeyPair().getUID(), UInt384.from(Math.abs(Modules.get(SecureRandom.class).nextInt(256+(1<<(i/16))))), 1));
		nodeMasses.sort(NodeMassGroupTable.NODE_MASS_COMPARATOR);

		NodeMassGroupTable nodeMassGroupTable = new NodeMassGroupTable(nodeMasses.get(0).getNID(), nodeMasses, 1);
		Assert.assertEquals(8, nodeMassGroupTable.groups());
	}
}