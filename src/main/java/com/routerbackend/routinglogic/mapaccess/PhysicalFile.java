/**
 * cache for a single square
 *
 * @author ab
 */
package com.routerbackend.routinglogic.mapaccess;

import com.routerbackend.routinglogic.codec.DataBuffers;
import com.routerbackend.routinglogic.utils.ByteDataReader;
import com.routerbackend.routinglogic.utils.Crc32;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public final class PhysicalFile {
  RandomAccessFile ra;
  long[] fileIndex = new long[25];
  int[] fileHeaderCrcs;
  public long creationTime;
  String fileName;
  public int divisor = 80;

  public PhysicalFile(File f, DataBuffers dataBuffers, int lookupVersion) throws IOException {
    fileName = f.getName();
    byte[] iobuffer = dataBuffers.iobuffer;
    ra = new RandomAccessFile(f, "r");
    ra.readFully(iobuffer, 0, 200);
    int fileIndexCrc = Crc32.crc(iobuffer, 0, 200);
    ByteDataReader dis = new ByteDataReader(iobuffer);
    for (int i = 0; i < 25; i++) {
      long lv = dis.readLong();
      short readVersion = (short) (lv >> 48);
      if (i == 0 && lookupVersion != -1 && readVersion != lookupVersion) {
        throw new IOException("lookup version mismatch (old rd5?) lookups.dat="
          + lookupVersion + " " + f.getName() + "=" + readVersion);
      }
      fileIndex[i] = lv & 0xffffffffffffL;
    }

    // read some extra info from the end of the file, if present
    long len = ra.length();

    long pos = fileIndex[24];
    int extraLen = 8 + 26 * 4;
    if (len == pos) return; // old format o.k.
    if (len < pos + extraLen) // > is o.k. for future extensions!
    {
      throw new IOException("file of size " + len + " too short, should be " + (pos + extraLen));
    }

    ra.seek(pos);
    ra.readFully(iobuffer, 0, extraLen);
    dis = new ByteDataReader(iobuffer);
    creationTime = dis.readLong();
    int crcData = dis.readInt();
    if (crcData == fileIndexCrc) {
      divisor = 80; // old format
    } else if ((crcData ^ 2) == fileIndexCrc) {
      divisor = 32; // new format
    } else {
      throw new IOException("top index checksum error");
    }

    fileHeaderCrcs = new int[25];
    for (int i = 0; i < 25; i++) {
      fileHeaderCrcs[i] = dis.readInt();
    }
  }
}
