package org.radix.discovery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.radix.collections.WireableList;

import com.radixdlt.atoms.Atom;
import com.radixdlt.common.EUID;
import com.radixdlt.common.AID;
import org.radix.common.ID.ID;
import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import org.radix.time.ChronologicObject;
import org.radix.time.NtpService;
import org.radix.time.Timestamps;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public abstract class DiscoveryRequest extends ChronologicObject implements ID
{
	@Override
	public short VERSION() { return 100; }

	public enum Action
	{
		DISCOVER, DELIVER, DISCOVER_AND_DELIVER;

		@Override
		@JsonValue
		public String toString() {
			return this.name();
		}
	}

	@JsonProperty("session")
	@DsonOutput(Output.ALL)
	private EUID				session = new EUID(System.nanoTime());

	@JsonProperty("action")
	@DsonOutput(Output.ALL)
	private Action				action = Action.DISCOVER;

	@JsonProperty("cursor")
	@DsonOutput(value = Output.HASH, include = false)
	private DiscoveryCursor		cursor = new DiscoveryCursor(0L);

	@JsonProperty("inventory")
	@DsonOutput(value = Output.HASH, include = false)
	private WireableList<AID>	inventory = new WireableList<>();

	@JsonProperty("delivered")
	@DsonOutput(value = Output.HASH, include = false)
	private WireableList<Atom> delivered = new WireableList<>();

	@JsonProperty("limit")
	@DsonOutput(Output.ALL)
	private short				limit = Modules.get(RuntimeProperties.class).get("discovery.response.max", (short) 1000);

	private EUID				uid = EUID.ZERO;

	public DiscoveryRequest()
	{
		super();

		setTimestamp(Timestamps.FROM, 0l);
		setTimestamp(Timestamps.TO, Long.MAX_VALUE);
	}

	public DiscoveryRequest(Action action)
	{
		this();

		this.action = action;
	}

	public boolean next()
	{
		boolean next = false;

		if (getCursor() != null && getCursor().hasNext())
		{
			setCursor(getCursor().getNext());
			next = true;
		}

		inventory.clear();
		delivered.clear();
		session = new EUID(System.nanoTime());
		setTimestamp(Timestamps.DEFAULT, Modules.get(NtpService.class).getUTCTimeMS());

		return next;
	}

	@Override
	public EUID getUID()
	{
		return uid;
	}

	@Override
	public void setUID(EUID uid)
	{
		this.uid = uid;
	}

	public Action getAction() { return action; }

	public EUID getSession()
	{
		return session;
	}

	public DiscoveryRequest setTimestamp(int from, int to)
	{
		setTimestamp(Timestamps.FROM, from*1000l);
		setTimestamp(Timestamps.TO, to*1000l);

		return this;
	}

	public DiscoveryCursor getCursor() { return cursor; }

	public DiscoveryRequest setCursor(DiscoveryCursor cursor)
	{
		this.cursor = cursor;

		return this;
	}

	public short getLimit() { return limit; }

	public DiscoveryRequest setLimit(short limit)
	{
		this.limit = limit;

		if (this.limit < 1)
			this.limit = 1;

		if (this.limit > Modules.get(RuntimeProperties.class).get("discovery.response.max", (short) 1000))
			this.limit = Modules.get(RuntimeProperties.class).get("discovery.response.max", (short) 1000);

		return this;
	}

	public List<AID> getInventory()
	{
		return new ArrayList<>(inventory);
	}

	public DiscoveryRequest setInventory(AID id)
	{
		this.inventory.clear();
		this.inventory.add(id);
		return this;
	}

	public DiscoveryRequest setInventory(Collection<AID> inventory)
	{
		this.inventory.clear();

		if (inventory != null)
			this.inventory.addAll(inventory);

		return this;
	}

	public List<Atom> getDelivered()
	{
		return new ArrayList<>(delivered);
	}

	public DiscoveryRequest setDelivered(Collection<Atom> delivered)
	{
		this.delivered.clear();

		if (delivered != null)
			this.delivered.addAll(delivered);

		return this;
	}

	@Override
	public String toString()
	{
		StringBuilder string = new StringBuilder();
		string.append(getSession()+":"+getAction());

		if (this.inventory.isEmpty() == false || this.delivered.isEmpty() == false)
			string.append(" ->");

		if (this.inventory.isEmpty() == false)
			string.append(" Inventory: "+this.inventory.size());

		if (this.delivered.isEmpty() == false)
			string.append(" Delivered: "+this.delivered.size());

		return string.toString();
	}

	// SERIALIZER //

	@JsonProperty("uid")
	@DsonOutput(Output.ALL)
	private EUID getJsonUid() {
		return (uid == null || EUID.ZERO.equals(uid)) ? null : uid;
	}

	@JsonProperty("uid")
	private void setJsonUid(EUID uid) {
		this.uid = uid;
	}
}