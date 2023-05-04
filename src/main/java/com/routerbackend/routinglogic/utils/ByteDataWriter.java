/**
 * fast data-writing to a byte-array
 *
 * @author ab
 */
package com.routerbackend.routinglogic.utils;


public class ByteDataWriter extends ByteDataReader {
  public ByteDataWriter(byte[] byteArray) {
    super(byteArray);
  }

  public final void writeVarLengthSigned(int v) {
    writeVarLengthUnsigned(v < 0 ? ((-v) << 1) | 1 : v << 1);
  }

  public final void writeVarLengthUnsigned(int v) {
    int i7 = v & 0x7f;
    if ((v >>>= 7) == 0) {
      ab[aboffset++] = (byte) (i7);
      return;
    }
    ab[aboffset++] = (byte) (i7 | 0x80);

    i7 = v & 0x7f;
    if ((v >>>= 7) == 0) {
      ab[aboffset++] = (byte) (i7);
      return;
    }
    ab[aboffset++] = (byte) (i7 | 0x80);

    i7 = v & 0x7f;
    if ((v >>>= 7) == 0) {
      ab[aboffset++] = (byte) (i7);
      return;
    }
    ab[aboffset++] = (byte) (i7 | 0x80);

    i7 = v & 0x7f;
    if ((v >>>= 7) == 0) {
      ab[aboffset++] = (byte) (i7);
      return;
    }
    ab[aboffset++] = (byte) (i7 | 0x80);

    ab[aboffset++] = (byte) (v);
  }
}
