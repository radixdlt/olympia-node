package com.radixdlt.serialization;

import java.util.ArrayList;
import java.util.List;

import com.radixdlt.crypto.Hash;
import org.radix.crypto.MerkleTree;

/**
 * Check serialization of MerkleTree
 */
public class MerkleTreeSerializeTest extends SerializeObject<MerkleTree> {
	public MerkleTreeSerializeTest() {
		super(MerkleTree.class, MerkleTreeSerializeTest::get);
	}

	private static MerkleTree get() {
		List<Hash> hashes = new ArrayList<>();
		for (int i = 0; i < 16; ++i) {
			hashes.add(Hash.random());
		}
		return new MerkleTree(hashes);
	}
}
