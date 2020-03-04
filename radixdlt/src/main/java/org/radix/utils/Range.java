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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.radix.shards.ShardRange;

public class Range<T extends Number & Comparable<T>>
{
	private T low, high;

	protected Range()
	{}

	public Range(T low, T high)
	{
		if (low.compareTo(high) > 0)
			throw new IllegalStateException("'low' shard can not be greater than 'high' shard");

		this.low = low;
		this.high = high;
	}

	protected void setLow(T low)
	{
		this.low = low;
	}

	protected void setHigh(T high)
	{
		this.high = high;
	}

	@Override
	public final boolean equals(Object other)
	{
		if (other == this)
			return true;

		if (other == null)
			return false;

		// `FooRange` should not automatically equal `(Range) BarRange`.
		if (!this.getClass().equals(other.getClass()))
			return false;

		if (other instanceof ShardRange) {
			ShardRange range = (ShardRange) other;
			return Objects.equals(range.getLow(), getLow()) && Objects.equals(range.getHigh(), getHigh());
		}
		return false;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(getLow(), getHigh());
	}

	@Override
	public String toString()
	{
		return getLow()+" -> "+getHigh();
	}

	public T getLow()
	{
		return this.low;
	}

	public T getHigh()
	{
		return this.high;
	}

	public Set<T> intersection(Set<T> points)
	{
		Set<T> intersections = new HashSet<>();

		for (T point : points)
			if (intersects(point))
				intersections.add(point);

		return Collections.unmodifiableSet(intersections);
	}

	public boolean intersects(T point)
	{
		if (this.low.intValue() == 0 && this.high.intValue() == 0)
			return false;

		if (point.compareTo(this.low) < 0 || point.compareTo(this.high) > 0)
			return false;

		return true;
	}

	public boolean intersects(Range<T> range)
	{
		if (this.low.intValue() == 0 && this.high.intValue() == 0)
			return false;

		if (range.getHigh().compareTo(this.low) < 0 || range.getLow().compareTo(this.high) > 0)
			return false;

		return true;
	}

	public boolean intersects(Collection<T> points)
	{
		if (this.low.intValue() == 0 && this.high.intValue() == 0)
			return false;

		for (T point : points)
			if (intersects(point))
				return true;

		return false;
	}

	public boolean contains(Range<T> range)
	{
		return contains(range.getLow(), range.getHigh());
	}

	public boolean contains(T low, T high)
	{
		if (this.low.compareTo(low) <= 0 && this.high.compareTo(high) >= 0)
			return true;

		return false;
	}

	public T getSpan()
	{
		if (this.high instanceof Float)
			return (T) Float.valueOf(this.high.floatValue() - this.low.floatValue());
		else if (this.high instanceof Double)
			return (T) Double.valueOf(this.high.doubleValue() - this.low.doubleValue());
		else if (this.high instanceof BigDecimal)
			return (T) ((BigDecimal)this.high).subtract((BigDecimal) this.low);
		else if (this.high instanceof BigInteger)
			return (T) ((BigInteger)this.high).subtract((BigInteger) this.low);
		else if (this.high instanceof Long)
			return (T) Long.valueOf(this.high.longValue() - this.low.longValue());
		else if (this.high instanceof Integer)
			return (T) Integer.valueOf(this.high.intValue() - this.low.intValue());
		else if (this.high instanceof Short)
			return (T) Short.valueOf((short) (this.high.shortValue() - this.low.shortValue()));
		else if (this.high instanceof Byte)
			return (T) Byte.valueOf((byte) (this.high.byteValue() - this.low.byteValue()));

		throw new IllegalStateException("Can not calculate range span with type "+this.high.getClass());
	}

}
