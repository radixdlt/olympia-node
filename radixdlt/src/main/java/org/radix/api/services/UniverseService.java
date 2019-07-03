package org.radix.api.services;

import org.json.JSONException;
import org.json.JSONObject;
import org.radix.modules.Modules;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;

public final class UniverseService {
	private final static UniverseService universeService = new UniverseService();

	public static UniverseService getInstance() {
		return universeService;
	}

	private UniverseService() {}

	public JSONObject getUniverse() throws JSONException {
		Universe universe = Modules.get(Universe.class);
		return Modules.get(Serialization.class).toJsonObject(universe, Output.API);
	}
}
