package com.radixdlt.store.tree;

import com.radixdlt.crypto.HashUtils;
import org.spongycastle.crypto.digests.SHA3Digest;

public abstract class PMTNode implements Cloneable {

	public enum NodeType {
		LEAF,
		EXTENSION,
		BRANCH,
		EMPTY
	}

	public static final int DB_SIZE_COND = 32;

	protected byte[] hash;
	protected byte[] serialized;
	protected NodeType nodeType;
	protected PMTKey branchNibble;
	protected PMTKey keyNibbles;
	protected byte[] value;

	protected PMTKey getBranchNibble() {
		return branchNibble;
	}

	protected PMTNode hash() {
		// TODO: use a dirty flag or wrapper to avoid re-serializing
		var ser = serialize();
		if (ser.length >= DB_SIZE_COND) {
			this.hash = sha3(ser);
		} else {
			this.hash = ser;
		}
		return this;
	}

	private byte[] sha3(byte[] data) {
		SHA3Digest sha3Digest = new SHA3Digest(256);
		byte[] hashed = new byte[sha3Digest.getDigestSize()];
		if (data.length != 0) {
			sha3Digest.update(data, 0, data.length);
		}
		sha3Digest.doFinal(hashed, 0);
		return hashed;
	}

	public byte[] getHash() {
		// TODO: introduce isDirty/null-on-write to avoid hashing and serialization
		this.hash();
		return this.hash;
	}

	public PMTKey getKey() {
		return this.keyNibbles;
	}

	public NodeType getNodeType() {
		return nodeType;
	}

	public byte[] getValue() {
		return value;
	}

	public PMTNode setValue(byte[] value) {
		if (this.value == value) {
			throw new IllegalArgumentException("Nothing changed");
		} else {
			this.value = value;
		}
		return this;
	}

	public abstract byte[] serialize();

	public static PMTNode deserialize(byte[] node) {
		// 1. use deserialization protocol e.g. RLP
		// 2. classify a node (leaf, branch, ext)
		//    ...Branch vs Leaf/Exp
		//    ...use Prefix for Leaf vs Exp
		// 3. instantiate with arguments
		return null;
	}
}
