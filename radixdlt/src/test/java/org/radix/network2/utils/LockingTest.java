package org.radix.network2.utils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for locking class.
 */
public class LockingTest {
	/**
	 * Lock counter. Doesn't actually do any locking, but increments a counter when
	 * one of the locking methods is called, and decrements when unlocked.
	 */
	private static class TestLock implements Lock {
		private int lockCount = 0;

		@Override
		public void lock() {
			lockCount += 1;
		}

		@Override
		public void lockInterruptibly() throws InterruptedException {
			lockCount += 1;
		}

		@Override
		public boolean tryLock() {
			lockCount += 1;
			return true;
		}

		@Override
		public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
			lockCount += 1;
			return true;
		}

		@Override
		public void unlock() {
			lockCount -= 1;
		}

		@Override
		public Condition newCondition() {
			return null;
		}

		/**
		 * Retrieve the current lock count. Lock count is increased when one of the
		 * locking methods is called, and decremented when unlocking.
		 *
		 * @return The current lock count.
		 */
		int getLockCount() {
			return lockCount;
		}

	}

	@Test
	public void withLock() {
		final TestLock lock = new TestLock();
		Locking.withLock(lock, () -> {
			assertEquals(lock.getLockCount(), 1);
			Locking.withLock(lock, () -> {
				assertEquals(lock.getLockCount(), 2);
			});
			assertEquals(lock.getLockCount(), 1);
		});
		assertEquals(lock.getLockCount(), 0);
	}

	@Test
	public void withConsumerLock() {
		final TestLock lock = new TestLock();
		final Object testObj = new Object();
		Locking.withConsumerLock(lock, o -> {
			assertNull(o);
			assertEquals(lock.getLockCount(), 1);
			Locking.withConsumerLock(lock, o2 -> {
				assertSame(o2, testObj);
				assertEquals(lock.getLockCount(), 2);
			}, testObj);
			assertEquals(lock.getLockCount(), 1);
		}, null);
		assertEquals(lock.getLockCount(), 0);
	}

	@Test
	public void withBiConsumerLock() {
		final TestLock lock = new TestLock();
		final Object testObj1 = new Object();
		final Object testObj2 = new Object();
		Locking.withConsumerLock(lock, (a1, a2) -> {
			assertNull(a1);
			assertNull(a2);
			assertEquals(lock.getLockCount(), 1);
			Locking.withConsumerLock(lock, (b1, b2) -> {
				assertSame(b1, testObj1);
				assertSame(b2, testObj2);
				assertEquals(lock.getLockCount(), 2);
			}, testObj1, testObj2);
			assertEquals(lock.getLockCount(), 1);
		}, null, null);
		assertEquals(lock.getLockCount(), 0);
	}

	@Test
	public void withSupplierLock() {
		final TestLock lock = new TestLock();
		final Object testObj = new Object();
		final Object result1 = Locking.withSupplierLock(lock, () -> {
			assertEquals(lock.getLockCount(), 1);
			final Object result2 = Locking.withSupplierLock(lock, () -> {
				assertEquals(lock.getLockCount(), 2);
				return testObj;
			});
			assertEquals(lock.getLockCount(), 1);
			assertSame(result2, testObj);
			return result2;
		});
		assertEquals(lock.getLockCount(), 0);
		assertSame(testObj, result1);
	}

	@Test
	public void withPredicateLock() {
		final TestLock lock = new TestLock();
		final Object testObj = new Object();
		final boolean result1 = Locking.withPredicateLock(lock, o -> {
			assertNull(o);
			assertEquals(lock.getLockCount(), 1);
			final boolean result2 = Locking.withPredicateLock(lock, o2 -> {
				assertSame(o2, testObj);
				assertEquals(lock.getLockCount(), 2);
				return false;
			}, testObj);
			assertEquals(lock.getLockCount(), 1);
			assertFalse(result2);
			return true;
		}, null);
		assertEquals(lock.getLockCount(), 0);
		assertTrue(result1);
	}

	@Test
	public void withFunctionLock() {
		final TestLock lock = new TestLock();
		final Object testObj = new Object();
		final Object result1 = Locking.withFunctionLock(lock, o -> {
			assertNull(o);
			assertEquals(lock.getLockCount(), 1);
			final Object result2 = Locking.withFunctionLock(lock, o2 -> {
				assertSame(o2, testObj);
				assertEquals(lock.getLockCount(), 2);
				return o2;
			}, testObj);
			assertEquals(lock.getLockCount(), 1);
			assertSame(result2, testObj);
			return o;
		}, null);
		assertEquals(lock.getLockCount(), 0);
		assertNull(result1);
	}
}
