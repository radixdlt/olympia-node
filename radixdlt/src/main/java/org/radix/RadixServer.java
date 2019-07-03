package org.radix;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.radix.modules.Modules;
import org.radix.modules.Service;
import org.radix.modules.exceptions.ModuleException;
import org.radix.properties.RuntimeProperties;
import org.radix.utils.FileUtils;

public class RadixServer extends Service {

	public RadixServer() throws IOException {
		if (Modules.get(RuntimeProperties.class).get("cp", 1) == 1) {
			if (Modules.get(RuntimeProperties.class).get("cp.refresh", 1) == 1) {
				String CPPath = Modules.get(RuntimeProperties.class).get("cp.path", System.getProperty("radix.jar.path") + "/CP");
				File dir = new File(CPPath);

				String dirAbsolutePath = dir.toURI().toURL().toString();
				URL panelAbsolutePath = Thread.currentThread().getClass()
						.getResource(Modules.get(RuntimeProperties.class).get("cp.resources", "/CP"));

				if (!dirAbsolutePath.contains(panelAbsolutePath.toString())) {
					if (!dir.exists()) {
						dir.mkdirs();
					}
					FileUtils.copyResourcesRecursively(panelAbsolutePath, dir);
				}
			}
		}
	}

	@Override
	public void start_impl() throws ModuleException {
		// Do nothing
	}

	@Override
	public void stop_impl() throws ModuleException {
		// Do nothing
	}

	@Override
	public String getName() {
		return "Radix Server";
	}

}
