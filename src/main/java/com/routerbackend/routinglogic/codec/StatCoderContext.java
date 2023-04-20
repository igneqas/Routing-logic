package com.routerbackend.routinglogic.codec;

import com.routerbackend.routinglogic.utils.BitCoderContext;

public final class StatCoderContext extends BitCoderContext {


  private static final int[] noisy_bits = new int[1024];

  static {
    // noisybits lookup
    for (int i = 0; i < 1024; i++) {
      int p = i;
      int noisybits = 0;
      while (p > 2) {
        noisybits++;
        p >>= 1;
      }
      noisy_bits[i] = noisybits;
    }
  }


  public StatCoderContext(byte[] ab) {
    super(ab);
  }

  /**
   * decode an unsigned integer with some of least significant bits
   * considered noisy
   *
   */
  public int decodeNoisyNumber(int noisybits) {
    int value = decodeBits(noisybits);
    return value | (decodeVarBits() << noisybits);
  }

  /**
   * decode a signed integer with some of of least significant bits considered
   * noisy
   *
   */
  public int decodeNoisyDiff(int noisybits) {
    int value = 0;
    if (noisybits > 0) {
      value = decodeBits(noisybits) - (1 << (noisybits - 1));
    }
    int val2 = decodeVarBits() << noisybits;
    if (val2 != 0) {
      if (decodeBit()) {
        val2 = -val2;
      }
    }
    return value + val2;
  }

  /**
   * decode a signed integer with the typical range and median taken from the
   * predicted value
   *
   */
  public int decodePredictedValue(int predictor) {
    int p = predictor < 0 ? -predictor : predictor;
    int noisybits = 0;
    while (p > 1023) {
      noisybits++;
      p >>= 1;
    }
    return predictor + decodeNoisyDiff(noisybits + noisy_bits[p]);
  }

  /**
   * @param values  the array to encode
   * @param offset  position in this array where to start
   * @param subsize number of values to encode
   * @param nextbitpos bitmask with the most significant bit set to 1
   * @param value   should be 0
   */
  public void decodeSortedArray(int[] values, int offset, int subsize, int nextbitpos, int value) {
    if (subsize == 1) // last-choice shortcut
    {
      if (nextbitpos >= 0) {
        value |= decodeBitsReverse(nextbitpos + 1);
      }
      values[offset] = value;
      return;
    }
    if (nextbitpos < 0) {
      while (subsize-- > 0) {
        values[offset++] = value;
      }
      return;
    }

    int size1 = decodeBounded(subsize);
    int size2 = subsize - size1;

    if (size1 > 0) {
      decodeSortedArray(values, offset, size1, nextbitpos - 1, value);
    }
    if (size2 > 0) {
      decodeSortedArray(values, offset + size1, size2, nextbitpos - 1, value | (1 << nextbitpos));
    }
  }

}
