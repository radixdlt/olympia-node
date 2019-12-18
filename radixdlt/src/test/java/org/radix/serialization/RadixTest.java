package org.radix.serialization;

import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockito.stubbing.Answer;
import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;
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
	private static RuntimeProperties properties;
	private static LocalSystem localSystem;

	@BeforeClass
	public static void startRadixTest() {
		TestSetupUtils.installBouncyCastleProvider();

		final SecureRandom secureRandom = new SecureRandom();

		properties = mock(RuntimeProperties.class);
		doAnswer(invocation -> invocation.getArgument(1)).when(properties).get(any(), any());

		final Universe universe = mock(Universe.class);
		when(universe.getMagic()).thenReturn(2);
		when(universe.getPort()).thenReturn(8080);

		final SystemMetaData systemMetaData = mock(SystemMetaData.class);
		SystemMetaData.set(systemMetaData);

		ntpService = mock(NtpService.class);
		when(ntpService.getUTCTimeMS()).thenAnswer((Answer<Long>) invocation -> System.currentTimeMillis());

		serialization = Serialization.getDefault();

		Modules.put(Universe.class, universe);
		localSystem = LocalSystem.restoreOrCreate(getProperties());
	}

	@AfterClass
	public static void finishRadixTest() {
		Modules.remove(Universe.class);
		SystemMetaData.clear();
	}

	public static Serialization getSerialization() {
		return serialization;
	}

	public static NtpService getNtpService() {
		return ntpService;
	}

	public static RuntimeProperties getProperties() {
		return properties;
	}

	public static LocalSystem getLocalSystem() {
		return localSystem;
	}
}
