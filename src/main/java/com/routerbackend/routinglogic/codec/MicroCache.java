package com.routerbackend.routinglogic.codec;

import com.routerbackend.routinglogic.utils.ByteDataWriter;

/**
 * a micro-cache is a data cache for an area of some square kilometers or some
 * hundreds or thousands nodes
 * <p>
 * This is the basic io-unit: always a full microcache is loaded from the
 * data-file if a node is requested at a position not yet covered by the caches
 * already loaded
 * <p>
 * The nodes are represented in a compact way (typical 20-50 bytes per node),
 * but in a way that they do not depend on each other, and garbage collection is
 * supported to remove the nodes already consumed from the cache.
 * <p>
 * The cache-internal data representation is different from that in the
 * data-files, where a cache is encoded as a whole, allowing more
 * redundancy-removal for a more compact encoding
 */
public class MicroCache extends ByteDataWriter {
  protected int[] faid;
  protected int[] fapos;
  protected int size = 0;

  private int delcount = 0;
  private int delbytes = 0;
  private int p2size; // next power of 2 of size

  // cache control: a virgin cache can be
  // put to ghost state for later recovery
  public boolean virgin = true;
  public boolean ghost = false;

  protected MicroCache(byte[] ab) {
    super(ab);
  }

  public final static MicroCache emptyNonVirgin = new MicroCache(null);

  static {
    emptyNonVirgin.virgin = false;
  }

  public static MicroCache emptyCache() {
    return new MicroCache(null);
  }

  protected void init(int size) {
    this.size = size;
    delcount = 0;
    delbytes = 0;
    p2size = 0x40000000;
    while (p2size > size)
      p2size >>= 1;
  }

  public final int getSize() {
    return size;
  }

  public final int getDataSize() {
    return ab == null ? 0 : ab.length;
  }

  protected final int startPos(int n) {
    return n > 0 ? fapos[n - 1] & 0x7fffffff : 0;
  }

  public final int collect(int threshold) {
    if (delcount <= threshold) {
      return 0;
    }

    virgin = false;

    int nsize = size - delcount;
    if (nsize == 0) {
      faid = null;
      fapos = null;
    } else {
      int[] nfaid = new int[nsize];
      int[] nfapos = new int[nsize];
      int idx = 0;

      byte[] nab = new byte[ab.length - delbytes];
      int nab_off = 0;
      for (int i = 0; i < size; i++) {
        int pos = fapos[i];
        if ((pos & 0x80000000) == 0) {
          int start = startPos(i);
          int end = fapos[i];
          int len = end - start;
          System.arraycopy(ab, start, nab, nab_off, len);
          nfaid[idx] = faid[i];
          nab_off += len;
          nfapos[idx] = nab_off;
          idx++;
        }
      }
      faid = nfaid;
      fapos = nfapos;
      ab = nab;
    }
    int deleted = delbytes;
    init(nsize);
    return deleted;
  }

  public final void unGhost() {
    ghost = false;
    delcount = 0;
    delbytes = 0;
    for (int i = 0; i < size; i++) {
      fapos[i] &= 0x7fffffff; // clear deleted flags
    }
  }
}
