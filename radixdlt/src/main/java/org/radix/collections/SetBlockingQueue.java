package org.radix.collections;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SetBlockingQueue<T> extends LinkedBlockingQueue<T>
{
	private final Set<T> set = new HashSet<T>();

	public SetBlockingQueue()
	{
		super(Integer.MAX_VALUE);
	}

	public SetBlockingQueue(Collection<? extends T> collection)
	{
		super(collection);
	}

	public SetBlockingQueue(int capacity)
	{
		super(capacity);
	}

	@Override
	public void clear()
	{
		super.clear();

		synchronized(this.set)
		{
			set.clear();
		}
	}

	@Override
	public boolean contains(Object object)
	{
		synchronized(this.set)
		{
			return this.set.contains(object);
		}
	}

	@Override
	public int drainTo(Collection<? super T> collection)
	{
		return drainTo(collection, Integer.MAX_VALUE);
	}
	
	@Override
	public int drainTo(Collection<? super T> collection, int maxElements)
	{
		int drained = super.drainTo(collection, maxElements);

		synchronized(this.set)
		{
			this.set.removeAll(collection);
		}
		
		return drained;
	}

	@Override
	public boolean offer(T object, long timeout, TimeUnit unit) throws InterruptedException
	{
		synchronized(this.set)
		{
			if (!this.set.add(object))
				return false;
		}
		
		try
		{
			boolean added = super.offer(object, timeout, unit);
				
			if (!added)
				synchronized(this.set)
				{
					this.set.remove(object);
				}
			
			return added;
		}
		catch (InterruptedException iex)
		{
			synchronized(this.set)
			{
				this.set.remove(object);
			}
			
			throw iex;
		}
	}

	@Override
	public boolean offer(T object)
	{
		synchronized(this.set)
		{
			if (this.set.add(object))
			{
				if (!super.offer(object))
					this.set.remove(object);
				else
					return true;
			}	
		}

		return false;
	}

	@Override
	public T poll()
	{
		T object = super.poll();

		synchronized(this.set)
		{
			if (object != null)
				this.set.remove(object);
		}

		return object;
	}

	@Override
	public T poll(long timeout, TimeUnit unit) throws InterruptedException
	{
		T object = super.poll(timeout, unit);

		synchronized(this.set)
		{
			if (object != null)
				this.set.remove(object);
		}

		return object;
	}
	
	@Override
	public T take() throws InterruptedException
	{
		T object = super.take();

		synchronized(this.set)
		{
			if (object != null)
				this.set.remove(object);
		}

		return object;
	}

	@Override
	public void put(T object) throws InterruptedException
	{
		this.add(object);
	}
	
	@Override
	public boolean add(T object)
	{
		synchronized(this.set)
		{
			if (this.set.contains(object))
				return false;

			return super.add(object);
		}
	}

	@Override
	public boolean addAll(Collection<? extends T> collection)
	{
		Set<T> copy = new HashSet<T>(collection);
		
		synchronized(this.set)
		{
			try
			{
				copy.removeAll(this.set);
				
				if (!copy.isEmpty())
				{
					boolean modified = false;
					for (T object : collection)
						modified |= add(object);
					
					return modified;
				}
			}
			catch (Exception ex)
			{
				this.set.removeAll(copy);
				throw ex;
			}
		}
		
		return false;
	}

	@Override
	public boolean removeAll(Collection<?> collection)
	{
		synchronized(this.set)
		{
			if (super.removeAll(collection))
			{
				this.set.removeAll(collection);
				return true;
			}
			
			return false;
		}
	}

	@Override
	public boolean retainAll(Collection<?> collection)
	{
		synchronized(this.set)
		{
			if (super.retainAll(collection))
			{
				this.set.retainAll(collection);
				return true;
			}
			
			return false;
		}
	}

	@Override
	public boolean remove(Object object)
	{
		synchronized(this.set)
		{
			if (super.remove(object))
			{
				this.set.remove(object);
				return true;
			}
			
			return false;
		}
	}
}
