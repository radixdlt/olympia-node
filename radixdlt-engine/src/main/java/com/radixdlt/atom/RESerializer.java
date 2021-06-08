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

import com.radixdlt.atommodel.system.state.EpochData;
import com.radixdlt.atommodel.system.state.RoundData;
import com.radixdlt.atommodel.system.state.StakeOwnership;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atommodel.system.state.ValidatorEpochData;
import com.radixdlt.atommodel.tokens.state.ExittingStake;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.system.state.ValidatorStake;
import com.radixdlt.atommodel.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.atommodel.tokens.state.TokenResource;
import com.radixdlt.atommodel.tokens.state.TokensInAccount;
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
		PREPARED_STAKE((byte) 4),
		VALIDATOR((byte) 5),
		UNIQUE((byte) 6),
		TOKENS_LOCKED((byte) 7),
		STAKE((byte) 8),
		ROUND_DATA((byte) 9),
		EPOCH_DATA((byte) 10),
		STAKE_SHARE((byte) 11),
		VALIDATOR_EPOCH_DATA((byte) 12),
		PREPARED_UNSTAKE((byte) 13),
		EXITTING_STAKE((byte) 14);

		private final byte id;

		SubstateType(byte id) {
			this.id = id;
		}
	}

	private static List<Class<? extends Particle>> byteToClass = List.of(
		REAddrParticle.class,
		SystemParticle.class,
		TokenResource.class,
		TokensInAccount.class,
		PreparedStake.class,
		ValidatorParticle.class,
		UniqueParticle.class,
		TokensInAccount.class,
		ValidatorStake.class,
		RoundData.class,
		EpochData.class,
		StakeOwnership.class,
		ValidatorEpochData.class,
		PreparedUnstakeOwnership.class,
		ExittingStake.class
	);

	private static Map<Class<? extends Particle>, List<Byte>> classToByteTypes = Map.ofEntries(
		Map.entry(REAddrParticle.class, List.of(SubstateType.RE_ADDR.id)),
		Map.entry(SystemParticle.class, List.of(SubstateType.SYSTEM.id)),
		Map.entry(TokenResource.class, List.of(SubstateType.TOKEN_DEF.id)),
		Map.entry(TokensInAccount.class, List.of(SubstateType.TOKENS.id, SubstateType.TOKENS_LOCKED.id)),
		Map.entry(PreparedStake.class, List.of(SubstateType.PREPARED_STAKE.id)),
		Map.entry(ValidatorParticle.class, List.of(SubstateType.VALIDATOR.id)),
		Map.entry(UniqueParticle.class, List.of(SubstateType.UNIQUE.id)),
		Map.entry(ValidatorStake.class, List.of(SubstateType.STAKE.id)),
		Map.entry(RoundData.class, List.of(SubstateType.ROUND_DATA.id)),
		Map.entry(EpochData.class, List.of(SubstateType.EPOCH_DATA.id)),
		Map.entry(StakeOwnership.class, List.of(SubstateType.STAKE_SHARE.id)),
		Map.entry(ValidatorEpochData.class, List.of(SubstateType.VALIDATOR_EPOCH_DATA.id)),
		Map.entry(PreparedUnstakeOwnership.class, List.of(SubstateType.PREPARED_UNSTAKE.id)),
		Map.entry(ExittingStake.class, List.of(SubstateType.EXITTING_STAKE.id))
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

	public static Class<? extends Particle> byteToClass(byte classId) throws DeserializeException {
		if (classId < 0 || classId >= byteToClass.size()) {
			throw new DeserializeException("Bad classId");
		}
		return byteToClass.get(classId);
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
		} else if (type == SubstateType.PREPARED_STAKE.id) {
			return deserializePreparedStake(buf);
		} else if (type == SubstateType.VALIDATOR.id) {
			return deserializeValidatorParticle(buf);
		} else if (type == SubstateType.UNIQUE.id) {
			return deserializeUniqueParticle(buf);
		} else if (type == SubstateType.TOKEN_DEF.id) {
			return deserializeTokenDefinitionParticle(buf);
		} else if (type == SubstateType.STAKE.id) {
			return deserializeStake(buf);
		} else if (type == SubstateType.ROUND_DATA.id) {
			return deserializeRoundState(buf);
		} else if (type == SubstateType.EPOCH_DATA.id) {
			return deserializeEpochState(buf);
		} else if (type == SubstateType.STAKE_SHARE.id) {
			return deserializeStakeShare(buf);
		} else if (type == SubstateType.VALIDATOR_EPOCH_DATA.id) {
			return deserializeValidatorEpochData(buf);
		} else if (type == SubstateType.PREPARED_UNSTAKE.id) {
			return deserializePreparedUnstake(buf);
		} else if (type == SubstateType.EXITTING_STAKE.id) {
			return deserializeExittingStake(buf);
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
		} else if (p instanceof TokensInAccount) {
			serializeData((TokensInAccount) p, buf);
		} else if (p instanceof PreparedStake) {
			serializeData((PreparedStake) p, buf);
		} else if (p instanceof ValidatorParticle) {
			serializeData((ValidatorParticle) p, buf);
		} else if (p instanceof UniqueParticle) {
			serializeData((UniqueParticle) p, buf);
		} else if (p instanceof TokenResource) {
			serializeData((TokenResource) p, buf);
		} else if (p instanceof ValidatorStake) {
			serializeData((ValidatorStake) p, buf);
		} else if (p instanceof RoundData) {
			serializeData((RoundData) p, buf);
		} else if (p instanceof EpochData) {
			serializeData((EpochData) p, buf);
		} else if (p instanceof StakeOwnership) {
			serializeData((StakeOwnership) p, buf);
		} else if (p instanceof ValidatorEpochData) {
			serializeData((ValidatorEpochData) p, buf);
		} else if (p instanceof PreparedUnstakeOwnership) {
			serializeData((PreparedUnstakeOwnership) p, buf);
		} else if (p instanceof ExittingStake) {
			serializeData((ExittingStake) p, buf);
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

	private static void serializeData(RoundData roundData, ByteBuffer buf) {
		buf.put(SubstateType.ROUND_DATA.id);
		buf.putLong(roundData.getView());
		buf.putLong(roundData.getTimestamp());
	}

	private static RoundData deserializeRoundState(ByteBuffer buf) throws DeserializeException {
		var view = buf.getLong();
		var timestamp = buf.getLong();
		return new RoundData(view, timestamp);
	}

	private static void serializeData(EpochData epochData, ByteBuffer buf) {
		buf.put(SubstateType.EPOCH_DATA.id);
		buf.putLong(epochData.getEpoch());
	}

	private static EpochData deserializeEpochState(ByteBuffer buf) throws DeserializeException {
		var epoch = buf.getLong();
		return new EpochData(epoch);
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

	private static void serializeData(TokensInAccount tokensInAccount, ByteBuffer buf) {
		tokensInAccount.getEpochUnlocked().ifPresentOrElse(
			e -> buf.put(SubstateType.TOKENS_LOCKED.id),
			() -> buf.put(SubstateType.TOKENS.id)
		);

		serializeREAddr(buf, tokensInAccount.getResourceAddr());
		serializeREAddr(buf, tokensInAccount.getHoldingAddr());
		buf.put(tokensInAccount.getAmount().toByteArray());

		tokensInAccount.getEpochUnlocked().ifPresent(buf::putLong);
	}

	private static TokensInAccount deserializeTokensParticle(ByteBuffer buf) throws DeserializeException {
		var rri = deserializeREAddr(buf);
		var holdingAddr = deserializeREAddr(buf);
		var amount = deserializeUInt256(buf);

		return new TokensInAccount(holdingAddr, amount, rri);
	}

	private static TokensInAccount deserializeTokensLockedParticle(ByteBuffer buf) throws DeserializeException {
		var rri = deserializeREAddr(buf);
		var holdingAddr = deserializeREAddr(buf);
		var amount = deserializeUInt256(buf);
		var epochUnlocked = buf.getLong();

		return new TokensInAccount(holdingAddr, amount, rri, epochUnlocked);
	}

	private static void serializeData(ValidatorStake stake, ByteBuffer buf) {
		buf.put(SubstateType.STAKE.id);
		serializeKey(buf, stake.getValidatorKey());
		buf.put(stake.getTotalStake().toByteArray());
		buf.put(stake.getTotalOwnership().toByteArray());
	}

	private static ValidatorStake deserializeStake(ByteBuffer buf) throws DeserializeException {
		var delegate = deserializeKey(buf);
		var amount = deserializeUInt256(buf);
		var ownership = deserializeUInt256(buf);
		return ValidatorStake.create(delegate, amount, ownership);
	}

	private static void serializeData(StakeOwnership p, ByteBuffer buf) {
		buf.put(SubstateType.STAKE_SHARE.id);

		serializeKey(buf, p.getDelegateKey());
		serializeREAddr(buf, p.getOwner());
		buf.put(p.getAmount().toByteArray());
	}

	private static StakeOwnership deserializeStakeShare(ByteBuffer buf) throws DeserializeException {
		var delegate = deserializeKey(buf);
		var owner = deserializeREAddr(buf);
		var amount = deserializeUInt256(buf);
		return new StakeOwnership(delegate, owner, amount);
	}

	private static void serializeData(ValidatorEpochData p, ByteBuffer buf) {
		buf.put(SubstateType.VALIDATOR_EPOCH_DATA.id);
		serializeKey(buf, p.validatorKey());
		buf.putLong(p.proposalsCompleted());
	}

	private static ValidatorEpochData deserializeValidatorEpochData(ByteBuffer buf) throws DeserializeException {
		var key = deserializeKey(buf);
		var proposalCsCompleted = buf.getLong();
		return new ValidatorEpochData(key, proposalCsCompleted);
	}

	private static void serializeData(ExittingStake p, ByteBuffer buf) {
		buf.put(SubstateType.EXITTING_STAKE.id);
		buf.putLong(p.getEpochUnlocked());
		serializeKey(buf, p.getDelegateKey());
		serializeREAddr(buf, p.getOwner());
		buf.put(p.getAmount().toByteArray());
	}

	private static ExittingStake deserializeExittingStake(ByteBuffer buf) throws DeserializeException {
		var epochUnlocked = buf.getLong();
		var delegate = deserializeKey(buf);
		var owner = deserializeREAddr(buf);
		var amount = deserializeUInt256(buf);
		return new ExittingStake(delegate, owner, epochUnlocked, amount);
	}


	private static void serializeData(PreparedUnstakeOwnership p, ByteBuffer buf) {
		buf.put(SubstateType.PREPARED_UNSTAKE.id);

		serializeKey(buf, p.getDelegateKey());
		serializeREAddr(buf, p.getOwner());
		buf.put(p.getAmount().toByteArray());
	}

	private static PreparedUnstakeOwnership deserializePreparedUnstake(ByteBuffer buf) throws DeserializeException {
		var delegate = deserializeKey(buf);
		var owner = deserializeREAddr(buf);
		var amount = deserializeUInt256(buf);
		return new PreparedUnstakeOwnership(delegate, owner, amount);
	}

	private static void serializeData(PreparedStake p, ByteBuffer buf) {
		buf.put(SubstateType.PREPARED_STAKE.id);

		serializeREAddr(buf, p.getOwner());
		serializeKey(buf, p.getDelegateKey());
		buf.put(p.getAmount().toByteArray());
	}

	private static PreparedStake deserializePreparedStake(ByteBuffer buf) throws DeserializeException {
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

	private static void serializeData(TokenResource p, ByteBuffer buf) {
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

	private static TokenResource deserializeTokenDefinitionParticle(ByteBuffer buf) throws DeserializeException {
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
		return new TokenResource(rri, name, description, iconUrl, url, supply, minter);
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
