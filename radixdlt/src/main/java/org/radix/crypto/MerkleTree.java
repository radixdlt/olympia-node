package org.radix.crypto;

import com.radixdlt.crypto.Hash;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.radix.collections.WireableList;
import org.radix.containers.BasicContainer;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * MerkleTree is an implementation of a Merkle binary hash tree where the leaves
 * are signatures (hashes, digests, CRCs, etc.) of some underlying data
 * structure that is not explicitly part of the tree.
 *
 * The internal leaves of the tree are signatures of its two child nodes. If an
 * internal node has only one child, the the signature of the child node is
 * adopted ("promoted").
 */
@SerializerId2("crypto.merkle_tree")
//@JsonDeserialize(converter = MerkleTreeConstructor.class)
public class MerkleTree extends BasicContainer
{
	@Override
	public short VERSION() { return 100; }

	public static final int 	MAGIC_HDR = 0xcdaace99;
	public static final int 	INT_BYTES = 4;
	public static final int 	LONG_BYTES = 8;
	public static final byte 	LEAF_SIG_TYPE = 0x0;
	public static final byte 	INTERNAL_SIG_TYPE = 0x01;

	@JsonProperty("leaves")
	@DsonOutput(Output.ALL)
	private WireableList<Hash> leaves;

	@JsonProperty("num_nodes")
	@DsonOutput(Output.ALL)
	private int 			nnodes;

	@JsonProperty("depth")
	@DsonOutput(Output.ALL)
	private int 			depth;

	private Node 			root;

	MerkleTree()
	{
		super();
	}

	/**
	 * Use this constructor to create a MerkleTree from a list of leaf hashes
	 * signatures. The Merkle tree is built from the bottom up.
	 *
	 * @param leaves
	 */
	public MerkleTree(List<Hash> leaves)
	{
		constructTree(new WireableList<>(leaves));
	}

	/**
	 * Create a tree from the bottom up starting from the leaf signatures.
	 */
	void constructTree(WireableList<Hash> hashes)
	{
		if (hashes.size() <= 1)
			throw new IllegalArgumentException("Must be at least two hashes to construct a Merkle tree");

		leaves = hashes;
		nnodes = hashes.size();
		List<Node> parents = bottomLevel(hashes);
		nnodes += parents.size();
		depth = 1;

		while (parents.size() > 1)
		{
			parents = internalLevel(parents);
			depth++;
			nnodes += parents.size();
		}

		root = parents.get(0);
	}

	public int getNumNodes()
	{
		return nnodes;
	}

	@Override
	public Hash getHash()
	{
		return root.hash;
	}

	public int getDepth()
	{
		return depth;
	}

	/**
	 * Constructs an internal level of the tree
	 */
	List<Node> internalLevel(List<Node> children)
	{
		List<Node> parents = new ArrayList<>(children.size() / 2);

		for (int i = 0; i < children.size() - 1; i += 2)
		{
			Node child1 = children.get(i);
			Node child2 = children.get(i + 1);

			Node parent = constructInternalNode(child1, child2);
			parents.add(parent);
		}

		if (children.size() % 2 != 0)
		{
			Node child = children.get(children.size() - 1);
			Node parent = constructInternalNode(child, null);
			parents.add(parent);
		}

		return parents;
	}

	/**
	 * Constructs the bottom part of the tree - the leaf nodes and their
	 * immediate parents. Returns a list of the parent nodes.
	 */
	List<Node> bottomLevel(List<Hash> hashes)
	{
		List<Node> parents = new ArrayList<>(hashes.size() / 2);

		for (int i = 0; i < hashes.size() - 1; i += 2)
		{
			Node leaf1 = constructLeafNode(hashes.get(i));
			Node leaf2 = constructLeafNode(hashes.get(i + 1));

			Node parent = constructInternalNode(leaf1, leaf2);
			parents.add(parent);
		}

		// if odd number of leafs, handle last entry
		if (hashes.size() % 2 != 0)
		{
			Node leaf = constructLeafNode(hashes.get(hashes.size() - 1));
			Node parent = constructInternalNode(leaf, null);
			parents.add(parent);
		}

		return parents;
	}

	private Node constructInternalNode(Node child1, Node child2)
	{
		Node parent = new Node();
		parent.type = INTERNAL_SIG_TYPE;

		if (child2 == null)
			parent.hash = child1.hash;
		else
			parent.hash = new Hash(internalHash(child1.hash.toByteArray(), child2.hash.toByteArray()));

		parent.left = child1;
		parent.right = child2;
		return parent;
	}

	private static Node constructLeafNode(Hash hash)
	{
		Node leaf = new Node();
		leaf.type = LEAF_SIG_TYPE;
		leaf.hash = hash;
		return leaf;
	}

	byte[] internalHash(byte[] left, byte[] right)
	{
		ByteBuffer buf = ByteBuffer.allocate(left.length+right.length);

		buf.put(left);
		buf.put(right);

		return Hash.hash256(buf.array());
	}

	/* ---[ Node class ]--- */

	/**
	 * The Node class should be treated as immutable, though immutable is not
	 * enforced in the current design.
	 *
	 * A Node knows whether it is an internal or leaf node and its hash.
	 *
	 * Internal Nodes will have at least one child (always on the left). Leaf
	 * Nodes will have no children (left = right = null).
	 */
	static class Node
	{
		byte 	type; // INTERNAL_SIG_TYPE or LEAF_SIG_TYPE
		Hash 	hash;
		Node 	left;
		Node 	right;

		@Override
		public String toString()
		{
			String leftType = "<null>";
			String rightType = "<null>";
			if (left != null)
				leftType = String.valueOf(left.type);

			if (right != null)
				rightType = String.valueOf(right.type);

			return String.format("MerkleTree.Node<type:%d, sig:%s, left (type): %s, right (type): %s>", type, hash.toString(), leftType, rightType);
		}
	}

	// Special setter for "leaves" that constructs the tree
	// correctly on deserialization.
	@JsonProperty("leaves")
	private void setJsonLeaves(WireableList<Hash> leaves) {
		constructTree(leaves);
	}
}