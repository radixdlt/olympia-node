package test.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class AttackTests
{
//	@Test
	public void hashGeneratorEstimator() 
	{
		int MAX_LEADING = 40;
		
		Random random = new Random();
		byte[] candidate = new byte[32];
		
		for (int l = 0 ; l < MAX_LEADING ; l++)
		{
			long start = System.currentTimeMillis();
			boolean found = false;
			long iterations = 0;
			
			while(found == false)
			{
				random.nextBytes(candidate);
				
				BitSet bitset = BitSet.valueOf(candidate);
				
				if (bitset.nextClearBit(0) == l)
				{
					System.out.println(l+": "+iterations+" iterations  Took: "+(System.currentTimeMillis()-start));
					found = true;
				}
				else
					iterations++;
			}
		}
	}	
	
//	@Test
	public void gossipDiscoveryTablePoisoning() 
	{
		int MATCH_RANGE = 4;
		
		Random random = new Random();
		Map<Integer, ArrayList<byte[]>> gossipMap = new HashMap<Integer, ArrayList<byte[]>>(); 
		
		byte[] target = new byte[32];
		random.nextBytes(target);
		target[0] = 0;
//		target[1] = 0;
		
		long iterations = 0;
		int discovered = 0;
		byte[] candidate = new byte[32];
		
		while(discovered < 1000)
		{
			random.nextBytes(candidate);
			
			iterations++;
			
			if (candidate[0] != 0)
				continue;

//			if (candidate[0] != 0 || candidate[1] != 0)
//				continue;

			int slot = getGossipMapSlot(target, candidate);
			
			if (!gossipMap.containsKey(slot))
				gossipMap.put(slot, new ArrayList<byte[]>());
			
			gossipMap.get(slot).add(candidate);
			
			discovered++;
		}
		
		int maxSlot = 0;
		for (int i = 0 ; i < target.length ; i++)
			if (gossipMap.containsKey(i)) 
			{
				maxSlot = i;
				System.out.println("Slot "+i+": "+gossipMap.get(i).size()+" items");
			}
		
		byte[] result = new byte[32];
		boolean found = false;
		iterations = 0;
		while(found == false)
		{
			random.nextBytes(candidate);
//			candidate[0] = 0;
//			candidate[1] = 0;
			
			if (getGossipMapSlot(target, candidate) > maxSlot)
			{
				found = false;
				break;
			}
			
			iterations++;
		}
		
		System.out.println("Target: "+Arrays.toString(target));
		System.out.println("Candidate: "+Arrays.toString(candidate));
		System.out.println("Result: "+Arrays.toString(result));
		System.out.println("Iterations: "+iterations);
	}
	
	public int getGossipMapSlot(byte[] target, byte[] candidate)
	{
		BitSet targetBitSet = BitSet.valueOf(target);
		BitSet candidateBitSet = BitSet.valueOf(candidate);
		
		candidateBitSet.xor(targetBitSet);
		return candidateBitSet.nextSetBit(0);
		
/*		byte[] result = new byte[32];
		
		for (int i = 0 ; i < candidate.length ; i++)
			result[i] = (byte) (candidate[i] ^ target[i]);

		for (int i = 0 ; i < result.length ; i++)
			if (result[i] != 0)
				return i;
		
		return result.length;*/
	}
}
