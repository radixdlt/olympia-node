/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.radix.utils;

import com.radixdlt.utils.RadixConstants;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.BitSet;
import java.util.Collection;
import java.util.Map;

import org.radix.containers.BasicContainer;
import com.radixdlt.utils.WireIO;
import com.radixdlt.serialization.DsonAnyProperties;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.MapHelper;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Implementation of a Bloom-filter, as described here:
 * http://en.wikipedia.org/wiki/Bloom_filter
 *
 * For updates and bugfixes, see http://github.com/magnuss/java-bloomfilter
 *
 * Inspired by the SimpleBloomFilter-class written by Ian Clarke. This
 * implementation provides a more evenly distributed Hash-function by
 * using a proper digest instead of the Java RNG. Many of the changes
 * were proposed in comments in his blog:
 * http://blog.locut.us/2008/01/12/a-decent-stand-alone-java-bloom-filter-implementation/
 *
 * @param <E> Object type that is to be inserted into the Bloom filter, e.g. String or Integer.
 * @author Dan Hughes
 */
@SerializerId2("internal.bloomer")
public class Bloomer<E> extends BasicContainer /*CryptoObject<Null>*/ implements Serializable
{
	@Override
	public short VERSION() { return 100;}

	/**
	 *
	 */
	private static final long serialVersionUID = -2564822127471283758L;

	private BitSet 	bitset;
    private int 	bitSetSize;
    private double 	bitsPerElement;
    private int 	expectedNumberOfFilterElements; // expected (maximum) number of elements to be added
    private int 	numberOfAddedElements; // number of elements actually added to the Bloom filter
    private int 	k; // number of hash functions
    private String	label;

    static final Charset charset = RadixConstants.STANDARD_CHARSET; // encoding used for storing hash values as strings

    static final String hashName = "MD5"; // MD5 gives good enough accuracy in most circumstances. Change to SHA1 if it's needed
    static final MessageDigest digestFunction;

    static
    {
    	// The digest method is reused between instances
        MessageDigest tmp;

        try
        {
            tmp = java.security.MessageDigest.getInstance(hashName);
        }
        catch (NoSuchAlgorithmException e)
        {
            tmp = null;
        }

        digestFunction = tmp;
    }

    public Bloomer()
    {
    	super();
    }

	public Bloomer(InputStream stream) throws IOException
	{
		this();

		deserialize(new WireIO.Reader(stream));
	}

	/**
      * Constructs an empty Bloom filter. The total length of the Bloom filter will be
      * c*n.
      *
      * @param c is the number of bits used per element.
      * @param n is the expected number of elements the filter will contain.
      * @param k is the number of hash functions used.
      */
    public Bloomer(double c, int n, int k, String label)
    {
      this.expectedNumberOfFilterElements = n;
      this.k = k;
      this.bitsPerElement = c;
      this.bitSetSize = (int)Math.ceil(c * n);
      numberOfAddedElements = 0;
      this.bitset = new BitSet(bitSetSize);
      this.label = label;
    }

    /**
     * Constructs an empty Bloom filter. The optimal number of hash functions (k) is estimated from the total size of the Bloom
     * and the number of expected elements.
     *
     * @param bitSetSize defines how many bits should be used in total for the filter.
     * @param expectedNumberOElements defines the maximum number of elements the filter is expected to contain.
     */
    public Bloomer(int bitSetSize, int expectedNumberOElements, String label)
    {
        this(bitSetSize / (double)expectedNumberOElements,
             expectedNumberOElements,
             (int) Math.round((bitSetSize / (double)expectedNumberOElements) * Math.log(2.0)), label);
    }

    /**
     * Constructs an empty Bloom filter with a given false positive probability. The number of bits per
     * element and the number of hash functions is estimated
     * to match the false positive probability.
     *
     * @param falsePositiveProbability is the desired false positive probability.
     * @param expectedNumberOfElements is the expected number of elements in the Bloom filter.
     */
    public Bloomer(double falsePositiveProbability, int expectedNumberOfElements, String label)
    {
        this(Math.ceil(-(Math.log(falsePositiveProbability) / Math.log(2))) / Math.log(2), // c = k / ln(2)
             expectedNumberOfElements,
             (int)Math.ceil(-(Math.log(falsePositiveProbability) / Math.log(2))), label); // k = ceil(-log_2(false prob.))
    }

    /**
     * Construct a new Bloom filter based on existing Bloom filter data.
     *
     * @param bitSetSize defines how many bits should be used for the filter.
     * @param expectedNumberOfFilterElements defines the maximum number of elements the filter is expected to contain.
     * @param actualNumberOfFilterElements specifies how many elements have been inserted into the <code>filterData</code> BitSet.
     * @param filterData a BitSet representing an existing Bloom filter.
     */
    public Bloomer(int bitSetSize, int expectedNumberOfFilterElements, int actualNumberOfFilterElements, BitSet filterData, String label)
    {
        this(bitSetSize, expectedNumberOfFilterElements, label);
        this.bitset = filterData;
        this.numberOfAddedElements = actualNumberOfFilterElements;
    }

    /**
     * Generates a digest based on the contents of a String.
     *
     * @param val specifies the input data.
     * @param charset specifies the encoding of the input data.
     * @return digest as long.
     */
    public static int createHash(String val, Charset charset)
    {
        return createHash(val.getBytes(charset));
    }

    /**
     * Generates a digest based on the contents of a String.
     *
     * @param val specifies the input data. The encoding is expected to be UTF-8.
     * @return digest as long.
     */
    public static int createHash(String val)
    {
        return createHash(val, charset);
    }

    /**
     * Generates a digest based on the contents of an array of bytes.
     *
     * @param data specifies input data.
     * @return digest as long.
     */
    public static int createHash(byte[] data)
    {
        return createHashes(data, 1)[0];
    }

    /**
     * Generates digests based on the contents of an array of bytes and splits the result into 4-byte int's and store them in an array. The
     * digest function is called until the required number of int's are produced. For each call to digest a salt
     * is prepended to the data. The salt is increased by 1 for each call.
     *
     * @param data specifies input data.
     * @param hashes number of hashes/int's to produce.
     * @return array of int-sized hashes
     */
    public static int[] createHashes(byte[] data, int hashes)
    {
        int[] result = new int[hashes];

        int k = 0;
        byte salt = 0;
        while (k < hashes)
        {
            byte[] digest;
            synchronized (digestFunction)
            {
                digestFunction.update(salt);
                salt++;
                digest = digestFunction.digest(data);
            }

            for (int i = 0; i < digest.length/4 && k < hashes; i++)
            {
                int h = 0;
                for (int j = (i*4); j < (i*4)+4; j++)
                {
                    h <<= 8;
                    h |= digest[j] & 0xFF;
                }
                result[k] = h;
                k++;
            }
        }
        return result;
    }

    /**
     * Compares the contents of two instances to see if they are equal.
     *
     * @param obj is the object to compare to.
     * @return True if the contents of the objects are equal.
     */
    @Override
    public boolean equals(Object obj)
    {
        if (obj == null)
            return false;

        if (getClass() != obj.getClass())
            return false;

        final Bloomer<?> other = (Bloomer<?>) obj;
        if (this.expectedNumberOfFilterElements != other.expectedNumberOfFilterElements)
            return false;

        if (this.k != other.k)
            return false;

        if (this.bitSetSize != other.bitSetSize)
            return false;

        if (this.bitset != other.bitset && (this.bitset == null || !this.bitset.equals(other.bitset)))
            return false;

        return true;
    }

    /**
     * Calculates a hash code for this class.
     * @return hash code representing the contents of an instance of this class.
     */
    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 61 * hash + (this.bitset != null ? this.bitset.hashCode() : 0);
        hash = 61 * hash + this.expectedNumberOfFilterElements;
        hash = 61 * hash + this.bitSetSize;
        hash = 61 * hash + this.k;
        return hash;
    }


    /**
     * Calculates the expected probability of false positives based on
     * the number of expected filter elements and the size of the Bloom filter.
     * <br /><br />
     * The value returned by this method is the <i>expected</i> rate of false
     * positives, assuming the number of inserted elements equals the number of
     * expected elements. If the number of elements in the Bloom filter is less
     * than the expected value, the true probability of false positives will be lower.
     *
     * @return expected probability of false positives.
     */
    public double expectedFalsePositiveProbability()
    {
        return getFalsePositiveProbability(expectedNumberOfFilterElements);
    }

    /**
     * Calculate the probability of a false positive given the specified
     * number of inserted elements.
     *
     * @param numberOfElements number of inserted elements.
     * @return probability of a false positive.
     */
    public double getFalsePositiveProbability(double numberOfElements)
    {
        // (1 - e^(-k * n / m)) ^ k
        return Math.pow(1.0 - Math.exp(-k * numberOfElements / bitSetSize), k);
    }

    /**
     * Get the current probability of a false positive. The probability is calculated from
     * the size of the Bloom filter and the current number of elements added to it.
     *
     * @return probability of false positives.
     */
    public double getFalsePositiveProbability()
    {
        return getFalsePositiveProbability(numberOfAddedElements);
    }


    /**
     * Returns the value chosen for K.<br />
     * <br />
     * K is the optimal number of hash functions based on the size
     * of the Bloom filter and the expected number of inserted elements.
     *
     * @return optimal k.
     */
    public int getK()
    {
        return k;
    }

    /**
     * Sets all bits to false in the Bloom filter.
     */
    public void clear()
    {
		synchronized(this)
		{
			bitset.clear();
			numberOfAddedElements = 0;
		}
    }

    /**
     * Adds an object to the Bloom filter. The output from the object's
     * toString() method is used as input to the hash functions.
     *
     * @param element is an element to register in the Bloom filter.
     */
    public void add(E element)
    {
    	byte[] bytes = element.toString().getBytes(charset);

		synchronized(this)
		{
			if (!contains(bytes))
				add(element.toString().getBytes(charset));
		}
    }

    /**
     * Adds an array of bytes to the Bloom filter.
     *
     * @param bytes array of bytes to add to the Bloom filter.
     */
    public void add(byte[] bytes)
    {
    	int[] hashes = createHashes(bytes, k);

    	synchronized(this)
		{
			for (int hash : hashes)
				bitset.set(Math.abs(hash % bitSetSize), true);

			numberOfAddedElements ++;
		}
    }

    /**
     * Adds all elements from a Collection to the Bloom filter.
     * @param c Collection of elements.
     */
    public void addAll(Collection<? extends E> c)
    {
		synchronized(this)
		{
			for (E element : c)
				add(element);
		}
    }

    /**
     * Returns true if the element could have been inserted into the Bloom filter.
     * Use getFalsePositiveProbability() to calculate the probability of this
     * being correct.
     *
     * @param element element to check.
     * @return true if the element could have been inserted into the Bloom filter.
     */
    public boolean contains(E element)
    {
		synchronized(this)
		{
			return contains(element.toString().getBytes(charset));
		}
    }

    /**
     * Returns true if the array of bytes could have been inserted into the Bloom filter.
     * Use getFalsePositiveProbability() to calculate the probability of this
     * being correct.
     *
     * @param bytes array of bytes to check.
     * @return true if the array could have been inserted into the Bloom filter.
     */
    public boolean contains(byte[] bytes)
    {
        int[] hashes = createHashes(bytes, k);

		synchronized(this)
		{
	        for (int hash : hashes)
	        {
	            if (!bitset.get(Math.abs(hash % bitSetSize)))
	                return false;
	        }
		}

        return true;
    }

    /**
     * Returns true if all the elements of a Collection could have been inserted
     * into the Bloom filter. Use getFalsePositiveProbability() to calculate the
     * probability of this being correct.
     * @param c elements to check.
     * @return true if all the elements in c could have been inserted into the Bloom filter.
     */
    public boolean containsAll(Collection<? extends E> c)
    {
		synchronized(this)
		{
			for (E element : c)
				if (!contains(element))
					return false;
		}

        return true;
    }

    /**
     * Read a single bit from the Bloom filter.
     * @param bit the bit to read.
     * @return true if the bit is set, false if it is not.
     */
    public boolean getBit(int bit)
    {
		synchronized(this)
		{
			return bitset.get(bit);
		}
    }

    /**
     * Set a single bit in the Bloom filter.
     * @param bit is the bit to set.
     * @param value If true, the bit is set. If false, the bit is cleared.
     */
    public void setBit(int bit, boolean value)
    {
		synchronized(this)
		{
			bitset.set(bit, value);
		}
    }

    /**
     * Return the bit set used to store the Bloom filter.
     * @return bit set representing the Bloom filter.
     */
    public BitSet getBitSet() {
        return bitset;
    }

    /**
     * Return the label for this Bloom filter.  Label is also used for id
     * @return Bloom filter label.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the number of bits in the Bloom filter. Use count() to retrieve
     * the number of inserted elements.
     *
     * @return the size of the bitset used by the Bloom filter.
     */
    public int size() {
        return this.bitSetSize;
    }

    /**
     * Returns the number of elements added to the Bloom filter after it
     * was constructed or after clear() was called.
     *
     * @return number of elements added to the Bloom filter.
     */
    public int count() {
        return this.numberOfAddedElements;
    }

    /**
     * Returns the expected number of elements to be inserted into the filter.
     * This value is the same value as the one passed to the constructor.
     *
     * @return expected number of elements.
     */
    public int getExpectedNumberOfElements() {
        return expectedNumberOfFilterElements;
    }

    /**
     * Get expected number of bits per element when the Bloom filter is full. This value is set by the constructor
     * when the Bloom filter is created. See also getBitsPerElement().
     *
     * @return expected number of bits per element.
     */
    public double getExpectedBitsPerElement() {
        return this.bitsPerElement;
    }

    /**
     * Get actual number of bits per element based on the number of elements that have currently been inserted and the length
     * of the Bloom filter. See also getExpectedBitsPerElement().
     *
     * @return number of bits per element.
     */
    public double getBitsPerElement() {
        return this.bitSetSize / (double)numberOfAddedElements;
    }


	public final void serialize(OutputStream outputStream) throws IOException
	{
		WireIO.Writer writer = new WireIO.Writer(outputStream);
		serialize(writer);
		outputStream.flush();
	}

	public void serialize(WireIO.Writer writer) throws IOException
	{
		writer.writeInt(bitSetSize);
		writer.writeVarBytes(bitset.toByteArray());
		writer.writeInt(expectedNumberOfFilterElements);
		writer.writeInt(numberOfAddedElements);
		writer.writeString(label);
	}

	// All JSON properties - 1 Any Getter, 1 Any Setter
	// Properties serialized this way to ensure that values are consistent
	@JsonAnyGetter
	@DsonOutput(Output.ALL)
	@DsonAnyProperties(value = {"bit_set_size", "bit_set", "expected_num_elements", "num_elements", "label"})
	Map<String, Object> getJsonProperties() {
		synchronized (this) {
			return MapHelper.mapOf(
					"bit_set_size", Integer.valueOf(this.bitSetSize),
					"bit_set", this.bitset.toByteArray(),
					"expected_num_elements", Integer.valueOf(this.expectedNumberOfFilterElements),
					"num_elements", Integer.valueOf(this.numberOfAddedElements),
					"label", this.label);
		}
	}

	// Assuming that synchronization on write is not required.
	// Presumably the object is being initialized before use.
	@JsonProperty("bit_set")
	private void setJsonBitset(byte[] value) {
		this.bitset = BitSet.valueOf(value);
	}

	@JsonProperty("bit_set_size")
	private void setJsonBitset(int size) {
		this.bitSetSize = size;
	}

	@JsonProperty("expected_num_elements")
	private void setJsonExpectedNumberOfFilterElements(int value) {
		this.expectedNumberOfFilterElements = value;
	}

	@JsonProperty("label")
	private void setJsonLabel(String label) {
		this.label = label;
	}

	@JsonProperty("num_elements")
	private void setJsonNumberOfAddedElements(int value) {
		this.numberOfAddedElements = value;
	}

	public final void deserialize(InputStream inputStream) throws IOException
	{
		deserialize(new WireIO.Reader(inputStream));
	}

	public void deserialize(WireIO.Reader reader) throws IOException
	{
		bitSetSize = reader.readInt();
		bitset = BitSet.valueOf(reader.readVarBytes());
		expectedNumberOfFilterElements = reader.readInt();
		numberOfAddedElements = reader.readInt();
		label = reader.readString();

		this.bitsPerElement = bitSetSize / (double)expectedNumberOfFilterElements;
    	this.k = (int) Math.round((bitSetSize / (double)expectedNumberOfFilterElements) * Math.log(2.0));
	}
}