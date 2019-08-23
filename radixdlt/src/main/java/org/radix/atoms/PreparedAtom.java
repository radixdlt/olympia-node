package org.radix.atoms;

import com.radixdlt.engine.CMAtom;
import com.radixdlt.engine.SimpleCMAtom;
import com.radixdlt.utils.UInt384;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import java.util.stream.Collectors;

import com.radixdlt.common.AID;
import org.radix.atoms.AtomStore.IDType;
import com.radixdlt.constraintmachine.CMParticle;
import com.radixdlt.common.EUID;
import org.radix.modules.Modules;
import com.radixdlt.utils.WireIO.Reader;
import com.radixdlt.utils.WireIO.Writer;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationUtils;
import org.radix.time.TemporalVertex;
import org.radix.universe.system.LocalSystem;

/**
 * The Atom which Tempo understands (non-translucent).
 * TODO: Separate out serialization/deserialization.
 */
public class PreparedAtom {
	private long clock;
	private AID atomID;
	private String classID;
	private Set<Long> shards = null;
	private long timestamp;
	private byte[] atomBytes;
	private UInt384 mass;

	private Map<Long, byte[]> uniqueIndexables = null;
	private Map<Long, byte[]> duplicateIndexables = null;

	private Atom atom;

	public PreparedAtom(CMAtom cmAtom, UInt384 mass) throws IOException {
		this.atom = (Atom) ((SimpleCMAtom) cmAtom).getAtom();
		this.mass = mass;
		this.atomID = atom.getAID();

		TemporalVertex vertex = atom.getTemporalProof().getVertexByNID(LocalSystem.getInstance().getNID());
		if (vertex == null)
			throw new IOException("Temporal vertex not found for node "+LocalSystem.getInstance().getNID()+" in TemporalProof for Atom "+atom);

		this.clock = vertex.getClock();
		this.classID = Modules.get(Serialization.class).getIdForClass(atom.getClass());

		this.timestamp = atom.getTimestamp();
		this.atomBytes = Modules.get(Serialization.class).toDson(atom, DsonOutput.Output.PERSIST);

		this.uniqueIndexables = new HashMap<>();
		for (CMParticle cmParticle : cmAtom.getCMInstruction().getParticles()) {
			EUID particleId = cmParticle.getParticle().getHID();
			cmParticle.nextSpins().forEach(s -> {
				final IDType idType;
				switch (s) {
					case UP:
						idType = IDType.PARTICLE_UP;
						break;
					case DOWN:
						idType = IDType.PARTICLE_DOWN;
						break;
					default:
						throw new IllegalStateException("Unknown SPIN state for particle " + s);
				}

				final byte[] indexableBytes = IDType.toByteArray(idType, particleId);
				this.uniqueIndexables.put(particleId.getLow() + s.intValue(), indexableBytes);
			});
		}
		this.uniqueIndexables.put(atom.getAID().getLow(), IDType.toByteArray(IDType.ATOM, atom.getAID()));

		this.shards = new HashSet<>();
		this.duplicateIndexables = new HashMap<>();

		for (EUID euid : cmAtom.getCMInstruction().getDestinations()) {
			this.duplicateIndexables.put(euid.getLow(), IDType.toByteArray(IDType.DESTINATION, euid));
			this.duplicateIndexables.put(euid.getShard(), IDType.toByteArray(IDType.SHARD, euid.getShard()));
			this.shards.add(euid.getShard());
		}

		cmAtom.getCMInstruction().getParticles().forEach(cmParticle -> {
			// TODO: Remove
			// This does not handle nested particle classes.
			// If that ever becomes a problem, this is the place to fix it.
			final Serialization serialization = Modules.get(Serialization.class);
			final String idForClass = serialization.getIdForClass(cmParticle.getParticle().getClass());
			final EUID numericClassId = SerializationUtils.stringToNumericID(idForClass);
			this.duplicateIndexables.put(numericClassId.getLow(), IDType.toByteArray(IDType.PARTICLE_CLASS, numericClassId));
		});
	}

	PreparedAtom(byte[] bytes) throws IOException
	{
		fromByteArray(bytes);
	}

	public UInt384 getMass() {
		return mass;
	}

	public long getClock()
	{
		return this.clock;
	}

	public AID getAtomID()
	{
		return this.atomID;
	}

	public String getClassID()
	{
		return this.classID;
	}

	public Set<Long> getShards()
	{
		return this.shards;
	}

	Collection<byte[]> getUniqueIndexables()
	{
		return this.uniqueIndexables.values();
	}

	Collection<byte[]> getDuplicateIndexables()
	{
		return this.duplicateIndexables.values();
	}

	public Set<EUID> getDestinations() {
		return this.duplicateIndexables.entrySet().stream()
			.filter(e -> e.getValue()[0] == IDType.DESTINATION.ordinal())
			.map(e -> IDType.toEUID(e.getValue()))
			.collect(Collectors.toSet());
	}

	public long getTimestamp()
	{
		return this.timestamp;
	}

	public Atom getAtom() {
		if (this.atom == null) {
			try {
				// TODO: Once pipeline is done correct don't need this deserialization anymore.
				this.atom = Modules.get(Serialization.class).fromDson(this.atomBytes, Atom.class);
			} catch (IOException e) {
				throw new IllegalStateException("Could not deserialize atom", e);
			}
		}
		return this.atom;
	}

	// SERLIALIZATION //
	public byte[] toByteArray() throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(2048);
		Writer writer = new Writer(baos);

		// Warning: Clock needs to be written first.
		writer.writeLong(this.clock);
		writer.writeAID(this.atomID);
		writer.writeString(this.classID);
		writer.writeBytes(mass.toByteArray());

		if (!this.shards.isEmpty()) {
			writer.writeShort(this.shards.size());
			for (long shard : this.shards)
				writer.writeLong(shard);
		} else {
			writer.writeShort(0);
		}


		if (this.uniqueIndexables != null && !this.uniqueIndexables.isEmpty()) {
			writer.writeShort(this.uniqueIndexables.size());

			for (Map.Entry<Long, byte[]> entry : this.uniqueIndexables.entrySet()) {
				writer.writeLong(entry.getKey().longValue());
				writer.writeVarBytes(entry.getValue());
			}
		} else {
			writer.writeShort(0);
		}

		if (this.duplicateIndexables != null && !this.duplicateIndexables.isEmpty()) {
			writer.writeShort(this.duplicateIndexables.size());

			for (Map.Entry<Long, byte[]> entry : this.duplicateIndexables.entrySet()) {
				writer.writeLong(entry.getKey().longValue());
				writer.writeVarBytes(entry.getValue());
			}
		} else {
			writer.writeShort(0);
		}

		writer.writeLong(timestamp);

		if (this.atomBytes == null) {
			this.atomBytes = Modules.get(Serialization.class).toDson(this.atom, DsonOutput.Output.PERSIST);
		}
		writer.writeVarBytes(this.atomBytes);

		return baos.toByteArray();
	}

	public void fromByteArray(byte[] bytes) throws IOException {
		Reader reader = new Reader(bytes);

		this.clock = reader.readLong();
		this.atomID = reader.readAID();
		this.classID = reader.readString();
		this.mass = UInt384.from(reader.readBytes(UInt384.BYTES));

		int numShards = reader.readShort();
		if (numShards > 0) {
			this.shards = new HashSet<>(numShards);

			for (int s = 0 ; s < numShards ; s++) {
				this.shards.add(reader.readLong());
			}
		}

		int numUniqueIndexables = reader.readShort();
		this.uniqueIndexables = new HashMap<>(numUniqueIndexables);
		if (numUniqueIndexables > 0) {
			for (int u = 0 ; u < numUniqueIndexables ; u++) {
				this.uniqueIndexables.put(reader.readLong(), reader.readVarBytes());
			}
		}

		int numDuplicateIndexables = reader.readShort();
		this.duplicateIndexables = new HashMap<>(numDuplicateIndexables);
		if (numDuplicateIndexables > 0) {
			for (int d = 0 ; d < numDuplicateIndexables ; d++) {
				this.duplicateIndexables.put(reader.readLong(), reader.readVarBytes());
			}
		}

		this.timestamp = reader.readLong();

		this.atomBytes = reader.readVarBytes();
	}

	public boolean contains(IDType type, EUID id)
	{
		byte[] query = IDType.toByteArray(type, id);

		for (byte[] indexable : this.uniqueIndexables.values())
		{
			if (Arrays.equals(indexable, query) == true)
				return true;
		}

		for (byte[] indexable : this.duplicateIndexables.values())
		{
			if (Arrays.equals(indexable, query) == true)
				return true;
		}

		return false;
	}
}
