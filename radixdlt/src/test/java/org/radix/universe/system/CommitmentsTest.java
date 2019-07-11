package org.radix.universe.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.radixdlt.common.AID;
import org.junit.Assert;
import org.junit.Test;
import org.radix.serialization.RadixTest;

public class CommitmentsTest extends RadixTest
{
	private static final Random rng = new Random();
	private static AID randomAID() {
		byte[] randomBytes = new byte[AID.BYTES];
		rng.nextBytes(randomBytes);
		return AID.from(randomBytes);
	}

	@Test
	public void put_single_aid_to_accumulator__check_single_aid_present_in_accumulator()
	{
		CommitmentAccumulator commitmentAccumulator = new CommitmentAccumulator(AID.BYTES * 8);

		AID aid = randomAID();
		commitmentAccumulator.put(aid);
		Assert.assertTrue(commitmentAccumulator.has(aid));
	}

	@Test
	public void fill_accumulator__check_multiple_aids_present_in_accumulator()
	{
		CommitmentAccumulator commitmentAccumulator = new CommitmentAccumulator(AID.BYTES * 8);

		List<AID> aids = new ArrayList<>();
		for (int h = 0 ; h < AID.BYTES * 8 ; h++)
		{
			AID aid = randomAID();
			commitmentAccumulator.put(aid);
			aids.add(aid);
		}

		for (int h = 0 ; h < AID.BYTES * 8 ; h++)
			Assert.assertTrue("should contain aid " + h + ": " + aids.get(h),
				commitmentAccumulator.has(aids.get(h)));
	}

	@Test
	public void fill_accumulator__insert_bitstreams_into_collector__check_single_aid_present_in_collector_at_index_to_threshold()
	{
		CommitmentAccumulator commitmentAccumulator = new CommitmentAccumulator(AID.BYTES * 8);
		CommitmentCollector commitmentCollector = new CommitmentCollector(AID.BYTES * 8);

		List<AID> aids = new ArrayList<>();
		for (int h = 0 ; h < AID.BYTES * 8 ; h++)
		{
			AID aid = randomAID();
			commitmentAccumulator.put(aid);
			commitmentCollector.put(h, commitmentAccumulator.toByteArray());
			aids.add(aid);
		}

		Assert.assertTrue(commitmentCollector.has(0, aids.get(0).getBytes(), AID.BYTES * 8));
	}

	@Test
	public void fill_and_slide_accumulator__insert_bitstreams_into_collector__search_for_random_aid_present_in_collector_to_threshold()
	{
		CommitmentAccumulator commitmentAccumulator = new CommitmentAccumulator(AID.BYTES * 8);
		CommitmentCollector commitmentCollector = new CommitmentCollector(AID.BYTES * 8);

		List<AID> aids = new ArrayList<>();
		for (int h = 0 ; h < AID.BYTES * 8*2 ; h++)
		{
			AID aid = randomAID();
			commitmentAccumulator.put(aid);
			commitmentCollector.put(h, commitmentAccumulator.toByteArray());

			if (h < AID.BYTES * 8)
				aids.add(aid);
		}

		Assert.assertTrue(commitmentCollector.has(aids.get((int) (java.lang.System.nanoTime() % aids.size())).getBytes(), AID.BYTES * 8));
	}

	@Test
	public void fill_and_slide_accumulator__insert_bitstreams_into_collector__search_for_random_aid_not_present_in_collector_to_threshold()
	{
		CommitmentAccumulator commitmentAccumulator = new CommitmentAccumulator(AID.BYTES * 8);
		CommitmentCollector commitmentCollector = new CommitmentCollector(AID.BYTES * 8);

		List<AID> aids = new ArrayList<>();
		for (int h = 0 ; h < AID.BYTES * 8<<1 ; h++)
		{
			AID aid = randomAID();
			commitmentAccumulator.put(aid);
			commitmentCollector.put(h, commitmentAccumulator.toByteArray());

			if (h < AID.BYTES * 8)
				aids.add(aid);
		}

		Assert.assertFalse(commitmentCollector.has(randomAID().getBytes(), AID.BYTES * 8));
	}

	@Test
	public void put_multiple_aids_to_accumulator__insert_bitstreams_into_collector__check_multiple_aids_are_present_in_collector_with_index_to_threshold()
	{
		CommitmentAccumulator commitmentAccumulator = new CommitmentAccumulator(AID.BYTES * 8);
		CommitmentCollector commitmentCollector = new CommitmentCollector(AID.BYTES * 8);

		List<AID> aids = new ArrayList<>();
		for (int h = 0 ; h < AID.BYTES * 8 ; h++)
		{
			AID aid = randomAID();
			commitmentAccumulator.put(aid);
			commitmentCollector.put(h, commitmentAccumulator.toByteArray());
			aids.add(aid);
		}

		Assert.assertTrue(commitmentCollector.has(0, aids.get(0).getBytes(), AID.BYTES * 8));
		Assert.assertTrue(commitmentCollector.has(1, aids.get(1).getBytes(), AID.BYTES * 8-1));
		Assert.assertFalse(commitmentCollector.has(2, aids.get(2).getBytes(), AID.BYTES * 8-1));
	}

	@Test
	public void put_multiple_aids_into_accumulator__insert_bitstreams_into_collector__get_threshold_of_first_aid_in_collector()
	{
		CommitmentAccumulator commitmentAccumulator = new CommitmentAccumulator(AID.BYTES * 8);
		CommitmentCollector commitmentCollector = new CommitmentCollector(AID.BYTES * 8);

		List<AID> aids = new ArrayList<>();
		for (int h = 0 ; h < AID.BYTES * 8 ; h++)
		{
			AID aid = randomAID();
			commitmentAccumulator.put(aid);
			commitmentCollector.put(h, commitmentAccumulator.toByteArray());
			aids.add(aid);
		}

		Assert.assertEquals(AID.BYTES * 8, commitmentCollector.has(0, aids.get(0).getBytes()));
	}

	@Test
	public void put_multiple_aids_into_accumulator__insert_bitstreams_into_collector__get_threshold_of_all_aids_in_collector()
	{
		CommitmentAccumulator commitmentAccumulator = new CommitmentAccumulator(AID.BYTES * 8);
		CommitmentCollector commitmentCollector = new CommitmentCollector(AID.BYTES * 8);

		List<AID> aids = new ArrayList<>();
		for (int h = 0 ; h < AID.BYTES * 8 ; h++)
		{
			AID aid = randomAID();
			commitmentAccumulator.put(aid);
			commitmentCollector.put(h, commitmentAccumulator.toByteArray());
			aids.add(aid);
		}

		for (int h = 0 ; h < AID.BYTES * 8 ; h++)
			Assert.assertEquals(AID.BYTES * 8-h, commitmentCollector.has(h, aids.get(h).getBytes()));
	}

	@Test
	public void put_multiple_aids_into_accumulator__insert_every_second_bitstream_into_collector__get_threshold_of_all_aids_in_collector()
	{
		CommitmentAccumulator commitmentAccumulator = new CommitmentAccumulator(AID.BYTES * 8);
		CommitmentCollector commitmentCollector = new CommitmentCollector(AID.BYTES * 8);

		List<AID> aids = new ArrayList<>();
		for (int h = 0 ; h < AID.BYTES * 8 ; h++)
		{
			AID aid = randomAID();
			commitmentAccumulator.put(aid);

			if ((h % 2) == 0)
				commitmentCollector.put(h, commitmentAccumulator.toByteArray());

			aids.add(aid);
		}

		for (int h = 0 ; h < AID.BYTES * 8 ; h++)
			Assert.assertEquals((AID.BYTES * 8-h)>>1, commitmentCollector.has(h, aids.get(h).getBytes()));
	}
}
