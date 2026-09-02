package com.terminaldetector.drmd;

import com.terminaldetector.drmd.diag.DiagProblems;
import com.terminaldetector.drmd.diag.DiagReport;
import com.terminaldetector.drmd.diag.DiagTrace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the diagnostics report and problem log directly — both are pure, which is the point of
 * them being separate from the code that gathers the facts.
 *
 * <p>Worth testing rather than eyeballing: this is the file someone sends when something is already
 * wrong, so a report that loses a row, mangles a value, or drowns in repeats fails exactly when it is
 * needed and cannot be re-taken.
 */
class DiagReportTest {

	@BeforeEach
	void reset() {
		DiagProblems.clear();
		DiagTrace.clear();
	}

	@Test
	@DisplayName("keys line up inside a section")
	void keysAlignWithinASection() {
		String text = new DiagReport()
				.section("Env")
				.row("a", "1")
				.row("longer", "2")
				.render();

		List<String> lines = text.lines().toList();
		assertEquals("== Env", lines.get(0));
		// Both values start at the same column, which is what makes a report scannable.
		assertEquals(lines.get(1).indexOf('1'), lines.get(2).indexOf('2'),
				"values not aligned:\n" + text);
	}

	@Test
	@DisplayName("alignment is per section, so one long key does not spread the whole report")
	void alignmentDoesNotLeakBetweenSections() {
		String text = new DiagReport()
				.section("Wide")
				.row("a_very_long_key_indeed", "1")
				.section("Narrow")
				.row("b", "2")
				.render();

		String narrowRow = text.lines().filter(l -> l.contains("b ") || l.trim().startsWith("b")).findFirst().orElse("");
		assertEquals("  b  2", narrowRow, "narrow section inherited the wide section's column");
	}

	@Test
	@DisplayName("an absent value is written, not dropped — 'it was missing' is a finding")
	void nullValueIsWritten() {
		String text = new DiagReport().section("S").row("seed", null).render();
		assertTrue(text.contains("seed  null"), text);
	}

	@Test
	@DisplayName("a value spanning lines is folded, so it cannot break the columns")
	void multiLineValuesAreFolded() {
		String text = new DiagReport().section("S").row("k", "one\ntwo\rthree\tfour").render();
		assertEquals(2, text.lines().count(), "value broke the layout:\n" + text);
		assertTrue(text.contains("one two three four"), text);
	}

	@Test
	@DisplayName("a runaway value is truncated and says how much was cut")
	void longValuesAreTruncated() {
		String huge = "x".repeat(450);
		String text = new DiagReport().section("S").row("k", huge).render();
		assertTrue(text.contains("… (+50 chars)"), "no truncation marker:\n" + text);
		assertTrue(text.length() < 600, "value was not actually shortened");
	}

	@Test
	@DisplayName("rows before any section still land somewhere rather than being lost")
	void rowsWithoutASectionAreKept() {
		String text = new DiagReport().row("orphan", "1").render();
		assertTrue(text.contains("orphan"), text);
	}

	@Test
	@DisplayName("an empty report is empty, not a header with nothing under it")
	void emptyReportIsEmpty() {
		assertEquals("", new DiagReport().render());
	}

	@Test
	@DisplayName("rowIf keeps a caller from having to break the chain")
	void rowIfSkipsWhenFalse() {
		String text = new DiagReport().section("S").rowIf(false, "hidden", 1).rowIf(true, "shown", 2).render();
		assertFalse(text.contains("hidden"), text);
		assertTrue(text.contains("shown"), text);
	}

	@Test
	@DisplayName("the trace keeps order, and does NOT collapse repeats the way problems do")
	void traceKeepsEveryEventInOrder() {
		DiagTrace.clear();
		DiagTrace.record("portal", "carried player through 1,2,3");
		DiagTrace.record("portal", "carried player through 1,2,3");

		List<DiagTrace.Event> events = DiagTrace.events();
		// Two travellers through the same portal are two events, and knowing there were two is the
		// point. Collapsing them would turn a record of what happened back into a summary of what is.
		assertEquals(2, events.size(), "the trace collapsed a repeat");
		assertTrue(events.get(0).millis() <= events.get(1).millis(), "trace is not in order");
	}

	@Test
	@DisplayName("the trace drops the oldest, so a report taken after a problem still holds what led to it")
	void traceIsBoundedFromTheFront() {
		DiagTrace.clear();
		for (int i = 0; i < 900; i++) DiagTrace.record("worldgen", "event " + i);
		List<DiagTrace.Event> events = DiagTrace.events();
		assertTrue(events.size() <= 600, "unbounded at " + events.size());
		assertTrue(events.get(events.size() - 1).message().contains("event 899"),
				"the newest event was dropped instead of the oldest");
	}

	@Test
	@DisplayName("counters aggregate what is too frequent to write down")
	void countersAggregate() {
		DiagTrace.clear();
		for (int i = 0; i < 5000; i++) DiagTrace.count("view.drawn");
		assertEquals(0, DiagTrace.size(), "a counted thing should not also fill the trace");
		assertEquals(5000, DiagTrace.counters().get("view.drawn"));
	}

	@Test
	@DisplayName("a repeating problem is counted, not repeated")
	void repeatedProblemsAreCounted() {
		for (int i = 0; i < 500; i++) DiagProblems.record("portal", "partner not loaded at 12,64,-40");
		assertEquals(1, DiagProblems.size(), "a per-tick failure filled the buffer");
		assertEquals(500, DiagProblems.snapshot().get(0).count());
	}

	@Test
	@DisplayName("distinct problems are kept, most recently seen first")
	void distinctProblemsAreOrdered() {
		DiagProblems.record("horizon", "rebuild took 41ms");
		DiagProblems.record("portal", "partner not loaded");
		List<DiagProblems.Entry> entries = DiagProblems.snapshot();
		assertEquals(2, entries.size());
		assertEquals("portal", entries.get(0).area(), "newest should be first");
		assertEquals("horizon", entries.get(1).area());
	}

	@Test
	@DisplayName("a repeat moves back to the front, since it is happening now")
	void repeatMovesToTheFront() {
		DiagProblems.record("horizon", "rebuild took 41ms");
		DiagProblems.record("portal", "partner not loaded");
		DiagProblems.record("horizon", "rebuild took 41ms");

		List<DiagProblems.Entry> entries = DiagProblems.snapshot();
		assertEquals("horizon", entries.get(0).area());
		assertEquals(2, entries.get(0).count());
		assertTrue(entries.get(0).lastSeenMillis() >= entries.get(0).firstSeenMillis(),
				"last seen must not predate first seen");
	}

	@Test
	@DisplayName("the buffer is bounded, and drops the oldest rather than refusing the newest")
	void bufferIsBounded() {
		for (int i = 0; i < 200; i++) DiagProblems.record("worldgen", "column " + i + " never filled");
		assertTrue(DiagProblems.size() <= 40, "unbounded at " + DiagProblems.size());
		assertTrue(DiagProblems.snapshot().get(0).message().contains("column 199"),
				"the newest problem was dropped instead of the oldest");
	}

	@Test
	@DisplayName("a problem with no message still records, rather than throwing inside a failure path")
	void nullsAreTolerated() {
		DiagProblems.record(null, null);
		assertEquals(1, DiagProblems.size());
	}
}
