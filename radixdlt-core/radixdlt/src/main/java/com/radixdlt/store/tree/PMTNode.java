package com.radixdlt.store.tree;

import com.radixdlt.crypto.HashUtils;

import java.nio.ByteBuffer;

public abstract class PMTNode {

	public enum NodeType {
		LEAF,
		EXTENSION,
		BRANCH,
		EMPTY
	}

	public final static Boolean AFTER_BRANCH = true;
	public final static Boolean BEFORE_BRANCH = false;

	public final static int DB_SIZE_COND = 32;

	protected byte[] hash;
	protected byte[] serialized;
	protected NodeType nodeType;
	protected PMTKey key;
	protected byte[] firstNibble;
	protected byte[] value;

	protected PMTNode hash() {
		// TODO: use a dirty flag or wrapper to avoid re-serializing
		var ser = serialize();
		if (ser.length >= DB_SIZE_COND) {
			// TODO: what's the best hashing variant? Consider tree size
			//       Ethereum uses sha3 which is keccak-256
			this.hash = HashUtils.sha512(ser).asBytes();
		} else {
			this.hash = ser;
		}
		return this;
	}

	public byte[] getHash() {
		// TODO: introduce isDirty/null-on-write to avoid hashing and serialization
		this.hash();
		return this.hash;
	}

	public NodeType getNodeType() {
		return nodeType;
	}

	public byte[] getValue() {
		return value;
	}

	public PMTNode setValue(byte[] value) {
		this.value = value;
		return this;
	}

	// INFO: Ext and Leaf may have overlap nibble encoded in branch position
	public abstract byte[] serialize();

	public static PMTNode deserialize(byte[] node){
		// 1. use deserialization protocol e.g. RLP
		// 2. classify a node (leaf, branch, ext)
		//    ...Branch vs Leaf/Exp
		//    ...use Prefix for Leaf vs Exp
		// 3. instantiate with arguments
		return null;
	}
}
