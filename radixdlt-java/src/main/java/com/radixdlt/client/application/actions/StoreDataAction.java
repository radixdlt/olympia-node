package com.radixdlt.client.application.actions;

import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An Application Layer Action object which stores data into an address or multiple addresses.
 */
public class StoreDataAction {
	private final RadixAddress source;
	private final Data data;
	private final List<RadixAddress> addresses;

	public StoreDataAction(RadixAddress source, Data data, RadixAddress address) {
		this.source = source;
		this.data = data;
		this.addresses = Collections.singletonList(address);
	}

	public StoreDataAction(RadixAddress source, Data data, RadixAddress address0, RadixAddress address1) {
		this.source = source;
		this.data = data;
		this.addresses = Arrays.asList(address0, address1);
	}

	public Data getData() {
		return data;
	}

	public List<RadixAddress> getAddresses() {
		return Collections.unmodifiableList(addresses);
	}

	public RadixAddress getSource() {
		return source;
	}
}
