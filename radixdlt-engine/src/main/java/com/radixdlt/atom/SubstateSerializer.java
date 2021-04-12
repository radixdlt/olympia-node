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
import com.radixdlt.DefaultSerialization;
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
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.nio.ByteBuffer;
import java.util.Map;

public final class SubstateSerializer {
	private static final Serialization serialization = DefaultSerialization.getInstance();
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
		} else {
			return serialization.fromDson(bytes, 2, bytes.length - 2, Particle.class);
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
		} else {
			buf.put(serialization.toDson(p, DsonOutput.Output.ALL));
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
}
