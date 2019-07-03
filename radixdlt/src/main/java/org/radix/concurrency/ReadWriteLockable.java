package org.radix.concurrency;

public interface ReadWriteLockable 
{
	public enum LockType { READ, WRITE }
	
	public void	lock();
	public void	lock(LockType lockType);
	public void	lock(LockType lockType, boolean monitorLongLocks);
	public void	unlock(LockType lockType);
	public void	unlock();
}
