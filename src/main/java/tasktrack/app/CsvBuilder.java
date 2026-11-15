package tasktrack.app;

import java.nio.charset.StandardCharsets;

/**
 * CSV（RFC 4180 準拠、UTF-8 BOM 付き、CRLF、全値をダブルクォートで囲む）を組み立てる。
 * <ul>
 *   <li>null は空欄（クォートなし）、空文字は {@code ""} として出力する。</li>
 *   <li>文字列値は、表計算ソフトで数式として解釈されうる場合に先頭へ {@code '} を付けて無害化する。</li>
 * </ul>
 */
final class CsvBuilder {
    private static final String CRLF = "\r\n";
    /** 先頭にあると数式・DDE として解釈されうる文字（全角の = + - @ を含む）。 */
    private static final String FORMULA_TRIGGERS = "=+-@\t\r\n＝＋－＠";

    private final StringBuilder sb = new StringBuilder("﻿");
    private final int columns;

    CsvBuilder(String... header) {
        columns = header.length;
        row((Object[]) header);
    }

    /** 1行追加する。値は String / Number / Boolean / null。 */
    CsvBuilder row(Object... values) {
        if (values.length != columns) {
            throw new IllegalArgumentException("列数が一致しません: " + values.length + " != " + columns);
        }
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            Object v = values[i];
            if (v == null) {
                continue;
            }
            String s = v instanceof String str ? neutralize(str) : v.toString();
            sb.append('"').append(s.replace("\"", "\"\"")).append('"');
        }
        sb.append(CRLF);
        return this;
    }

    /** 数式として解釈されうる値の先頭に {@code '} を付ける。それ以外は変更しない。 */
    static String neutralize(String value) {
        if (!value.isEmpty() && FORMULA_TRIGGERS.indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }

    byte[] toBytes() {
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
