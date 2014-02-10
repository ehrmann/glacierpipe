package glacierpipe.format;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StringFormatTest {

	@Test
	public void testParseBinarySuffixParseZero() {
		assertEquals(0, StringFormat.parseBinarySuffixedLong("0"));
		assertEquals(0, StringFormat.parseBinarySuffixedLong("0k"));
		assertEquals(0, StringFormat.parseBinarySuffixedLong("0m"));
		assertEquals(0, StringFormat.parseBinarySuffixedLong("0g"));
		assertEquals(0, StringFormat.parseBinarySuffixedLong("0t"));
		assertEquals(0, StringFormat.parseBinarySuffixedLong("0p"));
		assertEquals(0, StringFormat.parseBinarySuffixedLong("0e"));
	}

	@Test
	public void testParseBinarySuffixParseOne() {
		assertEquals(1024L, StringFormat.parseBinarySuffixedLong("1K"));
		assertEquals(1048576L, StringFormat.parseBinarySuffixedLong("1M"));
		assertEquals(1073741824L, StringFormat.parseBinarySuffixedLong("1G"));
		assertEquals(1099511627776L, StringFormat.parseBinarySuffixedLong("1T"));
		assertEquals(1125899906842624L, StringFormat.parseBinarySuffixedLong("1P"));
		assertEquals(1152921504606846976L, StringFormat.parseBinarySuffixedLong("1E"));
	}
	
	@Test
	public void testParseBinarySuffixParse() {
		assertEquals(541 * 1024L, StringFormat.parseBinarySuffixedLong("541K"));
		assertEquals(541 * 1048576L, StringFormat.parseBinarySuffixedLong("541M"));
		assertEquals(541 * 1073741824L, StringFormat.parseBinarySuffixedLong("541G"));
		assertEquals(541 * 1099511627776L, StringFormat.parseBinarySuffixedLong("541T"));
		assertEquals(541 * 1125899906842624L, StringFormat.parseBinarySuffixedLong("541P"));
		assertEquals(541 * 1152921504606846976L, StringFormat.parseBinarySuffixedLong("541E"));
	}
	
	@Test
	public void testParseBinarySuffixParseNegative() {
		assertEquals(-541 * 1024L, StringFormat.parseBinarySuffixedLong("-541K"));
		assertEquals(-541 * 1048576L, StringFormat.parseBinarySuffixedLong("-541M"));
		assertEquals(-541 * 1073741824L, StringFormat.parseBinarySuffixedLong("-541G"));
		assertEquals(-541 * 1099511627776L, StringFormat.parseBinarySuffixedLong("-541T"));
		assertEquals(-541 * 1125899906842624L, StringFormat.parseBinarySuffixedLong("-541P"));
		assertEquals(-541 * 1152921504606846976L, StringFormat.parseBinarySuffixedLong("-541E"));
	}
	
	
}
