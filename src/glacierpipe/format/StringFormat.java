/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package glacierpipe.format;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringFormat {

	public static String toHumanReadableDataSize(long bytes) {
		if (bytes < (1L << 0L) * 128L) {
			return String.format("%d B", bytes);
		} else if (bytes < (1L << 10L) * 128L) {
			return String.format("%.3f KB", (double)bytes / (1L << 10L));
		} else if (bytes < (1L << 20L) * 128L) {
			return String.format("%.3f MB", (double)bytes / (1L << 20L));
		} else if (bytes < (1L << 30L) * 128L) {
			return String.format("%.3f GB", (double)bytes / (1L << 30L));
		} else if (bytes < (1L << 40L) * 128L) {
			return String.format("%.3f TB", (double)bytes / (1L << 40L));
		} else if (bytes < (1L << 50L) * 128L) {
			return String.format("%.3f PB", (double)bytes / (1L << 50L));
		} else {
			return String.format("%.3f EB", (double)bytes / (1L << 60L));
		}
	}

	private static final Pattern SIZE_PATTERN = Pattern.compile("((?:-?[1-9]\\d*)|0)\\s*([kmgtpe])?", Pattern.CASE_INSENSITIVE);
	private static final Map<String, Long> BINAR_SUFFIX_FACTOR_MAP;

	static {
		Map<String, Long> binarySuffixFactorMap = new HashMap<String, Long>();
		binarySuffixFactorMap.put("k", 1L << 10);
		binarySuffixFactorMap.put("m", 1L << 20);
		binarySuffixFactorMap.put("g", 1L << 30);
		binarySuffixFactorMap.put("t", 1L << 40);
		binarySuffixFactorMap.put("p", 1L << 50);
		binarySuffixFactorMap.put("e", 1L << 60);
		BINAR_SUFFIX_FACTOR_MAP = Collections.unmodifiableMap(binarySuffixFactorMap);
	}

	public static long parseBinarySuffixedLong(String s) {
		Matcher matcher = SIZE_PATTERN.matcher(s);
		if (matcher.matches()) {
			Long value = Long.parseLong(matcher.group(1));
			String suffix = matcher.group(2);
			if (suffix != null) {
				Long factor = BINAR_SUFFIX_FACTOR_MAP.get(suffix.toLowerCase());
				if (factor != null) {
					// TODO: detect overflow
					value *= factor;
				} else {
					throw new NumberFormatException("Unrecognized suffix: '" + suffix + "'");
				}
			}
			
			return value;
		} else {
			throw new NumberFormatException("Unable to parse binary-suffixed long '" + s + "'");
		}
	}

}
