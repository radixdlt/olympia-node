package org.radix.integration.shards;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;
import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;
import com.radixdlt.universe.Universe;
import org.radix.universe.system.LocalSystem;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Acceptance tests written as integration tests for RLAU-1100.
 * <p>
 * These tests cover the happy case in setup scenario 1 together with
 * the three unhappy cases in setup scenario 2.
 */
public class StaticShardingAcceptanceSetup {
	// Typed null value
	private static final LocalSystem NULL_LOCALSYSTEM = null;

	@Before
	public void beforeTest() {
		// Make sure LocalSystem.getInstance() actually creates instance
		Whitebox.setInternalState(LocalSystem.class, "instance", NULL_LOCALSYSTEM);
	}

	@After
	public void afterTest() {
		// Remove setup Universe, RuntimeProperties and LocalSystem instance
		Modules.remove(Universe.class);
		Modules.remove(RuntimeProperties.class);
		Whitebox.setInternalState(LocalSystem.class, "instance", NULL_LOCALSYSTEM);
	}

	// Setup Scenario 1: There is a valid shard range
	// Given ‌that I have defined a shard range that is smaller than 2^44 and that is bigger than 0,
	// When ‌I start the node,
	// Then ‌the Node will start successfully.
	@Test
	public void setup_scenario1() {
		// Shard range ("shards.range") is read from default.config and consumed in
		// LocalSystem#getInstance() to produce a ShardSpace.  We setup and check
		// that here.

		// Smaller than 2^44
		setupWithShardSpace((1L << 44) - 1L);
		assertNotNull(LocalSystem.getInstance());

		// Bigger than 0
		setupWithShardSpace(1L);
		assertNotNull(LocalSystem.getInstance());
	}

	// Setup Scenario 2, case 1: There is a invalid shard range
	// Given ‌that I have defined a shard range that is not a integer,
	// When ‌I start the node,
	// Then ‌the Node will give me an error.
	@Test(expected = ClassCastException.class)
	public void setup_scenario2_case1() {
		setupWithNonIntegerShardSpace("foo");
		LocalSystem.getInstance();
	}

	// Setup Scenario 2, case 2: There is a invalid shard range
	// Given ‌that I have defined a shard range that is bigger than 2^44,
	// When ‌I start the node,
	// Then ‌the Node will give me an error.
	@Test(expected = IllegalArgumentException.class)
	public void setup_scenario2_case2() {
		setupWithShardSpace((1L << 44) + 1L);
		LocalSystem.getInstance();
	}

	// Setup Scenario 2, case 3: There is a invalid shard range
	// Given ‌that I have defined a shard range that is smaller than 0,
	// When ‌I start the node,
	// Then ‌the Node will give me an error.
	@Test(expected = IllegalArgumentException.class)
	public void setup_scenario2_case3() {
		setupWithShardSpace(-1L);
		LocalSystem.getInstance();
	}

	private static void setupWithNonIntegerShardSpace(String value) {
		Universe universe = mock(Universe.class);
		when(universe.getPort()).thenReturn(30000);
		RuntimeProperties runtimeProperties = mock(RuntimeProperties.class);
		when(runtimeProperties.get("node.key.path", "node.key")).thenReturn("node.key");
		when(runtimeProperties.get(eq("shards.range"), any())).thenReturn(value);
		when(runtimeProperties.get("network.port", 30000)).thenReturn(30000);
		Modules.replace(Universe.class, universe);
		Modules.replace(RuntimeProperties.class, runtimeProperties);

		// Make sure LocalSystem.getInstance() actually creates instance
		Whitebox.setInternalState(LocalSystem.class, "instance", NULL_LOCALSYSTEM);
	}

	private static void setupWithShardSpace(long value) {
		Universe universe = mock(Universe.class);
		when(universe.getPort()).thenReturn(30000);
		RuntimeProperties runtimeProperties = mock(RuntimeProperties.class);
		when(runtimeProperties.get("node.key.path", "node.key")).thenReturn("node.key");
		when(runtimeProperties.get(eq("shards.range"), any())).thenReturn(value);
		when(runtimeProperties.get("network.port", 30000)).thenReturn(30000);
		Modules.replace(Universe.class, universe);
		Modules.replace(RuntimeProperties.class, runtimeProperties);

		// Make sure LocalSystem.getInstance() actually creates instance
		Whitebox.setInternalState(LocalSystem.class, "instance", NULL_LOCALSYSTEM);
	}
}
