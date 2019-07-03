package org.radix.api.services;

import org.json.JSONArray;
import org.json.JSONObject;
import org.radix.modules.Modules;
import org.radix.modules.Plugin;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.SystemProfiler;

/**
 * Service methods which should only be accessible by owner of node
 */
public final class AdminService {
	private static final AdminService adminService = new AdminService();

	public static AdminService getInstance() {
		return adminService;
	}

	private AdminService() {}

	public JSONObject getSystem() {
        return Modules.get(Serialization.class).toJsonObject(LocalSystem.getInstance(), Output.API);
	}

	public JSONArray getModules() {
		JSONArray array = new JSONArray();
		Modules.getAll().stream()
			.filter(module -> module instanceof Plugin)
			.map(module -> {
				JSONObject object = new JSONObject();
				object.put("name", module.getName());
				object.put("uid", module.getUID().toString());
				return object;
			})
			.forEach(array::put);

		return array;
	}

	public JSONArray getProfiler() {
		JSONArray array = new JSONArray();
		Modules.get(SystemProfiler.class).getAll()
			.stream()
			.sorted()
			.map(record -> {
				JSONObject object = new JSONObject();
				object.put("name", record.getName());
				object.put("duration", record.getDuration().get() / 1000000000.0);
				object.put("average", record.getAverage() / 1000000000.0);
				object.put("iterations", record.getIterations().get());
				return object;
			})
			.forEach(array::put);
		return array;
	}
}
