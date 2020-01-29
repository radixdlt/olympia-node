/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix.network2.messaging;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;

import org.apache.commons.cli.ParseException;
import org.json.JSONObject;
import org.radix.Radix;
import org.radix.network2.transport.udp.UDPTransportModule;
import org.radix.properties.RuntimeProperties;
import org.radix.utils.IOUtils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.grapher.graphviz.GraphvizGrapher;
import com.google.inject.grapher.graphviz.GraphvizModule;

/**
 * Factory for creating a {@link MessageCentral}.
 */
public class MessageCentralFactory {

	/**
	 * Create a {@link MessageCentral} based on a default configuration.
	 * Note that the default configuration is unspecified right now, but
	 * at least will include sufficient transports that nodes will be able
	 * to talk to each other if they use it.
	 *
	 * @param properties Static configuration properties to use when creating
	 * @return The newly constructed {@link MessageCentral}
	 */
	public MessageCentral createDefault(RuntimeProperties properties) {
		return createInjector(properties).getInstance(MessageCentral.class);
	}

	private Injector createInjector(RuntimeProperties properties) {
		Injector injector = Guice.createInjector(
				new MessageCentralModule(properties),
				new UDPTransportModule(properties)
		);
		return injector;
	}

	private static void outputGraph(Injector injector, String filename) {
		try (PrintWriter out = new PrintWriter(new File(filename), "UTF-8")) {
			Injector graphvizInjector = Guice.createInjector(new GraphvizModule());
			GraphvizGrapher grapher = graphvizInjector.getInstance(GraphvizGrapher.class);
			grapher.setOut(out);
			grapher.setRankdir("TB");
			grapher.graph(injector);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static void main(String[] args) throws IOException, ParseException {
		JSONObject runtimeConfigurationJSON = new JSONObject();
		if (Radix.class.getResourceAsStream("/runtime_options.json") != null)
			runtimeConfigurationJSON = new JSONObject(IOUtils.toString(Radix.class.getResourceAsStream("/runtime_options.json")));

		RuntimeProperties properties = new RuntimeProperties(runtimeConfigurationJSON, args);
		Injector injector = new MessageCentralFactory().createInjector(properties);
		outputGraph(injector, "MessageCentral.dot");
	}
}
