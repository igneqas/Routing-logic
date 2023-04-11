package com.routerbackend.codec;

/**
 * Encoder/Decoder for signed integers that automatically detects the typical
 * range of these numbers to determine a noisy-bit count as a very simple
 * dictionary
 * <p>
 * Adapted for 3-pass encoding (counters -&gt; statistics -&gt; encoding )
 * but doesn't do anything at pass1
 */
public final class NoisyDiffCoder {
  private int noisybits;
  private StatCoderContext bc;

  /**
   * Create a decoder and read the noisy-bit count from the gibe context
   */
  public NoisyDiffCoder(StatCoderContext bc) {
    noisybits = bc.decodeVarBits();
    this.bc = bc;
  }

  /**
   * decodes a signed int
   */
  public int decodeSignedValue() {
    return bc.decodeNoisyDiff(noisybits);
  }
}
