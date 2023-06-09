/**
 * cache for a single square
 *
 * @author ab
 */
package com.routerbackend.routinglogic.mapaccess;

import com.routerbackend.routinglogic.codec.*;
import com.routerbackend.routinglogic.utils.ByteDataReader;
import com.routerbackend.routinglogic.utils.ByteDataWriter;
import com.routerbackend.routinglogic.utils.Crc32;

import java.io.IOException;
import java.io.RandomAccessFile;

final class OsmFile {
  private RandomAccessFile is = null;
  private long fileOffset;
  private int[] posIdx;
  private ByteDataWriter[] microCaches;
  public int lonDegree;
  public int latDegree;
  public String filename;
  private int divisor;
  private int cellsize;
  private int ncaches;
  private int indexsize;

  public OsmFile(PhysicalFile rafile, int lonDegree, int latDegree) throws IOException {
    this.lonDegree = lonDegree;
    this.latDegree = latDegree;
    int lonMod5 = lonDegree % 5;
    int latMod5 = latDegree % 5;
    int tileIndex = lonMod5 * 5 + latMod5;

    if (rafile != null) {
      divisor = rafile.divisor;

      cellsize = 1000000 / divisor;
      ncaches = divisor * divisor;
      indexsize = ncaches * 4;

      byte[] iobuffer = new byte[65636];
      filename = rafile.fileName;

      long[] index = rafile.fileIndex;
      fileOffset = tileIndex > 0 ? index[tileIndex - 1] : 200L;
      if (fileOffset == index[tileIndex])
        return; // empty

      is = rafile.ra;
      posIdx = new int[ncaches];
      microCaches = new ByteDataWriter[ncaches];
      is.seek(fileOffset);
      is.readFully(iobuffer, 0, indexsize);

      if (rafile.fileHeaderCrcs != null) {
        int headerCrc = Crc32.crc(iobuffer, 0, indexsize);
        if (rafile.fileHeaderCrcs[tileIndex] != headerCrc) {
          throw new IOException("sub index checksum error");
        }
      }

      ByteDataReader dis = new ByteDataReader(iobuffer);
      for (int i = 0; i < ncaches; i++) {
        posIdx[i] = dis.readInt();
      }
    }
  }

  public boolean hasData() {
    return microCaches != null;
  }

  public ByteDataWriter getMicroCache(int ilon, int ilat) {
    int lonIdx = ilon / cellsize;
    int latIdx = ilat / cellsize;
    int subIdx = (latIdx - divisor * latDegree) * divisor + (lonIdx - divisor * lonDegree);
    return microCaches[subIdx];
  }

  public ByteDataWriter createMicroCache(int ilon, int ilat, TagValueValidator wayValidator, WaypointMatcher waypointMatcher, OsmNodesMap hollowNodes)
    throws Exception {
    int lonIdx = ilon / cellsize;
    int latIdx = ilat / cellsize;
    ByteDataWriter segment = createMicroCache(lonIdx, latIdx, wayValidator, waypointMatcher, true, hollowNodes);
    int subIdx = (latIdx - divisor * latDegree) * divisor + (lonIdx - divisor * lonDegree);
    microCaches[subIdx] = segment;
    return segment;
  }

  private int getPosIdx(int idx) {
    return idx == -1 ? indexsize : posIdx[idx];
  }

  public int getDataInputForSubIdx(int subIdx, byte[] iobuffer) throws IOException {
    int startPos = getPosIdx(subIdx - 1);
    int endPos = getPosIdx(subIdx);
    int size = endPos - startPos;
    if (size > 0) {
      is.seek(fileOffset + startPos);
      if (size <= iobuffer.length) {
        is.readFully(iobuffer, 0, size);
      }
    }

    return size;
  }

  public ByteDataWriter createMicroCache(int lonIdx, int latIdx, TagValueValidator wayValidator,
                                     WaypointMatcher waypointMatcher, boolean reallyDecode, OsmNodesMap hollowNodes) throws IOException {
    int subIdx = (latIdx - divisor * latDegree) * divisor + (lonIdx - divisor * lonDegree);

    byte[] ab = new byte[65636];
    int asize = getDataInputForSubIdx(subIdx, ab);

    if (asize > ab.length) {
      ab = new byte[asize];
      asize = getDataInputForSubIdx(subIdx, ab);
    }

    StatCoderContext bc = new StatCoderContext(ab);

    try {
      if (!reallyDecode) {
        return null;
      }
      new DirectWeaver(bc, lonIdx, latIdx, divisor, wayValidator, waypointMatcher, hollowNodes);
      return new ByteDataWriter(null);
    } finally {
      // crc check only if the buffer has not been fully read
      int readBytes = (bc.getReadingBitPosition() + 7) >> 3;
      if (readBytes != asize - 4) {
        int crcData = Crc32.crc(ab, 0, asize - 4);
        int crcFooter = new ByteDataReader(ab, asize - 4).readInt();
        if (crcData == crcFooter) {
          throw new IOException("old, unsupported data-format");
        } else if ((crcData ^ 2) != crcFooter) {
          throw new IOException("checkum error");
        }
      }
    }
  }

  // set this OsmFile to ghost-state:
  void setGhostState() {
    int nc = microCaches == null ? 0 : microCaches.length;
    for (int i = 0; i < nc; i++) {
      ByteDataWriter mc = microCaches[i];
      if (mc == null)
        continue;

      microCaches[i] = null;
    }
  }

  void clean() {
    int microCachesSize = microCaches == null ? 0 : microCaches.length;
    for (int i = 0; i < microCachesSize; i++) {
      ByteDataWriter mc = microCaches[i];
      if (mc == null)
        continue;
      microCaches[i] = null;
    }
  }
}
