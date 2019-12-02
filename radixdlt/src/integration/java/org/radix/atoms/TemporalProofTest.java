package org.radix.atoms;

import com.radixdlt.common.AID;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.crypto.CryptoException;
import org.radix.exceptions.ValidationException;
import org.radix.integration.RadixTest;
import org.radix.time.TemporalProof;
import org.radix.time.TemporalProofNotValidException;
import org.radix.time.TemporalVertex;

import java.util.List;
import java.util.Set;

public class TemporalProofTest extends RadixTest
{
	@Rule
	public ExpectedException thrown = ExpectedException.none();

	private void validate(TemporalProof temporalProof) throws ValidationException {
		TemporalProofValidator.validate(temporalProof);
	}

	@Test
	public void createSimpleTemporalProof() throws CryptoException, ValidationException
	{
		ECKeyPair NODE_KEY = new ECKeyPair();
		Hash hash = new Hash("0000000000000000000000000000000000000000000000000000000000000000");

		TemporalProof temporalProof = new TemporalProof(AID.from(hash.toByteArray()));
		addVertex(temporalProof, NODE_KEY, 10L, 1L, EUID.ZERO);
		validate(temporalProof);
	}

	@Test
	public void createComplexTemporalProof() throws CryptoException, ValidationException
	{
		ECKeyPair NODE_KEY1 = new ECKeyPair();
		ECKeyPair NODE_KEY2 = new ECKeyPair();
		ECKeyPair NODE_KEY3 = new ECKeyPair();
		Hash hash = new Hash("0000000000000000000000000000000000000000000000000000000000000000");

		TemporalProof temporalProof = new TemporalProof(AID.from(hash.toByteArray()));
		addVertex(temporalProof, NODE_KEY1, 10L, 1L, EUID.ZERO);
		addVertex(temporalProof, NODE_KEY2, 20L, 1L, lastVertex(temporalProof).getHID());
		addVertex(temporalProof, NODE_KEY3, 30L, 1L, lastVertex(temporalProof).getHID());
		validate(temporalProof);
	}

	@Test
	public void createComplexMergedTemporalProof() throws CryptoException, ValidationException
	{
		ECKeyPair NODE_KEY11 = new ECKeyPair();
		ECKeyPair NODE_KEY12 = new ECKeyPair();
		ECKeyPair NODE_KEY13 = new ECKeyPair();
		ECKeyPair NODE_KEY22 = new ECKeyPair();
		ECKeyPair NODE_KEY23 = new ECKeyPair();
		Hash hash = new Hash("0000000000000000000000000000000000000000000000000000000000000000");

		TemporalProof temporalProof1 = new TemporalProof(AID.from(hash.toByteArray()));
		addVertex(temporalProof1, NODE_KEY11, 10L, 1L, EUID.ZERO);
		addVertex(temporalProof1, NODE_KEY12, 20L, 1L, lastVertex(temporalProof1).getHID());
		addVertex(temporalProof1, NODE_KEY13, 30L, 1L, lastVertex(temporalProof1).getHID());
		validate(temporalProof1);

		TemporalProof temporalProof2 = new TemporalProof(AID.from(hash.toByteArray()));
		temporalProof2.add(temporalProof1.getVertices().get(0), NODE_KEY11);
		addVertex(temporalProof2, NODE_KEY22, 20L, 1L, lastVertex(temporalProof2).getHID());
		addVertex(temporalProof2, NODE_KEY23, 30L, 1L, lastVertex(temporalProof2).getHID());
		validate(temporalProof2);

		temporalProof1.merge(temporalProof2);
		validate(temporalProof1);

		Assert.assertEquals(temporalProof1.size(), 5);
	}

	@Test
	public void getBranchesFromOriginOnlyTemporalProof() throws CryptoException, ValidationException
	{
		ECKeyPair NODE_KEY1 = new ECKeyPair();
		Hash hash = new Hash("0000000000000000000000000000000000000000000000000000000000000000");

		TemporalProof temporalProof = new TemporalProof(AID.from(hash.toByteArray()));
		addVertex(temporalProof, NODE_KEY1, 10L, 1L, EUID.ZERO);
		validate(temporalProof);

		Set<TemporalProof> branches = temporalProof.getBranches();

		Assert.assertTrue(branches.size() == 1);

		for (TemporalProof branch : branches)
			Assert.assertTrue(branch.hasVertexByNID(NODE_KEY1.getUID()));
	}

	@Test
	public void getBranchesFromComplexTemporalProof() throws CryptoException, ValidationException
	{
		ECKeyPair NODE_KEY1 = new ECKeyPair();
		ECKeyPair NODE_KEY2 = new ECKeyPair();
		ECKeyPair NODE_KEY3 = new ECKeyPair();
		ECKeyPair NODE_KEY4 = new ECKeyPair();
		Hash hash = new Hash("0000000000000000000000000000000000000000000000000000000000000000");

		TemporalProof temporalProof = new TemporalProof(AID.from(hash.toByteArray()));
		TemporalVertex temporalVertex = addVertex(temporalProof, NODE_KEY1, 10L, 1L, EUID.ZERO);
		addVertex(temporalProof, NODE_KEY2, 20L, 1L, temporalVertex.getHID());
		addVertex(temporalProof, NODE_KEY3, 30L, 1L, lastVertex(temporalProof).getHID());
		addVertex(temporalProof, NODE_KEY4, 10L, 1L, temporalVertex.getHID());
		validate(temporalProof);

		Set<TemporalProof> branches = temporalProof.getBranches();

		for (TemporalProof branch : branches)
		{
			Assert.assertTrue(branch.hasVertexByNID(NODE_KEY1.getUID()));

			if (branch.size() == 2)
				Assert.assertTrue(branch.hasVertexByNID(NODE_KEY4.getUID()));
			else if (branch.size() == 3)
			{
				Assert.assertTrue(branch.hasVertexByNID(NODE_KEY2.getUID()));
				Assert.assertTrue(branch.hasVertexByNID(NODE_KEY3.getUID()));
			}
			else
				throw new TemporalProofNotValidException("Unexpected branch size", branch);
		}
	}

	@Test
	public void getUnbrokenComplexTemporalProof() throws CryptoException, ValidationException
	{
		ECKeyPair NODE_KEY1 = new ECKeyPair();
		ECKeyPair NODE_KEY2 = new ECKeyPair();
		ECKeyPair NODE_KEY3 = new ECKeyPair();
		ECKeyPair NODE_KEY4 = new ECKeyPair();
		ECKeyPair NODE_KEY5 = new ECKeyPair();

		ECKeyPair NODE_KEYX1 = new ECKeyPair();
		ECKeyPair NODE_KEYX2 = new ECKeyPair();
		ECKeyPair NODE_KEYX3 = new ECKeyPair();
		ECKeyPair NODE_KEYX4 = new ECKeyPair();
		ECKeyPair NODE_KEYX5 = new ECKeyPair();

		Hash object = Hash.random();

		TemporalProof temporalProof = new TemporalProof(AID.from(object.toByteArray()));

		TemporalVertex temporalVertex0 = addVertex(temporalProof, NODE_KEY1, 10L, 1L, EUID.ZERO, NODE_KEY2.getUID(), NODE_KEY3.getUID());
		TemporalVertex temporalVertex00 = addVertex(temporalProof, NODE_KEY2, 20L, 1L, temporalVertex0.getHID(), NODE_KEY4.getUID());
		TemporalVertex temporalVertex01 = addVertex(temporalProof, NODE_KEY3, 30L, 1L, temporalVertex0.getHID(), NODE_KEY5.getUID());
		addVertex(temporalProof, NODE_KEY4, 40L, 1L, temporalVertex00.getHID());
		TemporalVertex temporalVertex010 = addVertex(temporalProof, NODE_KEY5, 50L, 1L, temporalVertex01.getHID());

		TemporalVertex temporalVertexX0 = addVertex(temporalProof, NODE_KEYX1, 999L, 1L, temporalVertex010.getHID(), NODE_KEYX2.getUID());
		TemporalVertex temporalVertexX1 = addVertex(temporalProof, NODE_KEYX2, 999L, 1L, temporalVertexX0.getHID(), NODE_KEYX3.getUID());
		TemporalVertex temporalVertexX2 = addVertex(temporalProof, NODE_KEYX3, 999L, 1L, temporalVertexX1.getHID(), NODE_KEYX4.getUID());
		TemporalVertex temporalVertexX3 = addVertex(temporalProof, NODE_KEYX4, 999L, 1L, temporalVertexX2.getHID(), NODE_KEYX5.getUID());
		addVertex(temporalProof, NODE_KEYX4, 999L, 1L, temporalVertexX3.getHID());

		validate(temporalProof);

		TemporalProof unbrokenTemporalProof = temporalProof.discardBrokenBranches();
		Assert.assertEquals(5, unbrokenTemporalProof.size());
	}

	@Test
	public void getSubSimpleTemporalProof() throws CryptoException, ValidationException
	{
		ECKeyPair NODE_KEY1 = new ECKeyPair();
		ECKeyPair NODE_KEY2 = new ECKeyPair();
		ECKeyPair NODE_KEY3 = new ECKeyPair();
		ECKeyPair NODE_KEY4 = new ECKeyPair();
		ECKeyPair NODE_KEY5 = new ECKeyPair();
		ECKeyPair NODE_KEY6 = new ECKeyPair();
		ECKeyPair NODE_KEY7 = new ECKeyPair();
		ECKeyPair NODE_KEY8 = new ECKeyPair();

		Hash object = Hash.random();

		TemporalProof temporalProof = new TemporalProof(AID.from(object.toByteArray()));
		TemporalVertex temporalVertex;

		temporalVertex = addVertex(temporalProof, NODE_KEY1, 10L, 1L, EUID.ZERO, NODE_KEY2.getUID());
		temporalVertex = addVertex(temporalProof, NODE_KEY2, 20L, 1L, temporalVertex.getHID(), NODE_KEY3.getUID());
		temporalVertex = addVertex(temporalProof, NODE_KEY3, 30L, 1L, temporalVertex.getHID(), NODE_KEY4.getUID());
		temporalVertex = addVertex(temporalProof, NODE_KEY4, 40L, 1L, temporalVertex.getHID(), NODE_KEY5.getUID());
		temporalVertex = addVertex(temporalProof, NODE_KEY5, 50L, 1L, temporalVertex.getHID(), NODE_KEY6.getUID());
		temporalVertex = addVertex(temporalProof, NODE_KEY6, 60L, 1L, temporalVertex.getHID(), NODE_KEY7.getUID());
		temporalVertex = addVertex(temporalProof, NODE_KEY7, 70L, 1L, temporalVertex.getHID(), NODE_KEY8.getUID());
		temporalVertex = addVertex(temporalProof, NODE_KEY8, 80L, 1L, temporalVertex.getHID());

		validate(temporalProof);

		TemporalProof subTemporalProof = temporalProof.getSubTemporalProof(4);
		Assert.assertEquals(4, subTemporalProof.size());
	}

	@Test
	public void getSubComplexTemporalProof() throws CryptoException, ValidationException
	{
		ECKeyPair NODE_KEY1 = new ECKeyPair();
		ECKeyPair NODE_KEY2 = new ECKeyPair();
		ECKeyPair NODE_KEY3 = new ECKeyPair();
		ECKeyPair NODE_KEY4 = new ECKeyPair();
		ECKeyPair NODE_KEY5 = new ECKeyPair();
		ECKeyPair NODE_KEY6 = new ECKeyPair();
		ECKeyPair NODE_KEY7 = new ECKeyPair();
		ECKeyPair NODE_KEY8 = new ECKeyPair();

		Hash object = Hash.random();

		TemporalProof temporalProof = new TemporalProof(AID.from(object.toByteArray()));
		TemporalVertex temporalVertex0    = addVertex(temporalProof, NODE_KEY1, 10L, 1L, EUID.ZERO, NODE_KEY2.getUID(), NODE_KEY3.getUID());
		TemporalVertex temporalVertex00   = addVertex(temporalProof, NODE_KEY2, 20L, 1L, temporalVertex0.getHID(), NODE_KEY4.getUID());
		TemporalVertex temporalVertex000  = addVertex(temporalProof, NODE_KEY4, 40L, 1L, temporalVertex00.getHID(), NODE_KEY5.getUID());
		addVertex(temporalProof, NODE_KEY5, 60L, 1L, temporalVertex000.getHID());
		TemporalVertex temporalVertex01   = addVertex(temporalProof, NODE_KEY3, 30L, 1L, temporalVertex0.getHID(), NODE_KEY6.getUID());
		TemporalVertex temporalVertex010  = addVertex(temporalProof, NODE_KEY6, 50L, 1L, temporalVertex01.getHID(), NODE_KEY7.getUID());
		TemporalVertex temporalVertex0100 = addVertex(temporalProof, NODE_KEY7, 70L, 1L, temporalVertex010.getHID(), NODE_KEY8.getUID());
		addVertex(temporalProof, NODE_KEY8, 80L, 1L, temporalVertex0100.getHID());

		validate(temporalProof);

		TemporalProof subTemporalProof = temporalProof.getSubTemporalProof(2);
		Assert.assertEquals(3, subTemporalProof.size());
	}

	@Test
	public void getLongestBranchFromComplexTemporalProof() throws CryptoException, ValidationException
	{
		ECKeyPair NODE_KEY1 = new ECKeyPair();
		ECKeyPair NODE_KEY2 = new ECKeyPair();
		ECKeyPair NODE_KEY3 = new ECKeyPair();
		ECKeyPair NODE_KEY4 = new ECKeyPair();
		Hash hash = new Hash("0000000000000000000000000000000000000000000000000000000000000000");

		TemporalProof temporalProof = new TemporalProof(AID.from(hash.toByteArray()));
		TemporalVertex temporalVertex = addVertex(temporalProof, NODE_KEY1, 10L, 1L, EUID.ZERO);
		addVertex(temporalProof, NODE_KEY2, 20L, 1L, temporalVertex.getHID());
		addVertex(temporalProof, NODE_KEY3, 30L, 1L, lastVertex(temporalProof).getHID());
		addVertex(temporalProof, NODE_KEY4, 10L, 1L, temporalVertex.getHID());

		validate(temporalProof);

		TemporalProof longestBranch = temporalProof.getLongestBranch();
		Assert.assertNotNull(longestBranch);
		Assert.assertTrue(longestBranch.size() == 3);

		Set<TemporalProof> branches = temporalProof.getBranches();
		for (TemporalProof branch : branches)
		{
			if (branch.equals(longestBranch))
				continue;

			Assert.assertTrue(longestBranch.size() >= branch.size());
		}
	}

	@Test
	public void getBranchFromComplexTemporalProof() throws CryptoException, ValidationException
	{
		ECKeyPair NODE_KEY1 = new ECKeyPair();
		ECKeyPair NODE_KEY2 = new ECKeyPair();
		ECKeyPair NODE_KEY3 = new ECKeyPair();
		ECKeyPair NODE_KEY4 = new ECKeyPair();
		Hash hash = new Hash("0000000000000000000000000000000000000000000000000000000000000000");

		TemporalProof temporalProof = new TemporalProof(AID.from(hash.toByteArray()));
		TemporalVertex temporalVertex = addVertex(temporalProof, NODE_KEY1, 10L, 1L, EUID.ZERO);
		addVertex(temporalProof, NODE_KEY2, 20L, 1L, temporalVertex.getHID());
		addVertex(temporalProof, NODE_KEY3, 20L, 1L, lastVertex(temporalProof).getHID());
		addVertex(temporalProof, NODE_KEY4, 20L, 1L, temporalVertex.getHID());

		validate(temporalProof);

		TemporalProof branch1 = temporalProof.getBranch(temporalProof.getVertexByNID(NODE_KEY3.getUID()), true);
		Assert.assertNotNull(branch1);
		Assert.assertEquals(3, branch1.size());

		TemporalProof branch2 = temporalProof.getBranch(temporalProof.getVertexByNID(NODE_KEY4.getUID()), true);
		Assert.assertNotNull(branch2);
		Assert.assertEquals(2, branch2.size());
	}

	@Test
	public void attemptDuplicateNIDVertexInsert() throws CryptoException, ValidationException
	{
		ECKeyPair NODE_KEY1 = new ECKeyPair();
		ECKeyPair NODE_KEY2 = new ECKeyPair();
		Hash object = Hash.random();

		TemporalProof temporalProof = new TemporalProof(AID.from(object.toByteArray()));
		TemporalVertex temporalVertex;
		temporalVertex = addVertex(temporalProof, NODE_KEY1, 10L, 1L, EUID.ZERO);
		temporalVertex = addVertex(temporalProof, NODE_KEY2, 20L, 1L, temporalVertex.getHID());

		// DUPLICATE //
		temporalVertex = addVertex(temporalProof, NODE_KEY1, 30L, 1L, temporalVertex.getHID());

		validate(temporalProof);
	}

	private TemporalVertex addVertex(TemporalProof temporalProof, ECKeyPair nodeKey, long clock, long rclock, EUID previous, EUID... nids)
		throws ValidationException, CryptoException {
		TemporalVertex newVertex = new TemporalVertex(nodeKey.getPublicKey(), clock, rclock, Hash.ZERO_HASH, previous, nids);
		temporalProof.add(newVertex, nodeKey);
		return newVertex;
	}

	private TemporalVertex lastVertex(TemporalProof tp) {
		List<TemporalVertex> vertices = tp.getVertices();
		return vertices.get(vertices.size() - 1);
	}
}
