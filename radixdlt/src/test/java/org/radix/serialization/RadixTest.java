package org.radix.serialization;

import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockito.stubbing.Answer;
import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;
import org.radix.shards.ShardSpace;
import org.radix.time.NtpService;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.SystemMetaData;

import java.security.SecureRandom;

import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

public abstract class RadixTest
{
	private static Serialization serialization;
	private static NtpService ntpService;
	private static RuntimeProperties runtimeProperties;

	@BeforeClass
	public static void startRadixTest() {
		TestSetupUtils.installBouncyCastleProvider();

		final SecureRandom secureRandom = new SecureRandom();

		runtimeProperties = mock(RuntimeProperties.class);
		doAnswer(invocation -> invocation.getArgument(1)).when(runtimeProperties).get(any(), any());

		final Universe universe = mock(Universe.class);
		when(universe.getMagic()).thenReturn(2);

		final SystemMetaData systemMetaData = mock(SystemMetaData.class);
		SystemMetaData.set(systemMetaData);

		ntpService = mock(NtpService.class);
		when(ntpService.getUTCTimeMS()).thenAnswer((Answer<Long>) invocation -> System.currentTimeMillis());

		serialization = Serialization.getDefault();

		final LocalSystem localSystem = mock(LocalSystem.class);
		when(localSystem.getShards()).thenReturn(new ShardSpace(10000, 20000));

		Modules.put(RuntimeProperties.class, runtimeProperties);
		Modules.put(Universe.class, universe);
		Modules.put(LocalSystem.class, localSystem);
	}

	@AfterClass
	public static void finishRadixTest() {
		Modules.remove(RuntimeProperties.class);
		Modules.remove(Serialization.class);
		Modules.remove(SecureRandom.class);
		Modules.remove(Universe.class);
		Modules.remove(LocalSystem.class);
		SystemMetaData.clear();
	}

	public static Serialization getSerialization() {
		return serialization;
	}

	public static NtpService getNtpService() {
		return ntpService;
	}

	public static RuntimeProperties getProperties() {
		return runtimeProperties;
	}
}
