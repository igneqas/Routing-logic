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
  private int[] lookupData = new int[0];
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
    float res;
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
      if ("way".equals(context)) addLookupValue("reversedirection", "yes");
      else if ("node".equals(context)) addLookupValue("nodeaccessgranted", "yes");
    }
    if ("reversedirection".equals(name)) return; // this is hardcoded
    if ("nodeaccessgranted".equals(name)) return; // this is hardcoded
    BExpressionLookupValue newValue = addLookupValue(name, value);

    // add aliases
    while (newValue != null && tk.hasMoreTokens()) newValue.addAlias(tk.nextToken());
  }

  public void finishMetaParsing() {
    if (parsedLines == 0 && !"global".equals(context)) {
      throw new IllegalArgumentException("lookup table does not contain data for context " + context + " (old version?)");
    }

    lookupIdxUsed = new boolean[lookupValues.size()];
  }

  public final void evaluate(int[] lookupData2) {
    lookupData = lookupData2;
    evaluate();
  }

  private void evaluate() {
    for (BExpression bExpression : expressionList) {
      bExpression.evaluate(this);
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
   * add a new lookup-value for the given name to the given lookupData array.
   * If no array is given (null value passed), the value is added to
   * the context-binded array. In that case, unknown names and values are
   * created dynamically.
   *
   * @return a newly created value element, if any, to optionally add aliases
   */
  public BExpressionLookupValue addLookupValue(String name, String value) {
    BExpressionLookupValue newValue = null;
    Integer num = lookupNumbers.get(name);
    if (num == null) {

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
    for (; i < values.length; i++) {
      BExpressionLookupValue v = values[i];
      if (v.matches(value)) break;
    }
    if (i == values.length) {

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
    lookupData[inum] = i;

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
