package tasktrack.app;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class CsvBuilderTest {

    private static String body(CsvBuilder csv) {
        byte[] bytes = csv.toBytes();
        return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
    }

    @Test
    void startsWithUtf8BomAndUsesCrlf() {
        byte[] bytes = new CsvBuilder("a", "b").row("1", "2").toBytes();
        assertArrayEquals(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOf(bytes, 3));
        assertEquals("\"a\",\"b\"\r\n\"1\",\"2\"\r\n", body(new CsvBuilder("a", "b").row("1", "2")));
    }

    @Test
    void quotesValuesAndDoublesEmbeddedQuotes() {
        assertEquals("\"v\"\r\n\"say \"\"hi\"\", ok\r\nnext line\"\r\n",
                body(new CsvBuilder("v").row("say \"hi\", ok\r\nnext line")));
    }

    @Test
    void nullIsBlankAndEmptyStringIsQuoted() {
        assertEquals("\"a\",\"b\",\"c\"\r\n,\"\",\"x\"\r\n", body(new CsvBuilder("a", "b", "c").row(null, "", "x")));
    }

    @Test
    void booleansAndNumbersAreWrittenPlainly() {
        assertEquals("\"a\",\"b\",\"c\"\r\n\"true\",\"false\",\"3600\"\r\n",
                body(new CsvBuilder("a", "b", "c").row(true, false, 3600L)));
    }

    @Test
    void neutralizesValuesThatLookLikeFormulas() {
        assertEquals("'=SUM(A1:A2)", CsvBuilder.neutralize("=SUM(A1:A2)"));
        assertEquals("'+81-3-0000-0000", CsvBuilder.neutralize("+81-3-0000-0000"));
        assertEquals("'-5", CsvBuilder.neutralize("-5"));
        assertEquals("'@cmd", CsvBuilder.neutralize("@cmd"));
        assertEquals("'\t=1+1", CsvBuilder.neutralize("\t=1+1"));
        assertEquals("'＝HYPERLINK(\"x\")", CsvBuilder.neutralize("＝HYPERLINK(\"x\")"));
        // 通常の値は変更しない
        assertEquals("顧客A = 重要", CsvBuilder.neutralize("顧客A = 重要"));
        assertEquals("", CsvBuilder.neutralize(""));
        assertEquals("\"=cmd|' /C calc'!A0\"\r\n".replace("\"=", "\"'="),
                body(new CsvBuilder("=cmd|' /C calc'!A0")).replace("\r\n", "\r\n"));
    }

    @Test
    void rejectsRowWithWrongColumnCount() {
        assertThrows(IllegalArgumentException.class, () -> new CsvBuilder("a", "b").row("only one"));
    }
}
