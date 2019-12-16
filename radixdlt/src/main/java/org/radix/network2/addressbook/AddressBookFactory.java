package org.radix.network2.addressbook;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.grapher.graphviz.GraphvizGrapher;
import com.google.inject.grapher.graphviz.GraphvizModule;
import org.radix.database.DatabaseEnvironment;
import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;

/**
 * Factory for creating an {@link AddressBook}.
 */
public class AddressBookFactory {

	/**
	 * Create a {@link AddressBook} based on a default configuration.
	 *
	 * @return The newly constructed {@link AddressBook}
	 * @param dbEnv
	 */
	public AddressBook createDefault(DatabaseEnvironment dbEnv) {
		return createInjector(dbEnv).getInstance(AddressBook.class);
	}

	private Injector createInjector(DatabaseEnvironment dbEnv) {
		Injector injector = Guice.createInjector(new AddressBookModule(dbEnv));
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
}
