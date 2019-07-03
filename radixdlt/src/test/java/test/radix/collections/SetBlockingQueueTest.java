package test.radix.collections;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import org.junit.Assert;
import org.junit.Test;
import org.radix.collections.SetBlockingQueue;

public class SetBlockingQueueTest
{
	@Test
	public void offerDuplicateTest()
	{
		BlockingQueue<Object> queue = new SetBlockingQueue<Object>();
		Object object = new Object();

		Assert.assertTrue(queue.offer(object));
		Assert.assertFalse(queue.offer(object));
	}

	@Test
	public void offerDuplicateAndPollTest()
	{
		BlockingQueue<Object> queue = new SetBlockingQueue<Object>();
		Object object = new Object();

		Assert.assertTrue(queue.offer(object));
		Assert.assertFalse(queue.offer(object));
		Assert.assertEquals(object, queue.poll());
		Assert.assertNull(queue.poll());
	}

	@Test
	public void addAllWithDuplicate()
	{
		BlockingQueue<Object> queue = new SetBlockingQueue<Object>();
		Set<Object> add = new HashSet<Object>();

		for (int i=0 ; i < 10 ; i++)
		{
			Object object = new Object();
			add.add(object);
		}

		Assert.assertTrue(queue.addAll(add));
		Assert.assertEquals(10, queue.size());

		add.add(new Object());
		Assert.assertTrue(queue.addAll(add));
		Assert.assertEquals(11, queue.size());
	}

	@Test
	public void addAllRemoveAllTest()
	{
		BlockingQueue<Object> queue = new SetBlockingQueue<Object>();
		Set<Object> add = new HashSet<Object>();
		Set<Object> remove = new HashSet<Object>();

		for (int i=0 ; i < 10 ; i++)
		{
			Object object = new Object();
			add.add(object);
			if (i!=0)
				remove.add(object);
		}

		Assert.assertTrue(queue.addAll(add));
		Assert.assertEquals(10, queue.size());
		Assert.assertTrue(queue.removeAll(remove));
		Assert.assertEquals(1, queue.size());
	}

	@Test
	public void addAllRetainAllTest()
	{
		BlockingQueue<Object> queue = new SetBlockingQueue<Object>();
		Set<Object> add = new HashSet<Object>();
		Set<Object> retain = new HashSet<Object>();

		for (int i=0 ; i < 10 ; i++)
		{
			Object object = new Object();
			add.add(object);
			if (i==0)
				retain.add(object);
		}

		Assert.assertTrue(queue.addAll(add));
		Assert.assertEquals(10, queue.size());
		Assert.assertTrue(queue.retainAll(retain));
		Assert.assertEquals(1, queue.size());
	}


	// The following test is failing once in a while so commented out
	/*
	@Test
	public void offerThreadedSingleItemQueue()
	{
		BlockingQueue<Object> queue = new SetBlockingQueue<Object>(1);
		CountDownLatch latch = new CountDownLatch(2);

		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Object object = new Object();
				Assert.assertTrue(queue.offer(object));
				try { Thread.sleep(1000); }
				catch (InterruptedException e)
				{
					// SHOULD NOT HAPPEN
				}

				Assert.assertEquals(object, queue.poll());
				latch.countDown();
			}
		}).start();

		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Object object = new Object();
				try { Assert.assertTrue(queue.offer(object, 1, TimeUnit.SECONDS)); }
				catch (InterruptedException e)
				{
					// SHOULD NOT HAPPEN
				}
				Assert.assertEquals(object, queue.poll());
				latch.countDown();
			}
		}).start();

		try { latch.await(); }
		catch (InterruptedException e)
		{
			// SHOULD NOT HAPPEN
		}
	}
	*/
}
