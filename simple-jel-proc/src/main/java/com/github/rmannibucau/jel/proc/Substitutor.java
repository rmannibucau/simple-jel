package com.github.rmannibucau.jel.proc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

// forked from commons-text but no need to bring a dep only for < 200 LOC
public class Substitutor {
    private static final char ESCAPE = '\\';
    private static final char[] PREFIX = "${" .toCharArray();
    private static final char[] SUFFIX = "}" .toCharArray();
    private static final char[] VALUE_DELIMITER = ":-" .toCharArray();

    private final Function<String, String> placeholders;

    public Substitutor(final Function<String, String> placeholders) {
        this.placeholders = placeholders;
    }

    public String replace(final String source) {
        if (source == null) {
            return null;
        }
        final StringBuilder builder = new StringBuilder(source);
        if (substitute(builder, 0, source.length(), null) <= 0) {
            return source;
        }
        return replace(builder.toString());
    }

    private int substitute(final StringBuilder buf, final int offset, final int length, List<String> priorVariables) {
        final boolean top = priorVariables == null;
        boolean altered = false;
        int lengthChange = 0;
        char[] chars = buf.toString().toCharArray();
        int bufEnd = offset + length;
        int pos = offset;
        while (pos < bufEnd) {
            final int startMatchLen = isMatch(PREFIX, chars, pos, bufEnd);
            if (startMatchLen == 0) {
                pos++;
            } else {
                if (pos > offset && chars[pos - 1] == ESCAPE) {
                    buf.deleteCharAt(pos - 1);
                    chars = buf.toString().toCharArray();
                    lengthChange--;
                    altered = true;
                    bufEnd--;
                } else {
                    final int startPos = pos;
                    pos += startMatchLen;
                    int endMatchLen;
                    while (pos < bufEnd) {
                        endMatchLen = isMatch(SUFFIX, chars, pos, bufEnd);
                        if (endMatchLen == 0) {
                            pos++;
                        } else {
                            String varNameExpr = new String(chars, startPos
                                    + startMatchLen, pos - startPos
                                    - startMatchLen);
                            pos += endMatchLen;
                            final int endPos = pos;

                            String varName = varNameExpr;
                            String varDefaultValue = null;

                            final char[] varNameExprChars = varNameExpr.toCharArray();
                            for (int i = 0; i < varNameExprChars.length; i++) {
                                if (isMatch(PREFIX, varNameExprChars, i, varNameExprChars.length) != 0) {
                                    break;
                                }
                                final int match = isMatch(VALUE_DELIMITER, varNameExprChars, i, varNameExprChars.length);
                                if (match != 0) {
                                    varName = varNameExpr.substring(0, i);
                                    varDefaultValue = varNameExpr.substring(i + match);
                                    break;
                                }
                            }

                            if (priorVariables == null) {
                                priorVariables = new ArrayList<>();
                                priorVariables.add(new String(chars, offset, length));
                            }

                            checkCyclicSubstitution(varName, priorVariables);
                            priorVariables.add(varName);

                            final String varValue = getOrDefault(varName, varDefaultValue);
                            if (varValue != null) {
                                final int varLen = varValue.length();
                                buf.replace(startPos, endPos, varValue);
                                altered = true;
                                int change = substitute(buf, startPos, varLen, priorVariables);
                                change = change + varLen - (endPos - startPos);
                                pos += change;
                                bufEnd += change;
                                lengthChange += change;
                                chars = buf.toString().toCharArray();
                            }

                            priorVariables.remove(priorVariables.size() - 1);
                            break;
                        }
                    }
                }
            }
        }
        if (top) {
            return altered ? 1 : 0;
        }
        return lengthChange;
    }

    private String getOrDefault(final String varName, final String varDefaultValue) {
        return ofNullable(placeholders.apply(varName)).orElse(varDefaultValue);
    }

    private int isMatch(final char[] chars, final char[] buffer, int pos,
                        final int bufferEnd) {
        final int len = chars.length;
        if (pos + len > bufferEnd) {
            return 0;
        }
        for (int i = 0; i < chars.length; i++, pos++) {
            if (chars[i] != buffer[pos]) {
                return 0;
            }
        }
        return len;
    }

    private void checkCyclicSubstitution(final String varName, final List<String> priorVariables) {
        if (!priorVariables.contains(varName)) {
            return;
        }
        final StringBuilder buf = new StringBuilder(256);
        buf.append("Infinite loop in property interpolation of ");
        buf.append(priorVariables.remove(0));
        buf.append(": ");
        if (!priorVariables.isEmpty()) {
            buf.append(String.join("->", priorVariables));
        }
        throw new IllegalStateException(buf.toString());
    }
}
