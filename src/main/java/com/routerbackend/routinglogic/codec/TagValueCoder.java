package com.routerbackend.routinglogic.codec;

import com.routerbackend.routinglogic.utils.BitCoderContext;

/**
 * Encoder/Decoder for way-/node-descriptions
 * <p>
 * It detects identical descriptions and sorts them
 * into a huffman-tree according to their frequencies
 * <p>
 * Adapted for 3-pass encoding (counters -&gt; statistics -&gt; encoding )
 * but doesn't do anything at pass1
 */
public final class TagValueCoder {
  private Object tree;
  private BitCoderContext bc;

  public TagValueWrapper decodeTagValueSet() {
    Object node = tree;
    while (node instanceof TreeNode) {
      TreeNode tn = (TreeNode) node;
      boolean nextBit = bc.decodeBit();
      node = nextBit ? tn.child2 : tn.child1;
    }
    return (TagValueWrapper) node;
  }

  public TagValueCoder(BitCoderContext bc, TagValueValidator validator) {
    tree = decodeTree(bc, validator);
    this.bc = bc;
  }

  private Object decodeTree(BitCoderContext bc, TagValueValidator validator) {
    boolean isNode = bc.decodeBit();
    if (isNode) {
      TreeNode node = new TreeNode();
      node.child1 = decodeTree(bc, validator);
      node.child2 = decodeTree(bc, validator);
      return node;
    }

    byte[] buffer = new byte[256];
    BitCoderContext ctx = new BitCoderContext(new byte[256]);
    ctx.reset(buffer);

    int inum = 0;
    int lastEncodedInum = 0;

    boolean hasdata = false;
    for (; ; ) {
      int delta = bc.decodeVarBits();
      if (!hasdata) {
        if (delta == 0) {
          return null;
        }
      }
      if (delta == 0) {
        ctx.encodeVarBits(0);
        break;
      }
      inum += delta;

      int data = bc.decodeVarBits();

      if (validator == null || validator.isLookupIdxUsed(inum)) {
        hasdata = true;
        ctx.encodeVarBits(inum - lastEncodedInum);
        ctx.encodeVarBits(data);
        lastEncodedInum = inum;
      }
    }

    byte[] res;
    int len = ctx.closeAndGetEncodedLength();
    if (validator == null) {
      res = new byte[len];
      System.arraycopy(buffer, 0, res, 0, len);
    } else {
      res = validator.unify(buffer, 0, len);
    }

    int accessType = validator == null ? 2 : validator.accessType(res);
    if (accessType > 0) {
      TagValueWrapper w = new TagValueWrapper();
      w.data = res;
      w.accessType = accessType;
      return w;
    }
    return null;
  }

  public static final class TreeNode {
    public Object child1;
    public Object child2;
  }
}
