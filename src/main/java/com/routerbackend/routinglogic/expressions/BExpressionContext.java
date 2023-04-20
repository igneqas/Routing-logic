// context for simple expression
// context means:
// - the local variables
// - the local variable names
// - the lookup-input variables

package com.routerbackend.routinglogic.expressions;

import com.routerbackend.routinglogic.utils.BitCoderContext;
import com.routerbackend.routinglogic.utils.IByteArrayUnifier;
import com.routerbackend.routinglogic.utils.Crc32;
import com.routerbackend.routinglogic.utils.LruMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;


public abstract class BExpressionContext implements IByteArrayUnifier {
  private static final String CONTEXT_TAG = "---context:";
  private static final String MODEL_TAG = "---model:";

  private String context;
  private boolean _inOurContext = false;
  private BufferedReader _br = null;
  private boolean _readerDone = false;

  public String _modelClass;

  private Map<String, Integer> lookupNumbers = new HashMap<String, Integer>();
  private List<BExpressionLookupValue[]> lookupValues = new ArrayList<BExpressionLookupValue[]>();
  private List<String> lookupNames = new ArrayList<String>();
  private List<int[]> lookupHistograms = new ArrayList<int[]>();
  private boolean[] lookupIdxUsed;

  private boolean lookupDataFrozen = false;

  private int[] lookupData = new int[0];

  private byte[] abBuf = new byte[256];
  private BitCoderContext ctxEndode = new BitCoderContext(abBuf);
  private BitCoderContext ctxDecode = new BitCoderContext(new byte[0]);

  private Map<String, Integer> variableNumbers = new HashMap<String, Integer>();

  private float[] variableData;


  // hash-cache for function results
  private CacheNode probeCacheNode = new CacheNode();
  private LruMap cache;

  private VarWrapper probeVarSet = new VarWrapper();
  private LruMap resultVarCache;

  private List<BExpression> expressionList;

  private int minWriteIdx;

  // build-in variable indexes for fast access
  private int[] buildInVariableIdx;
  private int nBuildInVars;

  private float[] currentVars;
  private int currentVarOffset;

  private BExpressionContext foreignContext;

  protected void setInverseVars() {
    currentVarOffset = nBuildInVars;
  }

  abstract String[] getBuildInVariableNames();

  public final float getBuildInVariable(int idx) {
    return currentVars[idx + currentVarOffset];
  }

  private int linenr;

  public BExpressionMetaData meta;
  private boolean lookupDataValid = false;

  protected BExpressionContext(String context, BExpressionMetaData meta) {
    this(context, 4096, meta);
  }

  /**
   * Create an Expression-Context for the given node
   *
   * @param context  global, way or node - context of that instance
   * @param hashSize size of hashmap for result caching
   */
  protected BExpressionContext(String context, int hashSize, BExpressionMetaData meta) {
    this.context = context;
    this.meta = meta;

    if (meta != null) meta.registerListener(context, this);

    if (Boolean.getBoolean("disableExpressionCache")) hashSize = 1;

    // create the expression cache
    if (hashSize > 0) {
      cache = new LruMap(4 * hashSize, hashSize);
      resultVarCache = new LruMap(4096, 4096);
    }
  }

  /**
   * encode internal lookup data to a byte array
   */
  public byte[] encode() {
    if (!lookupDataValid)
      throw new IllegalArgumentException("internal error: encoding undefined data?");
    return encode(lookupData);
  }

  public byte[] encode(int[] ld) {
    BitCoderContext ctx = ctxEndode;
    ctx.reset();

    int skippedTags = 0;
    int nonNullTags = 0;

    // (skip first bit ("reversedirection") )

    // all others are generic
    for (int inum = 1; inum < lookupValues.size(); inum++) // loop over lookup names
    {
      int d = ld[inum];
      if (d == 0) {
        skippedTags++;
        continue;
      }
      ctx.encodeVarBits(skippedTags + 1);
      nonNullTags++;
      skippedTags = 0;

      // 0 excluded already, 1 (=unknown) we rotate up to 8
      // to have the good code space for the popular values
      int dd = d < 2 ? 7 : (d < 9 ? d - 2 : d - 1);
      ctx.encodeVarBits(dd);
    }
    ctx.encodeVarBits(0);

    if (nonNullTags == 0) return null;

    int len = ctx.closeAndGetEncodedLength();
    byte[] ab = new byte[len];
    System.arraycopy(abBuf, 0, ab, 0, len);


    // crosscheck: decode and compare
    int[] ld2 = new int[lookupValues.size()];
    decode(ld2, false, ab);
    for (int inum = 1; inum < lookupValues.size(); inum++) // loop over lookup names (except reverse dir)
    {
      if (ld2[inum] != ld[inum])
        throw new RuntimeException("assertion failed encoding inum=" + inum + " val=" + ld[inum] + " " + getKeyValueDescription(false, ab));
    }

    return ab;
  }

  /**
   * decode a byte-array into a lookup data array
   */
  private void decode(int[] ld, boolean inverseDirection, byte[] ab) {
    BitCoderContext ctx = ctxDecode;
    ctx.reset(ab);

    // start with first bit hardwired ("reversedirection")
    ld[0] = inverseDirection ? 2 : 0;

    // all others are generic
    int inum = 1;
    for (; ; ) {
      int delta = ctx.decodeVarBits();
      if (delta == 0) break;
      if (inum + delta > ld.length) break; // higher minor version is o.k.

      while (delta-- > 1) ld[inum++] = 0;

      // see encoder for value rotation
      int dd = ctx.decodeVarBits();
      int d = dd == 7 ? 1 : (dd < 7 ? dd + 2 : dd + 1);
      if (d >= lookupValues.get(inum).length && d < 1000) d = 1; // map out-of-range to unknown
      ld[inum++] = d;
    }
    while (inum < ld.length) ld[inum++] = 0;
  }

  public String getKeyValueDescription(boolean inverseDirection, byte[] ab) {
    StringBuilder sb = new StringBuilder(200);
    decode(lookupData, inverseDirection, ab);
    for (int inum = 0; inum < lookupValues.size(); inum++) // loop over lookup names
    {
      BExpressionLookupValue[] va = lookupValues.get(inum);
      int val = lookupData[inum];
      String value = (val >= 1000) ? Float.toString((val - 1000) / 100f) : va[val].toString();
      if (value != null && value.length() > 0) {
        if (sb.length() > 0) sb.append(' ');
        sb.append(lookupNames.get(inum) + "=" + value);
      }
    }
    return sb.toString();
  }

  public float getLookupValue(int key) {
    float res = 0f;
    int val = lookupData[key];
    if (val == 0) return Float.NaN;
    res = (val - 1000) / 100f;
    return res;
  }

  private int parsedLines = 0;
  private boolean fixTagsWritten = false;

  public void parseMetaLine(String line) {
    parsedLines++;
    StringTokenizer tk = new StringTokenizer(line, " ");
    String name = tk.nextToken();
    String value = tk.nextToken();
    int idx = name.indexOf(';');
    if (idx >= 0) name = name.substring(0, idx);

    if (!fixTagsWritten) {
      fixTagsWritten = true;
      if ("way".equals(context)) addLookupValue("reversedirection", "yes", null);
      else if ("node".equals(context)) addLookupValue("nodeaccessgranted", "yes", null);
    }
    if ("reversedirection".equals(name)) return; // this is hardcoded
    if ("nodeaccessgranted".equals(name)) return; // this is hardcoded
    BExpressionLookupValue newValue = addLookupValue(name, value, null);

    // add aliases
    while (newValue != null && tk.hasMoreTokens()) newValue.addAlias(tk.nextToken());
  }

  public void finishMetaParsing() {
    if (parsedLines == 0 && !"global".equals(context)) {
      throw new IllegalArgumentException("lookup table does not contain data for context " + context + " (old version?)");
    }

    // post-process metadata:
    lookupDataFrozen = true;

    lookupIdxUsed = new boolean[lookupValues.size()];
  }

  public final void evaluate(int[] lookupData2) {
    lookupData = lookupData2;
    evaluate();
  }

  private void evaluate() {
    int n = expressionList.size();
    for (int expidx = 0; expidx < n; expidx++) {
      expressionList.get(expidx).evaluate(this);
    }
  }

  private CacheNode lastCacheNode = new CacheNode();

   @Override
  public final byte[] unify(byte[] ab, int offset, int len) {
    probeCacheNode.ab = null; // crc based cache lookup only
    probeCacheNode.hash = Crc32.crc(ab, offset, len);

    CacheNode cn = (CacheNode) cache.get(probeCacheNode);
    if (cn != null) {
      byte[] cab = cn.ab;
      if (cab.length == len) {
        for (int i = 0; i < len; i++) {
          if (cab[i] != ab[i + offset]) {
            cn = null;
            break;
          }
        }
        if (cn != null) {
          lastCacheNode = cn;
          return cn.ab;
        }
      }
    }
    byte[] nab = new byte[len];
    System.arraycopy(ab, offset, nab, 0, len);
    return nab;
  }


  public final void evaluate(boolean inverseDirection, byte[] ab) {
    lookupDataValid = false; // this is an assertion for a nasty pifall

    if (cache == null) {
      decode(lookupData, inverseDirection, ab);
      if (currentVars == null || currentVars.length != nBuildInVars) {
        currentVars = new float[nBuildInVars];
      }
      evaluateInto(currentVars, 0);
      currentVarOffset = 0;
      return;
    }

    CacheNode cn;
    if (lastCacheNode.ab == ab) {
      cn = lastCacheNode;
    } else {
      probeCacheNode.ab = ab;
      probeCacheNode.hash = Crc32.crc(ab, 0, ab.length);
      cn = (CacheNode) cache.get(probeCacheNode);
    }

    if (cn == null) {
      cn = (CacheNode) cache.removeLru();
      if (cn == null) {
        cn = new CacheNode();
      }
      cn.hash = probeCacheNode.hash;
      cn.ab = ab;
      cache.put(cn);

      if (probeVarSet.vars == null) {
        probeVarSet.vars = new float[2 * nBuildInVars];
      }

      // forward direction
      decode(lookupData, false, ab);
      evaluateInto(probeVarSet.vars, 0);

      // inverse direction
      lookupData[0] = 2; // inverse shortcut: reuse decoding
      evaluateInto(probeVarSet.vars, nBuildInVars);

      probeVarSet.hash = Arrays.hashCode(probeVarSet.vars);

      // unify the result variable set
      VarWrapper vw = (VarWrapper) resultVarCache.get(probeVarSet);
      if (vw == null) {
        vw = (VarWrapper) resultVarCache.removeLru();
        if (vw == null) {
          vw = new VarWrapper();
        }
        vw.hash = probeVarSet.hash;
        vw.vars = probeVarSet.vars;
        probeVarSet.vars = null;
        resultVarCache.put(vw);
      }
      cn.vars = vw.vars;
    } else {
      cache.touch(cn);
    }

    currentVars = cn.vars;
    currentVarOffset = inverseDirection ? nBuildInVars : 0;
  }

  private void evaluateInto(float[] vars, int offset) {
    evaluate();
    for (int vi = 0; vi < nBuildInVars; vi++) {
      int idx = buildInVariableIdx[vi];
      vars[vi + offset] = idx == -1 ? 0.f : variableData[idx];
    }
  }

  /**
   * @return a new lookupData array, or null if no metadata defined
   */
  public int[] createNewLookupData() {
    if (lookupDataFrozen) {
      return new int[lookupValues.size()];
    }
    return null;
  }

  /**
   * add a new lookup-value for the given name to the given lookupData array.
   * If no array is given (null value passed), the value is added to
   * the context-binded array. In that case, unknown names and values are
   * created dynamically.
   *
   * @return a newly created value element, if any, to optionally add aliases
   */
  public BExpressionLookupValue addLookupValue(String name, String value, int[] lookupData2) {
    BExpressionLookupValue newValue = null;
    Integer num = lookupNumbers.get(name);
    if (num == null) {
      if (lookupData2 != null) {
        // do not create unknown name for external data array
        return newValue;
      }

      // unknown name, create
      num = Integer.valueOf(lookupValues.size());
      lookupNumbers.put(name, num);
      lookupNames.add(name);
      lookupValues.add(new BExpressionLookupValue[]{new BExpressionLookupValue("")
        , new BExpressionLookupValue("unknown")});
      lookupHistograms.add(new int[2]);
      int[] ndata = new int[lookupData.length + 1];
      System.arraycopy(lookupData, 0, ndata, 0, lookupData.length);
      lookupData = ndata;
    }

    // look for that value
    int inum = num.intValue();
    BExpressionLookupValue[] values = lookupValues.get(inum);
    int[] histo = lookupHistograms.get(inum);
    int i = 0;
    boolean bFoundAsterix = false;
    for (; i < values.length; i++) {
      BExpressionLookupValue v = values[i];
      if (v.equals("*")) bFoundAsterix = true;
      if (v.matches(value)) break;
    }
    if (i == values.length) {
      if (lookupData2 != null) {
        // do not create unknown value for external data array,
        // record as 'unknown' instead
        lookupData2[inum] = 1; // 1 == unknown
        if (bFoundAsterix) {
          // found value for lookup *
          //System.out.println( "add unknown " + name + "  " + value );
          String org = value;
          try {
            // remove some unused characters
            value = value.replace(",", ".");
            value = value.replace(">", "");
            value = value.replace("_", "");
            if (value.indexOf("-") == 0) value = value.substring(1);
            if (value.indexOf("~") == 0) value = value.substring(1);
            if (value.contains("-")) {    // replace eg. 1.4-1.6 m
              String tmp = value.substring(value.indexOf("-") + 1).replaceAll("[0-9.,-]", "");
              value = value.substring(0, value.indexOf("-")) + tmp;
            }
            // do some value conversion
            if (value.toLowerCase().contains("ft")) {
              float foot = 0f;
              int inch = 0;
              String[] sa = value.toLowerCase().trim().split("ft");
              if (sa.length >= 1) foot = Float.parseFloat(sa[0].trim());
              if (sa.length == 2) {
                value = sa[1];
                if (value.indexOf("in") > 0) value = value.substring(0, value.indexOf("in"));
                inch = Integer.parseInt(value.trim());
                foot += inch / 12f;
              }
              value = String.format(Locale.US, "%3.1f", foot * 0.3048f);
            }
            if (value.toLowerCase().contains("'")) {
              float foot = 0f;
              int inch = 0;
              String[] sa = value.toLowerCase().trim().split("'");
              if (sa.length >= 1) foot = Float.parseFloat(sa[0].trim());
              if (sa.length == 2) {
                value = sa[1];
                if (value.indexOf("''") > 0) value = value.substring(0, value.indexOf("''"));
                if (value.indexOf("\"") > 0) value = value.substring(0, value.indexOf("\""));
                inch = Integer.parseInt(value.trim());
                foot += inch / 12f;
              }
              value = String.format(Locale.US, "%3.1f", foot * 0.3048f);
            } else if (value.contains("in") || value.contains("\"")) {
              float inch = 0f;
              if (value.indexOf("in") > 0) value = value.substring(0, value.indexOf("in"));
              if (value.indexOf("\"") > 0) value = value.substring(0, value.indexOf("\""));
              inch = Float.parseFloat(value.trim());
              value = String.format(Locale.US, "%3.1f", inch * 0.0254f);
            } else if (value.toLowerCase().contains("feet") || value.toLowerCase().contains("foot")) {
              float feet = 0f;
              String s = value.substring(0, value.toLowerCase().indexOf("f"));
              feet = Float.parseFloat(s.trim());
              value = String.format(Locale.US, "%3.1f", feet * 0.3048f);
            } else if (value.toLowerCase().contains("fathom") || value.toLowerCase().contains("fm")) {
              float fathom = 0f;
              String s = value.substring(0, value.toLowerCase().indexOf("f"));
              fathom = Float.parseFloat(s.trim());
              value = String.format(Locale.US, "%3.1f", fathom * 1.8288f);
            } else if (value.contains("cm")) {
              String[] sa = value.trim().split("cm");
              if (sa.length == 1) value = sa[0].trim();
              float cm = Float.parseFloat(value.trim());
              value = String.format(Locale.US, "%3.1f", cm * 100f);
            } else if (value.toLowerCase().contains("meter")) {
              String s = value.substring(0, value.toLowerCase().indexOf("m"));
              value = s.trim();
            } else if (value.toLowerCase().contains("mph")) {
              value = value.replace("_", "");
              String[] sa = value.trim().toLowerCase().split("mph");
              if (sa.length >= 1) value = sa[0].trim();
              float mph = Float.parseFloat(value.trim());
              value = String.format(Locale.US, "%3.1f", mph * 1.609344f);
            } else if (value.toLowerCase().contains("knot")) {
              String[] sa = value.trim().toLowerCase().split("knot");
              if (sa.length >= 1) value = sa[0].trim();
              float nm = Float.parseFloat(value.trim());
              value = String.format(Locale.US, "%3.1f", nm * 1.852f);
            } else if (value.contains("kmh") || value.contains("km/h") || value.contains("kph")) {
              String[] sa = value.trim().split("k");
              if (sa.length == 1) value = sa[0].trim();
            } else if (value.contains("m")) {
              String s = value.substring(0, value.toLowerCase().indexOf("m"));
              value = s.trim();
            } else if (value.contains("(")) {
              String s = value.substring(0, value.toLowerCase().indexOf("("));
              value = s.trim();
            }
            // found negative maxdraft values
            // no negative values
            // values are float with 2 decimals
            lookupData2[inum] = 1000 + (int) (Math.abs(Float.parseFloat(value)) * 100f);
          } catch (Exception e) {
            // ignore errors
            System.err.println("error for " + name + "  " + org + " trans " + value + " " + e.getMessage());
            lookupData2[inum] = 0;
          }
        }
        return newValue;
      }

      if (i == 499) {
        // System.out.println( "value limit reached for: " + name );
      }
      if (i == 500) {
        return newValue;
      }
      // unknown value, create
      BExpressionLookupValue[] nvalues = new BExpressionLookupValue[values.length + 1];
      int[] nhisto = new int[values.length + 1];
      System.arraycopy(values, 0, nvalues, 0, values.length);
      System.arraycopy(histo, 0, nhisto, 0, histo.length);
      values = nvalues;
      histo = nhisto;
      newValue = new BExpressionLookupValue(value);
      values[i] = newValue;
      lookupHistograms.set(inum, histo);
      lookupValues.set(inum, values);
    }

    histo[i]++;

    // finally remember the actual data
    if (lookupData2 != null) lookupData2[inum] = i;
    else lookupData[inum] = i;
    return newValue;
  }

  public int getOutputVariableIndex(String name, boolean mustExist) {
    int idx = getVariableIdx(name, false);
    if (idx < 0) {
      if (mustExist) {
        throw new IllegalArgumentException("unknown variable: " + name);
      }
    } else if (idx < minWriteIdx) {
      throw new IllegalArgumentException("bad access to global variable: " + name);
    }
    for (int i = 0; i < nBuildInVars; i++) {
      if (buildInVariableIdx[i] == idx) {
        return i;
      }
    }
    int[] extended = new int[nBuildInVars + 1];
    System.arraycopy(buildInVariableIdx, 0, extended, 0, nBuildInVars);
    extended[nBuildInVars] = idx;
    buildInVariableIdx = extended;
    return nBuildInVars++;
  }

  public void setForeignContext(BExpressionContext foreignContext) {
    this.foreignContext = foreignContext;
  }

  public float getForeignVariableValue(int foreignIndex) {
    return foreignContext.getBuildInVariable(foreignIndex);
  }

  public int getForeignVariableIdx(String context, String name) {
    if (foreignContext == null || !context.equals(foreignContext.context)) {
      throw new IllegalArgumentException("unknown foreign context: " + context);
    }
    return foreignContext.getOutputVariableIndex(name, true);
  }

  public void parseFile(File file, String readOnlyContext) {
    if (!file.exists()) {
      throw new IllegalArgumentException("profile " + file + " does not exist");
    }
    try {
      if (readOnlyContext != null) {
        linenr = 1;
        String realContext = context;
        context = readOnlyContext;
        expressionList = _parseFile(file);
        variableData = new float[variableNumbers.size()];
        evaluate(lookupData); // lookupData is dummy here - evaluate just to create the variables
        context = realContext;
      }
      linenr = 1;
      minWriteIdx = variableData == null ? 0 : variableData.length;

      expressionList = _parseFile(file);

      // determine the build-in variable indices
      String[] varNames = getBuildInVariableNames();
      nBuildInVars = varNames.length;
      buildInVariableIdx = new int[nBuildInVars];
      for (int vi = 0; vi < varNames.length; vi++) {
        buildInVariableIdx[vi] = getVariableIdx(varNames[vi], false);
      }

      float[] readOnlyData = variableData;
      variableData = new float[variableNumbers.size()];
      for (int i = 0; i < minWriteIdx; i++) {
        variableData[i] = readOnlyData[i];
      }
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("ParseException " + file + " at line " + linenr + ": " + e.getMessage());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    if (expressionList.size() == 0) {
      throw new IllegalArgumentException(file.getAbsolutePath()
        + " does not contain expressions for context " + context + " (old version?)");
    }
  }

  private List<BExpression> _parseFile(File file) throws Exception {
    _br = new BufferedReader(new FileReader(file));
    _readerDone = false;
    List<BExpression> result = new ArrayList<BExpression>();
    for (; ; ) {
      BExpression exp = BExpression.parse(this, 0);
      if (exp == null) break;
      result.add(exp);
    }
    _br.close();
    _br = null;
    return result;
  }

  public void setVariableValue(String name, float value, boolean create) {
    Integer num = variableNumbers.get(name);
    if (num != null) {
      variableData[num.intValue()] = value;
    } else if (create) {
      num = getVariableIdx(name, create);
      float[] readOnlyData = variableData;
      int minWriteIdx = readOnlyData.length;
      variableData = new float[variableNumbers.size()];
      for (int i = 0; i < minWriteIdx; i++) {
        variableData[i] = readOnlyData[i];
      }
      variableData[num.intValue()] = value;
    }
  }

  public float getVariableValue(String name, float defaultValue) {
    Integer num = variableNumbers.get(name);
    return num == null ? defaultValue : getVariableValue(num.intValue());
  }

  float getVariableValue(int variableIdx) {
    return variableData[variableIdx];
  }

  int getVariableIdx(String name, boolean create) {
    Integer num = variableNumbers.get(name);
    if (num == null) {
      if (create) {
        num = Integer.valueOf(variableNumbers.size());
        variableNumbers.put(name, num);
      } else {
        return -1;
      }
    }
    return num.intValue();
  }

  int getMinWriteIdx() {
    return minWriteIdx;
  }

  float getLookupMatch(int nameIdx, int[] valueIdxArray) {
    for (int i = 0; i < valueIdxArray.length; i++) {
      if (lookupData[nameIdx] == valueIdxArray[i]) {
        return 1.0f;
      }
    }
    return 0.0f;
  }

  public int getLookupNameIdx(String name) {
    Integer num = lookupNumbers.get(name);
    return num == null ? -1 : num.intValue();
  }

  public final void markLookupIdxUsed(int idx) {
    lookupIdxUsed[idx] = true;
  }

  public final boolean isLookupIdxUsed(int idx) {
    return idx < lookupIdxUsed.length && lookupIdxUsed[idx];
  }

  public final void setAllTagsUsed() {
    for (int i = 0; i < lookupIdxUsed.length; i++) {
      lookupIdxUsed[i] = true;
    }
  }

  int getLookupValueIdx(int nameIdx, String value) {
    BExpressionLookupValue[] values = lookupValues.get(nameIdx);
    for (int i = 0; i < values.length; i++) {
      if (values[i].equals(value)) return i;
    }
    return -1;
  }


  String parseToken() throws Exception {
    for (; ; ) {
      String token = _parseToken();
      if (token == null) return null;
      if (token.startsWith(CONTEXT_TAG)) {
        _inOurContext = token.substring(CONTEXT_TAG.length()).equals(context);
      } else if (token.startsWith(MODEL_TAG)) {
        _modelClass = token.substring(MODEL_TAG.length()).trim();
      } else if (_inOurContext) {
        return token;
      }
    }
  }


  private String _parseToken() throws Exception {
    StringBuilder sb = new StringBuilder(32);
    boolean inComment = false;
    for (; ; ) {
      int ic = _readerDone ? -1 : _br.read();
      if (ic < 0) {
        if (sb.length() == 0) return null;
        _readerDone = true;
        return sb.toString();
      }
      char c = (char) ic;
      if (c == '\n') linenr++;

      if (inComment) {
        if (c == '\r' || c == '\n') inComment = false;
        continue;
      }
      if (Character.isWhitespace(c)) {
        if (sb.length() > 0) return sb.toString();
        else continue;
      }
      if (c == '#' && sb.length() == 0) inComment = true;
      else sb.append(c);
    }
  }

  float assign(int variableIdx, float value) {
    variableData[variableIdx] = value;
    return value;
  }

}
