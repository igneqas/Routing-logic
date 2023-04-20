package com.routerbackend.routinglogic.utils;

/**
 * Some methods for String handling
 */
public class StringUtils {
  private static char[] jsnChr = new char[]{'\'', '"', '\\', '/'};
  private static String[] jsnEsc = new String[]{"\\'", "\\\"", "\\\\", "\\/"};

  /**
   * Escape a literal to put into a json document
   */
  public static String escapeJson(String s) {
    return escape(s, jsnChr, jsnEsc);
  }

  /**
   * Escape a literal to put into a xml document
   */

  private static String escape(String s, char[] chr, String[] esc) {
    StringBuilder sb = null;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      int j = 0;
      while (j < chr.length) {
        if (c == chr[j]) {
          if (sb == null) {
            sb = new StringBuilder(s.substring(0, i));
          }
          sb.append(esc[j]);
          break;
        }
        j++;
      }
      if (sb != null && j == chr.length) {
        sb.append(c);
      }
    }
    return sb == null ? s : sb.toString();
  }
}
