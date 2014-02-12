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
	
	@Test
	public void testDoubleParseBinarySuffixParseZero() {
		assertEquals(0.0, StringFormat.parseBinarySuffixedDouble("0"), 0.0);
		assertEquals(0.0, StringFormat.parseBinarySuffixedDouble("0k"), 0.0);
		assertEquals(0.0, StringFormat.parseBinarySuffixedDouble("0m"), 0.0);
		assertEquals(0.0, StringFormat.parseBinarySuffixedDouble("0g"), 0.0);
		assertEquals(0.0, StringFormat.parseBinarySuffixedDouble("0t"), 0.0);
		assertEquals(0.0, StringFormat.parseBinarySuffixedDouble("0p"), 0.0);
		assertEquals(0.0, StringFormat.parseBinarySuffixedDouble("0e"), 0.0);
	}

	@Test
	public void testDoubleParseBinarySuffixParseOne() {
		assertEquals(1024.0, StringFormat.parseBinarySuffixedDouble("1K"), 0.0);
		assertEquals(1048576.0, StringFormat.parseBinarySuffixedDouble("1M"), 0.0);
		assertEquals(1073741824.0, StringFormat.parseBinarySuffixedDouble("1G"), 0.0);
		assertEquals(1099511627776.0, StringFormat.parseBinarySuffixedDouble("1T"), 0.0);
		assertEquals(1125899906842624.0, StringFormat.parseBinarySuffixedDouble("1P"), 0.0);
		assertEquals(1152921504606846976.0, StringFormat.parseBinarySuffixedDouble("1E"), 0.0);
	}
	
	@Test
	public void testDoubleParseBinarySuffixParse() {
		assertEquals(541 * 1024.0, StringFormat.parseBinarySuffixedDouble("541K"), 0.0);
		assertEquals(541 * 1048576.0, StringFormat.parseBinarySuffixedDouble("541M"), 0.0);
		assertEquals(541 * 1073741824.0, StringFormat.parseBinarySuffixedDouble("541G"), 0.0);
		assertEquals(541 * 1099511627776.0, StringFormat.parseBinarySuffixedDouble("541T"), 0.0);
		assertEquals(541 * 1125899906842624.0, StringFormat.parseBinarySuffixedDouble("541P"), 0.0);
		assertEquals(541 * 1152921504606846976.0, StringFormat.parseBinarySuffixedDouble("541E"), 0.0);
	}
	
	@Test
	public void testDoubleParseBinarySuffixParseNegative() {
		assertEquals(-541 * 1024.0, StringFormat.parseBinarySuffixedDouble("-541K"), 0.0);
		assertEquals(-541 * 1048576.0, StringFormat.parseBinarySuffixedDouble("-541M"), 0.0);
		assertEquals(-541 * 1073741824.0, StringFormat.parseBinarySuffixedDouble("-541G"), 0.0);
		assertEquals(-541 * 1099511627776.0, StringFormat.parseBinarySuffixedDouble("-541T"), 0.0);
		assertEquals(-541 * 1125899906842624.0, StringFormat.parseBinarySuffixedDouble("-541P"), 0.0);
		assertEquals(-541 * 1152921504606846976.0, StringFormat.parseBinarySuffixedDouble("-541E"), 0.0);
	}
	
	@Test
	public void testDoubleParseBinarySuffixParseMixed() {
		assertEquals(0.0, StringFormat.parseBinarySuffixedDouble("0.0"), 0.0);
		assertEquals(0.0, StringFormat.parseBinarySuffixedDouble(".0"), 0.0);
		assertEquals(0.0, StringFormat.parseBinarySuffixedDouble("0."), 0.0);
		
		assertEquals(128.0, StringFormat.parseBinarySuffixedDouble("128"), 0.0);
		assertEquals(128.0, StringFormat.parseBinarySuffixedDouble("128.0"), 0.0);
		assertEquals(128.0, StringFormat.parseBinarySuffixedDouble("128."), 0.0);
		assertEquals(128.0, StringFormat.parseBinarySuffixedDouble(".125k"), 0.0);
		assertEquals(128.0, StringFormat.parseBinarySuffixedDouble("0.125K"), 0.0);		
		
		assertEquals(-541.125 * 1024.0, StringFormat.parseBinarySuffixedDouble("-541.125K"), 0.0);
		
	}
}
