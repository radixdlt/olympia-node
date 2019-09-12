package org.radix.network2.transport;

import java.io.IOException;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class SendResultTest {

	@Test
	public void testComplete() {
		SendResult complete = SendResult.complete();

		assertThat(complete.toString(), containsString("Complete"));
		assertTrue(complete.isComplete());
	}

	@Test
	public void testFailure() {
		SendResult complete = SendResult.failure(new IOException());

		assertThat(complete.toString(), containsString(IOException.class.getName()));
		assertFalse(complete.isComplete());
	}

	@Test
	public void testGetException() {
		SendResult complete = SendResult.failure(new IOException());

		assertThat(complete.getThrowable(), notNullValue());
		assertThat(complete.getThrowable().getClass().getName(), equalTo(IOException.class.getName()));
	}
}
