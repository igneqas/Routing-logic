package com.routerbackend.routinglogic.codec;

import com.routerbackend.routinglogic.utils.IByteArrayUnifier;

/**
 * MicroCache2 is the new format that uses statistical encoding and
 * is able to do access filtering and waypoint matching during encoding
 */
public final class MicroCache2 extends MicroCache {
  private int lonBase;
  private int latBase;
  private int cellsize;

  public byte[] readUnified(int len, IByteArrayUnifier u) {
    byte[] b = u.unify(ab, aboffset, len);
    aboffset += len;
    return b;
  }

  public MicroCache2(StatCoderContext bc, DataBuffers dataBuffers, int lonIdx, int latIdx, int divisor, TagValueValidator wayValidator, WaypointMatcher waypointMatcher) {
    super(null);
    cellsize = 1000000 / divisor;
    lonBase = lonIdx * cellsize;
    latBase = latIdx * cellsize;

    TagValueCoder wayTagCoder = new TagValueCoder(bc, dataBuffers, wayValidator);
    TagValueCoder nodeTagCoder = new TagValueCoder(bc, dataBuffers, null);
    NoisyDiffCoder nodeIdxDiff = new NoisyDiffCoder(bc);
    NoisyDiffCoder nodeEleDiff = new NoisyDiffCoder(bc);
    NoisyDiffCoder extLonDiff = new NoisyDiffCoder(bc);
    NoisyDiffCoder extLatDiff = new NoisyDiffCoder(bc);
    NoisyDiffCoder transEleDiff = new NoisyDiffCoder(bc);

    size = bc.decodeNoisyNumber(5);
    faid = size > dataBuffers.ibuf2.length ? new int[size] : dataBuffers.ibuf2;
    fapos = size > dataBuffers.ibuf3.length ? new int[size] : dataBuffers.ibuf3;


    int[] alon = size > dataBuffers.alon.length ? new int[size] : dataBuffers.alon;
    int[] alat = size > dataBuffers.alat.length ? new int[size] : dataBuffers.alat;

    if (debug)
      System.out.println("*** decoding cache of size=" + size + " for lonIdx=" + lonIdx + " latIdx=" + latIdx);

    bc.decodeSortedArray(faid, 0, size, 29, 0);

    for (int n = 0; n < size; n++) {
      long id64 = expandId(faid[n]);
      alon[n] = (int) (id64 >> 32);
      alat[n] = (int) (id64 & 0xffffffff);
    }

    int netdatasize = bc.decodeNoisyNumber(10);
    ab = netdatasize > dataBuffers.bbuf1.length ? new byte[netdatasize] : dataBuffers.bbuf1;
    aboffset = 0;

    int[] validBits = new int[(size + 31) >> 5];

    int finaldatasize = 0;

    LinkedListContainer reverseLinks = new LinkedListContainer(size, dataBuffers.ibuf1);

    int selev = 0;
    for (int n = 0; n < size; n++) // loop over nodes
    {
      int ilon = alon[n];
      int ilat = alat[n];

      // future escapes (turn restrictions?)
      short trExceptions = 0;
      int featureId = bc.decodeVarBits();
      if (featureId == 13) {
        fapos[n] = aboffset;
        validBits[n >> 5] |= 1 << n; // mark dummy-node valid
        continue; // empty node escape (delta files only)
      }
      while (featureId != 0) {
        int bitsize = bc.decodeNoisyNumber(5);

        if (featureId == 2) // exceptions to turn-restriction
        {
          trExceptions = (short) bc.decodeBounded(1023);
        } else if (featureId == 1) // turn-restriction
        {
          writeBoolean(true);
          writeShort(trExceptions); // exceptions from previous feature
          trExceptions = 0;

          writeBoolean(bc.decodeBit()); // isPositive
          writeInt(ilon + bc.decodeNoisyDiff(10)); // fromLon
          writeInt(ilat + bc.decodeNoisyDiff(10)); // fromLat
          writeInt(ilon + bc.decodeNoisyDiff(10)); // toLon
          writeInt(ilat + bc.decodeNoisyDiff(10)); // toLat
        } else {
          for (int i = 0; i < bitsize; i++) bc.decodeBit(); // unknown feature, just skip
        }
        featureId = bc.decodeVarBits();
      }
      writeBoolean(false);

      selev += nodeEleDiff.decodeSignedValue();
      writeShort((short) selev);
      TagValueWrapper nodeTags = nodeTagCoder.decodeTagValueSet();
      writeVarBytes(nodeTags == null ? null : nodeTags.data);

      int links = bc.decodeNoisyNumber(1);
      if (debug)
        System.out.println("***   decoding node " + ilon + "/" + ilat + " with links=" + links);
      for (int li = 0; li < links; li++) {
        int sizeoffset = 0;
        int nodeIdx = n + nodeIdxDiff.decodeSignedValue();

        int dlon_remaining;
        int dlat_remaining;

        boolean isReverse = false;
        if (nodeIdx != n) // internal (forward-) link
        {
          dlon_remaining = alon[nodeIdx] - ilon;
          dlat_remaining = alat[nodeIdx] - ilat;
        } else {
          isReverse = bc.decodeBit();
          dlon_remaining = extLonDiff.decodeSignedValue();
          dlat_remaining = extLatDiff.decodeSignedValue();
        }
        if (debug)
          System.out.println("***     decoding link to " + (ilon + dlon_remaining) + "/" + (ilat + dlat_remaining) + " extern=" + (nodeIdx == n));

        TagValueWrapper wayTags = wayTagCoder.decodeTagValueSet();

        boolean linkValid = wayTags != null || wayValidator == null;
        if (linkValid) {
          int startPointer = aboffset;
          sizeoffset = writeSizePlaceHolder();

          writeVarLengthSigned(dlon_remaining);
          writeVarLengthSigned(dlat_remaining);

          validBits[n >> 5] |= 1 << n; // mark source-node valid
          if (nodeIdx != n) // valid internal (forward-) link
          {
            reverseLinks.addDataElement(nodeIdx, n); // register reverse link
            finaldatasize += 1 + aboffset - startPointer; // reserve place for reverse
            validBits[nodeIdx >> 5] |= 1 << nodeIdx; // mark target-node valid
          }
          writeModeAndDesc(isReverse, wayTags == null ? null : wayTags.data);
        }

        if (!isReverse) // write geometry for forward links only
        {
          WaypointMatcher matcher = wayTags == null || wayTags.accessType < 2 ? null : waypointMatcher;
          int ilontarget = ilon + dlon_remaining;
          int ilattarget = ilat + dlat_remaining;
          if (matcher != null) {
            if (!matcher.start(ilon, ilat, ilontarget, ilattarget)) {
              matcher = null;
            }
          }

          int transcount = bc.decodeVarBits();
          if (debug) System.out.println("***       decoding geometry with count=" + transcount);
          int count = transcount + 1;
          for (int i = 0; i < transcount; i++) {
            int dlon = bc.decodePredictedValue(dlon_remaining / count);
            int dlat = bc.decodePredictedValue(dlat_remaining / count);
            dlon_remaining -= dlon;
            dlat_remaining -= dlat;
            count--;
            int elediff = transEleDiff.decodeSignedValue();
            if (wayTags != null) {
              writeVarLengthSigned(dlon);
              writeVarLengthSigned(dlat);
              writeVarLengthSigned(elediff);
            }

            if (matcher != null)
              matcher.transferNode(ilontarget - dlon_remaining, ilattarget - dlat_remaining);
          }
          if (matcher != null) matcher.end();
        }
        if (linkValid) {
          injectSize(sizeoffset);
        }
      }
      fapos[n] = aboffset;
    }

    // calculate final data size
    int finalsize = 0;
    int startpos = 0;
    for (int i = 0; i < size; i++) {
      int endpos = fapos[i];
      if ((validBits[i >> 5] & (1 << i)) != 0) {
        finaldatasize += endpos - startpos;
        finalsize++;
      }
      startpos = endpos;
    }
    // append the reverse links at the end of each node
    byte[] abOld = ab;
    int[] faidOld = faid;
    int[] faposOld = fapos;
    int sizeOld = size;
    ab = new byte[finaldatasize];
    faid = new int[finalsize];
    fapos = new int[finalsize];
    aboffset = 0;
    size = 0;

    startpos = 0;
    for (int n = 0; n < sizeOld; n++) {
      int endpos = faposOld[n];
      if ((validBits[n >> 5] & (1 << n)) != 0) {
        int len = endpos - startpos;
        System.arraycopy(abOld, startpos, ab, aboffset, len);
        if (debug)
          System.out.println("*** copied " + len + " bytes from " + aboffset + " for node " + n);
        aboffset += len;

        int cnt = reverseLinks.initList(n);
        if (debug)
          System.out.println("*** appending " + cnt + " reverse links for node " + n);

        for (int ri = 0; ri < cnt; ri++) {
          int nodeIdx = reverseLinks.getDataElement();
          int sizeoffset = writeSizePlaceHolder();
          writeVarLengthSigned(alon[nodeIdx] - alon[n]);
          writeVarLengthSigned(alat[nodeIdx] - alat[n]);
          writeModeAndDesc(true, null);
          injectSize(sizeoffset);
        }
        faid[size] = faidOld[n];
        fapos[size] = aboffset;
        size++;
      }
      startpos = endpos;
    }
    init(size);
  }

  @Override
  public long expandId(int id32) {
    int dlon = 0;
    int dlat = 0;

    for (int bm = 1; bm < 0x8000; bm <<= 1) {
      if ((id32 & 1) != 0) dlon |= bm;
      if ((id32 & 2) != 0) dlat |= bm;
      id32 >>= 2;
    }

    int lon32 = lonBase + dlon;
    int lat32 = latBase + dlat;

    return ((long) lon32) << 32 | lat32;
  }

  @Override
  public int shrinkId(long id64) {
    int lon32 = (int) (id64 >> 32);
    int lat32 = (int) (id64 & 0xffffffff);
    int dlon = lon32 - lonBase;
    int dlat = lat32 - latBase;
    int id32 = 0;

    for (int bm = 0x4000; bm > 0; bm >>= 1) {
      id32 <<= 2;
      if ((dlon & bm) != 0) id32 |= 1;
      if ((dlat & bm) != 0) id32 |= 2;
    }
    return id32;
  }
}
