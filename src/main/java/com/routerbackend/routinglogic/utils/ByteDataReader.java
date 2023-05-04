/**
 * fast data-reading from a byte-array
 *
 * @author ab
 */
package com.routerbackend.routinglogic.utils;


public class ByteDataReader {
  protected byte[] ab;
  protected int aboffset;
  protected int aboffsetEnd;

  public ByteDataReader(byte[] byteArray) {
    ab = byteArray;
    aboffsetEnd = ab == null ? 0 : ab.length;
  }

  public ByteDataReader(byte[] byteArray, int offset) {
    ab = byteArray;
    aboffset = offset;
    aboffsetEnd = ab == null ? 0 : ab.length;
  }

  public final void reset(byte[] byteArray) {
    ab = byteArray;
    aboffset = 0;
    aboffsetEnd = ab == null ? 0 : ab.length;
  }


  public final int readInt() {
    int i3 = ab[aboffset++] & 0xff;
    int i2 = ab[aboffset++] & 0xff;
    int i1 = ab[aboffset++] & 0xff;
    int i0 = ab[aboffset++] & 0xff;
    return (i3 << 24) + (i2 << 16) + (i1 << 8) + i0;
  }

  public final long readLong() {
    long i7 = ab[aboffset++] & 0xff;
    long i6 = ab[aboffset++] & 0xff;
    long i5 = ab[aboffset++] & 0xff;
    long i4 = ab[aboffset++] & 0xff;
    long i3 = ab[aboffset++] & 0xff;
    long i2 = ab[aboffset++] & 0xff;
    long i1 = ab[aboffset++] & 0xff;
    long i0 = ab[aboffset++] & 0xff;
    return (i7 << 56) + (i6 << 48) + (i5 << 40) + (i4 << 32) + (i3 << 24) + (i2 << 16) + (i1 << 8) + i0;
  }

  public final int readVarLengthSigned() {
    int v = readVarLengthUnsigned();
    return (v & 1) == 0 ? v >> 1 : -(v >> 1);
  }

  public final int readVarLengthUnsigned() {
    byte b;
    int v = (b = ab[aboffset++]) & 0x7f;
    if (b >= 0) return v;
    v |= ((b = ab[aboffset++]) & 0x7f) << 7;
    if (b >= 0) return v;
    v |= ((b = ab[aboffset++]) & 0x7f) << 14;
    if (b >= 0) return v;
    v |= ((b = ab[aboffset++]) & 0x7f) << 21;
    if (b >= 0) return v;
    v |= ((b = ab[aboffset++]) & 0xf) << 28;
    return v;
  }

  public final boolean hasMoreData() {
    return aboffset < aboffsetEnd;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < ab.length; i++)
      sb.append(i == 0 ? " " : ", ").append(Integer.toString(ab[i]));
    sb.append(" ]");
    return sb.toString();
  }

}
