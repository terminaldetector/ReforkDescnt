package com.terminaldetector.drmd.diag;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the diagnostics report — pure text assembly, zero Minecraft imports, the
 * {@code SkirtGeometry}/{@code PortalTransform} idiom: the caller gathers the facts, this file only
 * lays them out.
 *
 * <p><b>Why a report at all.</b> Several of this project's worst days went the same way: something
 * looked wrong in game, and the only way to find out why was to guess, change something, rebuild, and
 * look again. The access-widener crash cost an evening that way; the mirror render has five failure
 * modes that look identical from a chair; the horizon's rebuild cost is invisible until it stutters.
 * None of those need guessing — the game knows the answer at the moment it goes wrong, and the only
 * thing missing was somewhere to write it down.
 *
 * <p>So the rule for what belongs here: <b>facts the mod knows and a log line would not</b> — versions
 * and which optional mods actually loaded, the state of the systems that have failure modes, counters
 * and timings, and anything the mod itself noticed going wrong. Not prose, not advice: whoever reads
 * it can reason, and a number they can check beats a sentence they have to trust.
 *
 * <p>Rows align within a section rather than across the whole report, so one long value in one place
 * does not push every other section's column out and make the rest harder to scan.
 */
public final class DiagReport {
	/** Beyond this a value is truncated — a report nobody can read is a report nobody sends. */
	private static final int MAX_VALUE = 400;

	private final List<Section> sections = new ArrayList<>();
	private Section current;

	private record Row(String key, String value) {}

	private static final class Section {
		final String title;
		final List<Row> rows = new ArrayList<>();
		final List<String> notes = new ArrayList<>();

		Section(String title) {
			this.title = title;
		}
	}

	/** Start a section. Rows and notes after this belong to it until the next one. */
	public DiagReport section(String title) {
		current = new Section(title == null ? "(unnamed)" : title);
		sections.add(current);
		return this;
	}

	/**
	 * One fact. A null value is written as {@code null} rather than dropped: "this was absent" is
	 * itself a finding, and silently omitting the row would hide it.
	 */
	public DiagReport row(String key, Object value) {
		ensureSection();
		current.rows.add(new Row(key == null ? "?" : key, clean(value)));
		return this;
	}

	/** A free line under the current section's rows — for something that is not a key and a value. */
	public DiagReport note(String text) {
		ensureSection();
		current.notes.add(clean(text));
		return this;
	}

	/** Rows only when {@code condition} holds, so a caller can chain without an if. */
	public DiagReport rowIf(boolean condition, String key, Object value) {
		return condition ? row(key, value) : this;
	}

	public String render() {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < sections.size(); i++) {
			Section section = sections.get(i);
			if (i > 0) out.append('\n');
			out.append("== ").append(section.title).append('\n');
			int width = 0;
			for (Row row : section.rows) width = Math.max(width, row.key().length());
			for (Row row : section.rows) {
				out.append("  ").append(row.key());
				for (int pad = row.key().length(); pad < width; pad++) out.append(' ');
				out.append("  ").append(row.value()).append('\n');
			}
			for (String note : section.notes) out.append("  ").append(note).append('\n');
		}
		return out.toString();
	}

	private void ensureSection() {
		if (current == null) section("(no section)");
	}

	/**
	 * One line, bounded length. Newlines and tabs are folded rather than escaped: a value that spans
	 * lines would break the column layout that makes the rest of the report scannable, and no value
	 * here is worth reading across lines.
	 */
	private static String clean(Object value) {
		if (value == null) return "null";
		String text = String.valueOf(value).replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
		if (text.length() <= MAX_VALUE) return text;
		return text.substring(0, MAX_VALUE) + "… (+" + (text.length() - MAX_VALUE) + " chars)";
	}
}
