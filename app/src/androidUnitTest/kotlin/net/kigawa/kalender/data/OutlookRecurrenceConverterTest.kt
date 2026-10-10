@file:OptIn(kotlin.time.ExperimentalTime::class)

package net.kigawa.kalender.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import net.kigawa.kalender.model.Frequency
import net.kigawa.kalender.model.RecurrenceRule
import net.kigawa.kalender.util.formatIsoDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutlookRecurrenceConverterTest {

    private val converter = OutlookRecurrenceConverter

    /** Graph API の range 日付は "yyyy-MM-dd" 形式が必須 */
    private val formatDate: (Long) -> String = { ms -> formatIsoDate(ms) }

    private val startMs = kotlinx.datetime.Instant.parse("2026-10-09T00:00:00Z").toEpochMilliseconds()

    private val utcZone = kotlinx.datetime.TimeZone.UTC

    /**
     * ruleToGraph は `{pattern, range}` を直接返すため、
     * "recurrence" キーで包まずにそのまま検証する。
     */
    private fun patternOf(graph: JsonObject): JsonObject = graph["pattern"]!!.jsonObject

    private fun rangeOf(graph: JsonObject): JsonObject = graph["range"]!!.jsonObject

    @Test
    fun when_rruleToGraph_given_daily_then_patternIsDailyAndRangeNoEnd() {
        val result = converter.rruleToGraph("FREQ=DAILY", startMs, formatDate)!!

        val pattern = patternOf(result)!!.jsonObject
        assertEquals("daily", pattern["type"]!!.toString().trim('"'))
        assertEquals("1", pattern["interval"]!!.toString())

        val range = rangeOf(result)!!.jsonObject
        assertEquals("noEnd", range["type"]!!.toString().trim('"'))
        assertEquals("2026-10-09", range["startDate"]!!.toString().trim('"'))
    }

    @Test
    fun when_rruleToGraph_given_dailyIntervalThree_then_intervalIsThree() {
        val result = converter.rruleToGraph("FREQ=DAILY;INTERVAL=3", startMs, formatDate)!!
        val pattern = patternOf(result)!!.jsonObject
        assertEquals("3", pattern["interval"]!!.toString())
    }

    @Test
    fun when_rruleToGraph_given_weekly_then_daysOfWeekAreGraphNames() {
        // 月・水・金
        val result = converter.rruleToGraph("FREQ=WEEKLY;BYDAY=MO,WE,FR", startMs, formatDate)!!
        val pattern = patternOf(result)!!.jsonObject
        assertEquals("weekly", pattern["type"]!!.toString().trim('"'))

        val days = pattern["daysOfWeek"]!!.toString()
        assertEquals("[\"monday\",\"wednesday\",\"friday\"]", days)
    }

    @Test
    fun when_rruleToGraph_given_weeklySunday_then_mapsToSunday() {
        val result = converter.rruleToGraph("FREQ=WEEKLY;BYDAY=SU", startMs, formatDate)!!
        val pattern = patternOf(result)!!.jsonObject
        assertEquals("[\"sunday\"]", pattern["daysOfWeek"]!!.toString())
    }

    @Test
    fun when_rruleToGraph_given_absoluteMonthly_then_patternIsAbsoluteMonthly() {
        val result = converter.rruleToGraph("FREQ=MONTHLY;BYMONTHDAY=15", startMs, formatDate)!!
        val pattern = patternOf(result)!!.jsonObject
        assertEquals("absoluteMonthly", pattern["type"]!!.toString().trim('"'))
        assertEquals("15", pattern["dayOfMonth"]!!.toString())
    }

    @Test
    fun when_rruleToGraph_given_absoluteYearly_then_patternIsAbsoluteYearly() {
        val result = converter.rruleToGraph("FREQ=YEARLY;BYMONTHDAY=1;BYMONTH=4", startMs, formatDate)!!
        val pattern = patternOf(result)!!.jsonObject
        assertEquals("absoluteYearly", pattern["type"]!!.toString().trim('"'))
        assertEquals("1", pattern["dayOfMonth"]!!.toString())
        assertEquals("4", pattern["month"]!!.toString())
    }

    @Test
    fun when_rruleToGraph_given_count_then_rangeIsNumbered() {
        val result = converter.rruleToGraph("FREQ=DAILY;COUNT=10", startMs, formatDate)!!
        val range = rangeOf(result)!!.jsonObject
        assertEquals("numbered", range["type"]!!.toString().trim('"'))
        assertEquals("10", range["numberOfOccurrences"]!!.toString())
    }

    @Test
    fun when_rruleToGraph_given_until_then_rangeIsEndDate() {
        // UNTIL はRRULE上UTC。フォーマッタは日付のみ返すため endDate と比較
        val untilMs = kotlinx.datetime.Instant.parse("2026-12-31T23:59:59Z").toEpochMilliseconds()
        val rule = RecurrenceRule(
            frequency = Frequency.DAILY,
            until = untilMs,
            neverEnds = false,
        )
        val result = converter.ruleToGraph(rule, startMs, formatDate)!!
        val range = rangeOf(result)!!.jsonObject
        assertEquals("endDate", range["type"]!!.toString().trim('"'))
        assertEquals("2026-12-31", range["endDate"]!!.toString().trim('"'))
    }

    @Test
    fun when_rruleToGraph_given_none_then_returnsNull() {
        assertNull(converter.rruleToGraph("FREQ=NONE", startMs, formatDate))
    }

    // --- 逆変換 (Graph → RRULE) ---

    @Test
    fun when_graphToRrule_given_dailyNoEnd_then_returnsDaily() {
        val recurrence = parse(
            """
            {
              "pattern": { "type": "daily", "interval": 1 },
              "range": { "type": "noEnd", "startDate": "2026-10-09" }
            }
            """
        )
        assertEquals("FREQ=DAILY", converter.graphToRrule(recurrence))
    }

    @Test
    fun when_graphToRrule_given_weeklyMultipleDays_then_returnsByDayInRfc5545() {
        val recurrence = parse(
            """
            {
              "pattern": {
                "type": "weekly",
                "interval": 1,
                "daysOfWeek": ["monday", "wednesday", "friday"]
              },
              "range": { "type": "noEnd", "startDate": "2026-10-09" }
            }
            """
        )
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE,FR", converter.graphToRrule(recurrence))
    }

    @Test
    fun when_graphToRrule_given_weeklySunday_then_mapsToSU() {
        val recurrence = parse(
            """
            {
              "pattern": { "type": "weekly", "interval": 2, "daysOfWeek": ["sunday"] },
              "range": { "type": "noEnd", "startDate": "2026-10-09" }
            }
            """
        )
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=SU", converter.graphToRrule(recurrence))
    }

    @Test
    fun when_graphToRrule_given_absoluteMonthly_then_returnsMonthly() {
        val recurrence = parse(
            """
            {
              "pattern": { "type": "absoluteMonthly", "interval": 1, "dayOfMonth": 15 },
              "range": { "type": "noEnd", "startDate": "2026-10-09" }
            }
            """
        )
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=15", converter.graphToRrule(recurrence))
    }

    @Test
    fun when_graphToRrule_given_absoluteYearly_then_returnsYearly() {
        val recurrence = parse(
            """
            {
              "pattern": {
                "type": "absoluteYearly",
                "interval": 1,
                "dayOfMonth": 1,
                "month": 4
              },
              "range": { "type": "noEnd", "startDate": "2026-10-09" }
            }
            """
        )
        assertEquals(
            "FREQ=YEARLY;BYMONTHDAY=1;BYMONTH=4",
            converter.graphToRrule(recurrence),
        )
    }

    @Test
    fun when_graphToRrule_given_numbered_then_returnsCount() {
        val recurrence = parse(
            """
            {
              "pattern": { "type": "daily", "interval": 1 },
              "range": {
                "type": "numbered",
                "startDate": "2026-10-09",
                "numberOfOccurrences": 10
              }
            }
            """
        )
        assertEquals("FREQ=DAILY;COUNT=10", converter.graphToRrule(recurrence))
    }

    @Test
    fun when_graphToRrule_given_noPattern_then_returnsNull() {
        val recurrence = parse("""{ "range": { "type": "noEnd" } }""")
        assertNull(converter.graphToRrule(recurrence))
    }

    // --- ラウンドトリップ ---

    @Test
    fun when_roundtrip_given_daily_then_preservesRrule() {
        val original = "FREQ=DAILY"
        val graph = converter.rruleToGraph(original, startMs, formatDate)!!
        assertEquals(original, converter.graphToRrule(graph))
    }

    @Test
    fun when_roundtrip_given_dailyIntervalThree_then_preservesRrule() {
        val original = "FREQ=DAILY;INTERVAL=3"
        val graph = converter.rruleToGraph(original, startMs, formatDate)!!
        assertEquals(original, converter.graphToRrule(graph))
    }

    @Test
    fun when_roundtrip_given_weeklyMoWeFr_then_preservesRrule() {
        val original = "FREQ=WEEKLY;BYDAY=MO,WE,FR"
        val graph = converter.rruleToGraph(original, startMs, formatDate)!!
        assertEquals(original, converter.graphToRrule(graph))
    }

    @Test
    fun when_roundtrip_given_weeklySunday_then_preservesRrule() {
        val original = "FREQ=WEEKLY;BYDAY=SU"
        val graph = converter.rruleToGraph(original, startMs, formatDate)!!
        assertEquals(original, converter.graphToRrule(graph))
    }

    @Test
    fun when_roundtrip_given_absoluteMonthly_then_preservesRrule() {
        val original = "FREQ=MONTHLY;BYMONTHDAY=15"
        val graph = converter.rruleToGraph(original, startMs, formatDate)!!
        assertEquals(original, converter.graphToRrule(graph))
    }

    @Test
    fun when_roundtrip_given_absoluteYearly_then_preservesRrule() {
        val original = "FREQ=YEARLY;BYMONTHDAY=1;BYMONTH=4"
        val graph = converter.rruleToGraph(original, startMs, formatDate)!!
        assertEquals(original, converter.graphToRrule(graph))
    }

    @Test
    fun when_roundtrip_given_dailyCountTen_then_preservesRrule() {
        val original = "FREQ=DAILY;COUNT=10"
        val graph = converter.rruleToGraph(original, startMs, formatDate)!!
        assertEquals(original, converter.graphToRrule(graph))
    }

    @Test
    fun when_roundtrip_given_relativeMonthly_then_approximatesToMonthly() {
        // relative系は RecurrenceRule で index を保持できないため近似される
        val recurrence = parse(
            """
            {
              "pattern": {
                "type": "relativeMonthly",
                "interval": 1,
                "daysOfWeek": ["monday"],
                "index": "second"
              },
              "range": { "type": "noEnd", "startDate": "2026-10-09" }
            }
            """
        )
        val rrule = converter.graphToRrule(recurrence)!!
        // 曜日情報は保持される（index は失われる）
        assertEquals("FREQ=MONTHLY;BYDAY=MO", rrule)
    }

    private fun parse(text: String): JsonObject =
        Json.parseToJsonElement(text.trimIndent()).jsonObject
}
