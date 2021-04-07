package tech.kinori.eclipse.p2mvn.maven;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenTemplate {

	private static final String regex = "\\$\\{([^}]+)\\}";
	private static final Pattern pattern = Pattern.compile(regex);

	public String format(
			String format,
			Map<String, String> values) {
		Matcher m = pattern.matcher(format);
		String result = format;
		while (m.find()) {
			String key = m.group(1);
			result = result.replaceFirst(regex, values.get(key));
		}
		return result;
	}
}
