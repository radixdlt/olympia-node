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
import com.radixdlt.atomos.UnclaimedREAddr;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.UInt256;

import java.nio.ByteBuffer;
import java.util.regex.Pattern;

public final class RESerializer {
	private static final Pattern OWASP_URL_REGEX = Pattern.compile(
		"^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))"
			+ "(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,A-Za-z0-9])+)([).!';/?:,][[:blank:]])?$"
	);

	public enum SubstateType {
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
		STAKE_OWNERSHIP((byte) 11),
		VALIDATOR_EPOCH_DATA((byte) 12),
		PREPARED_UNSTAKE((byte) 13),
		EXITTING_STAKE((byte) 14);

		private final byte id;

		SubstateType(byte id) {
			this.id = id;
		}

		public byte id() {
			return id;
		}
	}

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

	public static byte[] serialize(Particle p) {
		var buf = ByteBuffer.allocate(1024);
		if (p instanceof UnclaimedREAddr) {
			serializeData((UnclaimedREAddr) p, buf);
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

	private static void serializeData(UnclaimedREAddr rriParticle, ByteBuffer buf) {
		buf.put(SubstateType.RE_ADDR.id);

		var rri = rriParticle.getAddr();
		serializeREAddr(buf, rri);
	}

	private static void serializeData(RoundData roundData, ByteBuffer buf) {
		buf.put(SubstateType.ROUND_DATA.id);
		buf.putLong(roundData.getView());
		buf.putLong(roundData.getTimestamp());
	}

	private static void serializeData(EpochData epochData, ByteBuffer buf) {
		buf.put(SubstateType.EPOCH_DATA.id);
		buf.putLong(epochData.getEpoch());
	}

	private static void serializeData(SystemParticle systemParticle, ByteBuffer buf) {
		buf.put(SubstateType.SYSTEM.id);
		buf.putLong(systemParticle.getEpoch());
		buf.putLong(systemParticle.getView());
		buf.putLong(systemParticle.getTimestamp());
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

	private static void serializeData(ValidatorStake stake, ByteBuffer buf) {
		buf.put(SubstateType.STAKE.id);
		serializeKey(buf, stake.getValidatorKey());
		buf.put(stake.getAmount().toByteArray());
		buf.put(stake.getTotalOwnership().toByteArray());
	}

	private static void serializeData(StakeOwnership p, ByteBuffer buf) {
		buf.put(SubstateType.STAKE_OWNERSHIP.id);

		serializeKey(buf, p.getDelegateKey());
		serializeREAddr(buf, p.getOwner());
		buf.put(p.getAmount().toByteArray());
	}

	private static void serializeData(ValidatorEpochData p, ByteBuffer buf) {
		buf.put(SubstateType.VALIDATOR_EPOCH_DATA.id);
		serializeKey(buf, p.validatorKey());
		buf.putLong(p.proposalsCompleted());
	}

	private static void serializeData(ExittingStake p, ByteBuffer buf) {
		buf.put(SubstateType.EXITTING_STAKE.id);
		buf.putLong(p.getEpochUnlocked());
		serializeKey(buf, p.getDelegateKey());
		serializeREAddr(buf, p.getOwner());
		buf.put(p.getAmount().toByteArray());
	}

	private static void serializeData(PreparedUnstakeOwnership p, ByteBuffer buf) {
		buf.put(SubstateType.PREPARED_UNSTAKE.id);

		serializeKey(buf, p.getDelegateKey());
		serializeREAddr(buf, p.getOwner());
		buf.put(p.getAmount().toByteArray());
	}

	private static void serializeData(PreparedStake p, ByteBuffer buf) {
		buf.put(SubstateType.PREPARED_STAKE.id);

		serializeREAddr(buf, p.getOwner());
		serializeKey(buf, p.getDelegateKey());
		buf.put(p.getAmount().toByteArray());
	}

	private static void serializeData(ValidatorParticle p, ByteBuffer buf) {
		buf.put(SubstateType.VALIDATOR.id);

		serializeKey(buf, p.getKey());
		buf.put((byte) (p.isRegisteredForNextEpoch() ? 1 : 0)); // isRegistered
		serializeString(buf, p.getName());
		serializeString(buf, p.getUrl());
	}

	private static void serializeData(UniqueParticle uniqueParticle, ByteBuffer buf) {
		buf.put(SubstateType.UNIQUE.id);

		serializeREAddr(buf, uniqueParticle.getREAddr());
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
				p.getOwner().ifPresentOrElse(
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

	public static Long deserializeNonNegativeLong(ByteBuffer buf) throws DeserializeException {
		var l = buf.getLong();
		if (l < 0) {
			throw new DeserializeException("Long must be positive");
		}
		return l;
	}

	public static UInt256 deserializeUInt256(ByteBuffer buf) {
		var amountDest = new byte[UInt256.BYTES]; // amount
		buf.get(amountDest);
		return UInt256.from(amountDest);
	}

	public static UInt256 deserializeNonZeroUInt256(ByteBuffer buf) throws DeserializeException {
		var amountDest = new byte[UInt256.BYTES]; // amount
		buf.get(amountDest);
		var uint256 = UInt256.from(amountDest);
		if (uint256.isZero()) {
			throw new DeserializeException("Cannot be zero.");
		}
		return uint256;
	}

	private static void serializeKey(ByteBuffer buf, ECPublicKey key) {
		buf.put(key.getCompressedBytes()); // address
	}

	public static ECPublicKey deserializeKey(ByteBuffer buf) throws DeserializeException {
		try {
			var keyBytes = new byte[33];
			buf.get(keyBytes);
			return ECPublicKey.fromBytes(keyBytes);
		} catch (PublicKeyException | IllegalArgumentException e) {
			throw new DeserializeException("Could not deserialize key");
		}
	}

	public static void serializeString(ByteBuffer buf, String s) {
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

	public static String deserializeUrl(ByteBuffer buf) throws DeserializeException {
		var len = Byte.toUnsignedInt(buf.get()); // url
		var dest = new byte[len];
		buf.get(dest);
		var url = new String(dest, RadixConstants.STANDARD_CHARSET);
		if (!url.isEmpty() && !OWASP_URL_REGEX.matcher(url).matches()) {
			throw new DeserializeException("URL: not a valid URL: " + url);
		}
		return url;
	}
}
