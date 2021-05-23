/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.atom;

import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.system.state.Stake;
import com.radixdlt.atommodel.tokens.state.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.state.TokensParticle;
import com.radixdlt.atommodel.unique.state.UniqueParticle;
import com.radixdlt.atommodel.validators.state.ValidatorParticle;
import com.radixdlt.atomos.REAddrParticle;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.UInt256;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

public final class RESerializer {
	private enum SubstateType {
		RE_ADDR((byte) 0),
		SYSTEM((byte) 1),
		TOKEN_DEF((byte) 2),
		TOKENS((byte) 3),
		DELEGATED_STAKE((byte) 4),
		VALIDATOR((byte) 5),
		UNIQUE((byte) 6),
		TOKENS_LOCKED((byte) 7),
		STAKE((byte) 8);

		private final byte id;

		SubstateType(byte id) {
			this.id = id;
		}
	}

	private static Map<Class<? extends Particle>, List<Byte>> classToByteTypes = Map.of(
		REAddrParticle.class, List.of(SubstateType.RE_ADDR.id),
		SystemParticle.class, List.of(SubstateType.SYSTEM.id),
		TokenDefinitionParticle.class, List.of(SubstateType.TOKEN_DEF.id),
		TokensParticle.class, List.of(SubstateType.TOKENS.id, SubstateType.TOKENS_LOCKED.id),
		PreparedStake.class, List.of(SubstateType.DELEGATED_STAKE.id),
		ValidatorParticle.class, List.of(SubstateType.VALIDATOR.id),
		UniqueParticle.class, List.of(SubstateType.UNIQUE.id),
		Stake.class, List.of(SubstateType.STAKE.id)
	);

	private RESerializer() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static byte[] serializeSignature(ECDSASignature signature) {
		var buf = ByteBuffer.allocate(32 * 2 + 1);
		buf.put(signature.getV());
		var rArray = signature.getR().toByteArray();
		var r = rArray.length > 32 ? UInt256.from(rArray, 1) : UInt256.from(rArray);
		buf.put(r.toByteArray());
		var sArray = signature.getS().toByteArray();
		var s = sArray.length > 32 ? UInt256.from(sArray, 1) : UInt256.from(sArray);
		buf.put(s.toByteArray());

		return buf.array();
	}

	public static ECDSASignature deserializeSignature(ByteBuffer buf) {
		var v = buf.get();
		var rArray = new byte[32];
		buf.get(rArray);
		var sArray = new byte[32];
		buf.get(sArray);
		return ECDSASignature.deserialize(rArray, sArray, v);
	}

	public static List<Byte> classToBytes(Class<? extends Particle> particleClass) {
		return classToByteTypes.get(particleClass);
	}

	public static Particle deserialize(byte[] bytes) throws DeserializeException {
		return deserialize(ByteBuffer.wrap(bytes));
	}

	public static Particle deserialize(ByteBuffer buf) throws DeserializeException {
		var type = buf.get();
		if (type == SubstateType.RE_ADDR.id) {
			return deserializeRRIParticle(buf);
		} else if (type == SubstateType.SYSTEM.id) {
			return deserializeSystemParticle(buf);
		} else if (type == SubstateType.TOKENS.id) {
			return deserializeTokensParticle(buf);
		} else if (type == SubstateType.TOKENS_LOCKED.id) {
			return deserializeTokensLockedParticle(buf);
		} else if (type == SubstateType.DELEGATED_STAKE.id) {
			return deserializeStakedTokensParticle(buf);
		} else if (type == SubstateType.VALIDATOR.id) {
			return deserializeValidatorParticle(buf);
		} else if (type == SubstateType.UNIQUE.id) {
			return deserializeUniqueParticle(buf);
		} else if (type == SubstateType.TOKEN_DEF.id) {
			return deserializeTokenDefinitionParticle(buf);
		} else if (type == SubstateType.STAKE.id) {
			return deserializeStake(buf);
		} else {
			throw new DeserializeException("Unsupported type: " + type);
		}
	}

	public static byte[] serialize(Particle p) {
		var buf = ByteBuffer.allocate(1024);
		if (p instanceof REAddrParticle) {
			serializeData((REAddrParticle) p, buf);
		} else if (p instanceof SystemParticle) {
			serializeData((SystemParticle) p, buf);
		} else if (p instanceof TokensParticle) {
			serializeData((TokensParticle) p, buf);
		} else if (p instanceof PreparedStake) {
			serializeData((PreparedStake) p, buf);
		} else if (p instanceof ValidatorParticle) {
			serializeData((ValidatorParticle) p, buf);
		} else if (p instanceof UniqueParticle) {
			serializeData((UniqueParticle) p, buf);
		} else if (p instanceof TokenDefinitionParticle) {
			serializeData((TokenDefinitionParticle) p, buf);
		} else if (p instanceof Stake) {
			serializeData((Stake) p, buf);
		} else {
			throw new IllegalStateException("Unknown particle: " + p);
		}

		var position = buf.position();
		buf.rewind();
		var bytes = new byte[position];
		buf.get(bytes);

		return bytes;
	}

	public static void serializeREAddr(ByteBuffer buf, REAddr rri) {
		buf.put(rri.getBytes());
	}

	public static REAddr deserializeREAddr(ByteBuffer buf) throws DeserializeException {
		var v = buf.get(); // version
		var type = REAddr.REAddrType.parse(v);
		if (type.isEmpty()) {
			throw new DeserializeException("Unknown address type " + v);
		}
		return type.get().parse(buf);
	}

	private static void serializeData(REAddrParticle rriParticle, ByteBuffer buf) {
		buf.put(SubstateType.RE_ADDR.id);

		var rri = rriParticle.getAddr();
		serializeREAddr(buf, rri);
	}

	private static REAddrParticle deserializeRRIParticle(ByteBuffer buf) throws DeserializeException {
		var rri = deserializeREAddr(buf);
		return new REAddrParticle(rri);
	}

	private static void serializeData(SystemParticle systemParticle, ByteBuffer buf) {
		buf.put(SubstateType.SYSTEM.id);

		buf.putLong(systemParticle.getEpoch());
		buf.putLong(systemParticle.getView());
		buf.putLong(systemParticle.getTimestamp());
	}

	private static SystemParticle deserializeSystemParticle(ByteBuffer buf) throws DeserializeException {
		var epoch = buf.getLong();
		var view = buf.getLong();
		var timestamp = buf.getLong();
		return new SystemParticle(epoch, view, timestamp);
	}

	private static void serializeData(TokensParticle tokensParticle, ByteBuffer buf) {
		tokensParticle.getEpochUnlocked().ifPresentOrElse(
			e -> buf.put(SubstateType.TOKENS_LOCKED.id),
			() -> buf.put(SubstateType.TOKENS.id)
		);

		serializeREAddr(buf, tokensParticle.getResourceAddr());
		serializeREAddr(buf, tokensParticle.getHoldingAddr());
		buf.put(tokensParticle.getAmount().toByteArray());

		tokensParticle.getEpochUnlocked().ifPresent(buf::putLong);
	}

	private static TokensParticle deserializeTokensParticle(ByteBuffer buf) throws DeserializeException {
		var rri = deserializeREAddr(buf);
		var holdingAddr = deserializeREAddr(buf);
		var amount = deserializeUInt256(buf);

		return new TokensParticle(holdingAddr, amount, rri);
	}

	private static TokensParticle deserializeTokensLockedParticle(ByteBuffer buf) throws DeserializeException {
		var rri = deserializeREAddr(buf);
		var holdingAddr = deserializeREAddr(buf);
		var amount = deserializeUInt256(buf);
		var epochUnlocked = buf.getLong();

		return new TokensParticle(holdingAddr, amount, rri, epochUnlocked);
	}

	private static void serializeData(Stake stake, ByteBuffer buf) {
		buf.put(SubstateType.STAKE.id);
		serializeKey(buf, stake.getValidatorKey());
		buf.put(stake.getAmount().toByteArray());
	}

	private static Stake deserializeStake(ByteBuffer buf) throws DeserializeException {
		var delegate = deserializeKey(buf);
		var amount = deserializeUInt256(buf);
		return new Stake(amount, delegate);
	}

	private static void serializeData(PreparedStake p, ByteBuffer buf) {
		buf.put(SubstateType.DELEGATED_STAKE.id);

		serializeREAddr(buf, p.getOwner());
		serializeKey(buf, p.getDelegateKey());
		buf.put(p.getAmount().toByteArray());
	}

	private static PreparedStake deserializeStakedTokensParticle(ByteBuffer buf) throws DeserializeException {
		var owner = deserializeREAddr(buf);
		var delegate = deserializeKey(buf);
		var amount = deserializeUInt256(buf);
		return new PreparedStake(amount, owner, delegate);
	}

	private static void serializeData(ValidatorParticle p, ByteBuffer buf) {
		buf.put(SubstateType.VALIDATOR.id);

		serializeKey(buf, p.getKey());
		buf.put((byte) (p.isRegisteredForNextEpoch() ? 1 : 0)); // isRegistered
		serializeString(buf, p.getName());
		serializeString(buf, p.getUrl());
	}

	private static ValidatorParticle deserializeValidatorParticle(ByteBuffer buf) throws DeserializeException {
		var key = deserializeKey(buf);
		var isRegistered = buf.get() != 0; // isRegistered
		var name = deserializeString(buf);
		var url = deserializeString(buf);
		return new ValidatorParticle(key, isRegistered, name, url);
	}

	private static void serializeData(UniqueParticle uniqueParticle, ByteBuffer buf) {
		buf.put(SubstateType.UNIQUE.id);

		serializeREAddr(buf, uniqueParticle.getREAddr());
	}

	private static UniqueParticle deserializeUniqueParticle(ByteBuffer buf) throws DeserializeException {
		var rri = deserializeREAddr(buf);
		return new UniqueParticle(rri);
	}

	private static void serializeData(TokenDefinitionParticle p, ByteBuffer buf) {
		buf.put(SubstateType.TOKEN_DEF.id);

		serializeREAddr(buf, p.getAddr());
		p.getSupply().ifPresentOrElse(
			i -> {
				buf.put((byte) 2);
				buf.put(i.toByteArray());
			},
			() -> {
				p.getMinter().ifPresentOrElse(
					m -> {
						buf.put((byte) 1);
						serializeKey(buf, m);
					},
					() -> buf.put((byte) 0)
				);
			}
		);
		serializeString(buf, p.getName());
		serializeString(buf, p.getDescription());
		serializeString(buf, p.getUrl());
		serializeString(buf, p.getIconUrl());
	}

	private static TokenDefinitionParticle deserializeTokenDefinitionParticle(ByteBuffer buf) throws DeserializeException {
		var rri = deserializeREAddr(buf);
		var type = buf.get();
		final UInt256 supply;
		final ECPublicKey minter;
		if (type == 0) {
			supply = null;
			minter = null;
		} else if (type == 1) {
			supply = null;
			minter = deserializeKey(buf);
		} else if (type == 2) {
			supply = deserializeUInt256(buf);
			minter = null;
		} else {
			throw new DeserializeException("Unknown token def type " + type);
		}
		var name = deserializeString(buf);
		var description = deserializeString(buf);
		var url = deserializeString(buf);
		var iconUrl = deserializeString(buf);
		return new TokenDefinitionParticle(rri, name, description, iconUrl, url, supply, minter);
	}

	private static UInt256 deserializeUInt256(ByteBuffer buf) {
		var amountDest = new byte[UInt256.BYTES]; // amount
		buf.get(amountDest);
		return UInt256.from(amountDest);
	}

	private static void serializeKey(ByteBuffer buf, ECPublicKey key) {
		buf.put(key.getCompressedBytes()); // address
	}

	private static ECPublicKey deserializeKey(ByteBuffer buf) throws DeserializeException {
		try {
			var keyBytes = new byte[33];
			buf.get(keyBytes);
			return ECPublicKey.fromBytes(keyBytes);
		} catch (PublicKeyException | IllegalArgumentException e) {
			throw new DeserializeException("Could not deserialize key");
		}
	}

	private static void serializeString(ByteBuffer buf, String s) {
		var sBytes = s.getBytes(RadixConstants.STANDARD_CHARSET);
		if (sBytes.length > 255) {
			throw new IllegalArgumentException("string cannot be greater than 255 chars");
		}
		var len = (byte) sBytes.length;
		buf.put(len); // url length
		buf.put(sBytes); // url
	}

	public static String deserializeString(ByteBuffer buf) {
		var len = Byte.toUnsignedInt(buf.get()); // url
		var dest = new byte[len];
		buf.get(dest);
		return new String(dest, RadixConstants.STANDARD_CHARSET);
	}
}
