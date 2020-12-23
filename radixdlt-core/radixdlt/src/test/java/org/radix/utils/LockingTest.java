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

package org.radix.utils;

import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

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
    public void withLockException() {
        final TestLock lock = new TestLock();
        Locking.withLock(lock, () -> {
            assertEquals(lock.getLockCount(), 1);
            try {
                Locking.withLock(lock, () -> {
                    assertEquals(lock.getLockCount(), 2);
                    throw new RuntimeException("Expected exception");
                });
            } catch (Exception e) {
                assertEquals("Expected exception", e.getMessage());
            }
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
    public void withConsumerLockException() {
        final TestLock lock = new TestLock();
        final Object testObj = new Object();
        Locking.withConsumerLock(lock, o -> {
            assertNull(o);
            assertEquals(lock.getLockCount(), 1);
            try {
                Locking.withConsumerLock(lock, o2 -> {
                    assertSame(o2, testObj);
                    assertEquals(lock.getLockCount(), 2);
                    throw new RuntimeException("Expected exception");
                }, testObj);
            } catch (Exception e) {
                assertEquals("Expected exception", e.getMessage());
            }
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
    public void withBiConsumerLockException() {
        final TestLock lock = new TestLock();
        final Object testObj1 = new Object();
        final Object testObj2 = new Object();
        Locking.withConsumerLock(lock, (a1, a2) -> {
            assertNull(a1);
            assertNull(a2);
            assertEquals(lock.getLockCount(), 1);
            try {
                Locking.withConsumerLock(lock, (b1, b2) -> {
                    assertSame(b1, testObj1);
                    assertSame(b2, testObj2);
                    assertEquals(lock.getLockCount(), 2);
                    throw new RuntimeException("Expected exception");
                }, testObj1, testObj2);
            } catch (Exception e) {
                assertEquals("Expected exception", e.getMessage());
            }
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
    public void withSupplierLockException() {
        final TestLock lock = new TestLock();
        final Object testObj = new Object();
        final Object result1 = Locking.withSupplierLock(lock, () -> {
            assertEquals(lock.getLockCount(), 1);
            try {
                Locking.withSupplierLock(lock, () -> {
                    assertEquals(lock.getLockCount(), 2);
                    throw new RuntimeException("Expected exception");
                });
            } catch (Exception e) {
                assertEquals("Expected exception", e.getMessage());
            }
            assertEquals(lock.getLockCount(), 1);
            return testObj;
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
    public void withPredicateLockException() {
        final TestLock lock = new TestLock();
        final Object testObj = new Object();
        final boolean result1 = Locking.withPredicateLock(lock, o -> {
            assertNull(o);
            assertEquals(lock.getLockCount(), 1);
            try {
                Locking.withPredicateLock(lock, o2 -> {
                    assertSame(o2, testObj);
                    assertEquals(lock.getLockCount(), 2);
                    throw new RuntimeException("Expected exception");
                }, testObj);
            } catch (Exception e) {
                assertEquals("Expected exception", e.getMessage());
            }
            assertEquals(lock.getLockCount(), 1);
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

    @Test
    public void withFunctionLockException() {
        final TestLock lock = new TestLock();
        final Object testObj = new Object();
        final Object result1 = Locking.withFunctionLock(lock, o -> {
            assertNull(o);
            assertEquals(lock.getLockCount(), 1);
            try {
                Locking.withFunctionLock(lock, o2 -> {
                    assertSame(o2, testObj);
                    assertEquals(lock.getLockCount(), 2);
                    throw new RuntimeException("Expected exception");
                }, testObj);
            } catch (Exception e) {
                assertEquals("Expected exception", e.getMessage());
            }
            assertEquals(lock.getLockCount(), 1);
            return o;
        }, null);
        assertEquals(lock.getLockCount(), 0);
        assertNull(result1);
    }

    @Test
    public void withBiFunctionLock() {
        final TestLock lock = new TestLock();
        final Object testObj1 = new Object();
        final Object testObj2 = new Object();
        Locking.withBiFunctionLock(lock, (o, o2) -> {
            assertEquals(lock.getLockCount(), 1);
            Locking.withBiFunctionLock(lock, (o1, o21) -> {
                assertEquals(lock.getLockCount(), 2);
                return null;
            }, testObj1, testObj2);
            assertEquals(lock.getLockCount(), 1);
            return null;
        }, testObj1, testObj2);
        assertEquals(lock.getLockCount(), 0);
    }

    @Test
    public void withBiFunctionLockException() {
        final TestLock lock = new TestLock();
        final Object testObj1 = new Object();
        final Object testObj2 = new Object();
        Locking.withBiFunctionLock(lock, (o, o2) -> {
            assertEquals(lock.getLockCount(), 1);
            try {
                Locking.withBiFunctionLock(lock, (o1, o21) -> {
                    assertEquals(lock.getLockCount(), 2);
                    throw new RuntimeException("Expected exception");
                }, testObj1, testObj2);
            } catch (Exception e) {
                assertEquals("Expected exception", e.getMessage());
            }
            assertEquals(lock.getLockCount(), 1);
            return null;
        }, testObj1, testObj2);
        assertEquals(lock.getLockCount(), 0);
    }
}
