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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokensParticle;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.atommodel.validators.ValidatorParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.nio.ByteBuffer;
import java.util.Map;

public final class SubstateSerializer {
	private static final BiMap<Class<? extends Particle>, Byte> classToByte = HashBiMap.create(Map.of(
		RRIParticle.class, (byte) 0,
		SystemParticle.class, (byte) 1,
		TokenDefinitionParticle.class, (byte) 2,
		TokensParticle.class, (byte) 3,
		StakedTokensParticle.class, (byte) 4,
		ValidatorParticle.class, (byte) 5,
		UniqueParticle.class, (byte) 6
	));

	private SubstateSerializer() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static byte classToByte(Class<? extends Particle> particleClass) {
		return classToByte.get(particleClass);
	}

	public static Particle deserialize(byte[] bytes) throws DeserializeException {
		var buf = ByteBuffer.wrap(bytes);
		var version = buf.get();
		if (version != 1) {
			throw new DeserializeException("Bad version: " + version);
		}
		var type = buf.get();
		var c = classToByte.inverse().get(type);
		if (c == null) {
			throw new DeserializeException("Bad type: " + type);
		}

		if (c == RRIParticle.class) {
			return deserializeRRIParticle(buf);
		} else if (c == SystemParticle.class) {
			return deserializeSystemParticle(buf);
		} else if (c == TokensParticle.class) {
			return deserializeTokensParticle(buf);
		} else if (c == StakedTokensParticle.class) {
			return deserializeStakedTokensParticle(buf);
		} else if (c == ValidatorParticle.class) {
			return deserializeValidatorParticle(buf);
		} else if (c == UniqueParticle.class) {
			return deserializeUniqueParticle(buf);
		} else if (c == TokenDefinitionParticle.class) {
			return deserializeTokenDefinitionParticle(buf);
		} else {
			throw new DeserializeException("Unsupported type: " + c);
		}
	}

	public static byte[] serialize(Particle p) {
		var buf = ByteBuffer.allocate(ConstraintMachine.DATA_MAX_SIZE);
		buf.put((byte) 1); // version
		buf.put(classToByte.get(p.getClass())); // substate type
		if (p instanceof RRIParticle) {
			serializeData((RRIParticle) p, buf);
		} else if (p instanceof SystemParticle) {
			serializeData((SystemParticle) p, buf);
		} else if (p instanceof TokensParticle) {
			serializeData((TokensParticle) p, buf);
		} else if (p instanceof StakedTokensParticle) {
			serializeData((StakedTokensParticle) p, buf);
		} else if (p instanceof ValidatorParticle) {
			serializeData((ValidatorParticle) p, buf);
		} else if (p instanceof UniqueParticle) {
			serializeData((UniqueParticle) p, buf);
		} else if (p instanceof TokenDefinitionParticle) {
			serializeData((TokenDefinitionParticle) p, buf);
		} else {
			throw new IllegalStateException("Unknown particle: " + p);
		}

		var position = buf.position();
		buf.rewind();
		var bytes = new byte[position];
		buf.get(bytes);

		return bytes;
	}

	public static Result<Particle> deserializeToResult(byte[] bytes) {
		try {
			return Result.ok(deserialize(bytes));
		} catch (DeserializeException e) {
			return Result.fail("Unable to deserialize {0}", Particle.class.getSimpleName());
		}
	}

	private static void serializeData(RRIParticle rriParticle, ByteBuffer buf) {
		var rri = rriParticle.getRri().toString().getBytes(RadixConstants.STANDARD_CHARSET);
		if (rri.length > 255) {
			throw new IllegalArgumentException("RRI cannot be greater than 255 chars");
		}
		var length = (byte) rri.length;
		buf.put(length); // length
		buf.put(rri); // rri
	}

	private static RRIParticle deserializeRRIParticle(ByteBuffer buf) {
		var length = Byte.toUnsignedInt(buf.get()); // length
		byte[] dst = new byte[length];
		buf.get(dst, 0, length);
		var rriString = new String(dst, RadixConstants.STANDARD_CHARSET);
		var rri = RRI.from(rriString);
		return new RRIParticle(rri);
	}

	private static void serializeData(SystemParticle systemParticle, ByteBuffer buf) {
		buf.putLong(systemParticle.getEpoch());
		buf.putLong(systemParticle.getView());
		buf.putLong(systemParticle.getTimestamp());
	}

	private static SystemParticle deserializeSystemParticle(ByteBuffer buf) {
		var epoch = buf.getLong();
		var view = buf.getLong();
		var timestamp = buf.getLong();

		return new SystemParticle(epoch, view, timestamp);
	}

	private static void serializeData(TokensParticle tokensParticle, ByteBuffer buf) {
		var rri = tokensParticle.getTokDefRef().toString().getBytes(RadixConstants.STANDARD_CHARSET);
		if (rri.length > 255) {
			throw new IllegalArgumentException("RRI cannot be greater than 255 chars");
		}
		var length = (byte) rri.length;
		buf.put(length); // rri length
		buf.put(rri); // rri
		buf.put((byte) tokensParticle.getAddress().toByteArray().length); // address length
		buf.put(tokensParticle.getAddress().toByteArray()); // address
		buf.put(tokensParticle.getAmount().toByteArray()); // amount
		buf.put((byte) (tokensParticle.isBurnable() ? 1 : 0)); // isBurnable
	}

	private static TokensParticle deserializeTokensParticle(ByteBuffer buf) {
		var length = Byte.toUnsignedInt(buf.get()); // length
		byte[] dst = new byte[length];
		buf.get(dst, 0, length);
		var rriString = new String(dst, RadixConstants.STANDARD_CHARSET);
		var rri = RRI.from(rriString);

		var addressLength = Byte.toUnsignedInt(buf.get()); // address length
		var addressDest = new byte[addressLength]; // address
		buf.get(addressDest);
		var address = RadixAddress.from(addressDest);

		var amountDest = new byte[UInt256.BYTES]; // amount
		buf.get(amountDest);
		var amount = UInt256.from(amountDest);

		var isBurnable = buf.get() != 0; // isBurnable

		return new TokensParticle(address, amount, rri, isBurnable);
	}

	private static void serializeData(StakedTokensParticle p, ByteBuffer buf) {
		buf.put((byte) p.getAddress().toByteArray().length); // address length
		buf.put(p.getAddress().toByteArray()); // address
		buf.put((byte) p.getDelegateAddress().toByteArray().length); // delegate length
		buf.put(p.getDelegateAddress().toByteArray()); // delegate
		buf.put(p.getAmount().toByteArray()); // amount
	}

	private static StakedTokensParticle deserializeStakedTokensParticle(ByteBuffer buf) {
		var addressLength = Byte.toUnsignedInt(buf.get()); // address length
		var addressDest = new byte[addressLength]; // address
		buf.get(addressDest);
		var address = RadixAddress.from(addressDest);

		var delegateLength = Byte.toUnsignedInt(buf.get()); // address length
		var delegateDest = new byte[delegateLength]; // address
		buf.get(delegateDest);
		var delegate = RadixAddress.from(delegateDest);

		var amountDest = new byte[UInt256.BYTES]; // amount
		buf.get(amountDest);
		var amount = UInt256.from(amountDest);

		return new StakedTokensParticle(delegate, address, amount);
	}

	private static void serializeData(ValidatorParticle p, ByteBuffer buf) {
		buf.put((byte) p.getAddress().toByteArray().length); // address length
		buf.put(p.getAddress().toByteArray()); // address

		buf.put((byte) (p.isRegisteredForNextEpoch() ? 1 : 0)); // isRegistered

		var name = p.getName().getBytes(RadixConstants.STANDARD_CHARSET);
		if (name.length > 255) {
			throw new IllegalArgumentException("RRI cannot be greater than 255 chars");
		}
		var length = (byte) name.length;
		buf.put(length); // name length
		buf.put(name); // name

		var url = p.getUrl().getBytes(RadixConstants.STANDARD_CHARSET);
		if (url.length > 255) {
			throw new IllegalArgumentException("RRI cannot be greater than 255 chars");
		}
		var urlLength = (byte) url.length;
		buf.put(urlLength); // url length
		buf.put(url); // url
	}

	private static ValidatorParticle deserializeValidatorParticle(ByteBuffer buf) {
		var addressLength = Byte.toUnsignedInt(buf.get()); // address length
		var addressDest = new byte[addressLength]; // address
		buf.get(addressDest);
		var address = RadixAddress.from(addressDest);

		var isRegistered = buf.get() != 0; // isRegistered

		var nameLength = Byte.toUnsignedInt(buf.get()); // name
		var nameDest = new byte[nameLength];
		buf.get(nameDest);
		var name = new String(nameDest, RadixConstants.STANDARD_CHARSET);

		var urlLength = Byte.toUnsignedInt(buf.get()); // url
		var urlDest = new byte[urlLength];
		buf.get(urlDest);
		var url = new String(urlDest, RadixConstants.STANDARD_CHARSET);
		return new ValidatorParticle(address, isRegistered, name, url);
	}

	private static void serializeData(UniqueParticle uniqueParticle, ByteBuffer buf) {
		serializeRri(buf, uniqueParticle.getRri());
	}

	private static UniqueParticle deserializeUniqueParticle(ByteBuffer buf) {
		var rri = deserializeRri(buf);
		return new UniqueParticle(rri);
	}

	private static void serializeData(TokenDefinitionParticle p, ByteBuffer buf) {
		serializeRri(buf, p.getRRI());
		p.getSupply().ifPresentOrElse(
			i -> {
				buf.put((byte) 0);
				buf.put(i.toByteArray());
			},
			() -> {
				buf.put((byte) 1);
			}
		);
		serializeString(buf, p.getName());
		serializeString(buf, p.getDescription());
		serializeString(buf, p.getUrl());
		serializeString(buf, p.getIconUrl());
	}

	private static TokenDefinitionParticle deserializeTokenDefinitionParticle(ByteBuffer buf) {
		var rri = deserializeRri(buf);
		var supply = buf.get() != 0 ? null : deserializeUInt256(buf);
		var name = deserializeString(buf);
		var description = deserializeString(buf);
		var url = deserializeString(buf);
		var iconUrl = deserializeString(buf);
		return new TokenDefinitionParticle(rri, name, description, iconUrl, url, supply);
	}

	private static UInt256 deserializeUInt256(ByteBuffer buf) {
		var amountDest = new byte[UInt256.BYTES]; // amount
		buf.get(amountDest);
		return UInt256.from(amountDest);
	}

	private static void serializeRri(ByteBuffer buf, RRI rri) {
		var rriBytes = rri.toString().getBytes(RadixConstants.STANDARD_CHARSET);
		if (rriBytes.length > 255) {
			throw new IllegalArgumentException("RRI cannot be greater than 255 chars");
		}
		var length = (byte) rriBytes.length;
		buf.put(length); // length
		buf.put(rriBytes); // rri
	}

	private static RRI deserializeRri(ByteBuffer buf) {
		var length = Byte.toUnsignedInt(buf.get()); // length
		byte[] dst = new byte[length];
		buf.get(dst, 0, length);
		var rriString = new String(dst, RadixConstants.STANDARD_CHARSET);
		return RRI.from(rriString);
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

	private static String deserializeString(ByteBuffer buf) {
		var len = Byte.toUnsignedInt(buf.get()); // url
		var dest = new byte[len];
		buf.get(dest);
		return new String(dest, RadixConstants.STANDARD_CHARSET);
	}
}
