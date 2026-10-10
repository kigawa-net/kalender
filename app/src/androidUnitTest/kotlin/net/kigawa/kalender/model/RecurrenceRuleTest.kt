package net.kigawa.kalender.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecurrenceRuleTest {

    @Test
    fun when_fromRRule_given_dailyWithoutInterval_then_parsesDailyEveryDay() {
        val rule = RecurrenceRule.fromRRule("FREQ=DAILY")
        assertEquals(Frequency.DAILY, rule.frequency)
        assertEquals(1, rule.interval)
    }

    @Test
    fun when_fromRRule_given_dailyWithInterval_then_parsesInterval() {
        val rule = RecurrenceRule.fromRRule("FREQ=DAILY;INTERVAL=3")
        assertEquals(Frequency.DAILY, rule.frequency)
        assertEquals(3, rule.interval)
    }

    @Test
    fun when_fromRRule_given_weeklyWithByDay_then_parsesWeekdayNumbers() {
        // BYDAY は RFC5545 の 2文字表記。1=月 .. 7=日 に正規化される
        val rule = RecurrenceRule.fromRRule("FREQ=WEEKLY;BYDAY=MO,WE,FR")
        assertEquals(Frequency.WEEKLY, rule.frequency)
        assertEquals(listOf(1, 3, 5), rule.byDay)
    }

    @Test
    fun when_fromRRule_given_sundayByDay_then_mapsToSeven() {
        val rule = RecurrenceRule.fromRRule("FREQ=WEEKLY;BYDAY=SU")
        assertEquals(listOf(7), rule.byDay)
    }

    @Test
    fun when_fromRRule_given_absoluteMonthly_then_parsesMonthDay() {
        val rule = RecurrenceRule.fromRRule("FREQ=MONTHLY;BYMONTHDAY=15")
        assertEquals(Frequency.MONTHLY, rule.frequency)
        assertEquals(listOf(15), rule.byMonthDay)
    }

    @Test
    fun when_fromRRule_given_absoluteYearly_then_parsesMonth() {
        val rule = RecurrenceRule.fromRRule("FREQ=YEARLY;BYMONTH=1;BYMONTHDAY=1")
        assertEquals(Frequency.YEARLY, rule.frequency)
        assertEquals(listOf(1), rule.byMonth)
        assertEquals(listOf(1), rule.byMonthDay)
    }

    @Test
    fun when_fromRRule_given_count_then_parsesCountAndNotNeverEnds() {
        val rule = RecurrenceRule.fromRRule("FREQ=DAILY;COUNT=10")
        assertEquals(10, rule.count)
        assertEquals(false, rule.neverEnds)
    }

    @Test
    fun when_fromRRule_given_noEndCondition_then_neverEndsIsTrue() {
        val rule = RecurrenceRule.fromRRule("FREQ=DAILY")
        assertNull(rule.count)
        assertNull(rule.until)
        assertEquals(true, rule.neverEnds)
    }

    @Test
    fun when_fromRRule_given_unknownFreq_then_fallsBackToNone() {
        val rule = RecurrenceRule.fromRRule("FREQ=SECONDLY;INTERVAL=30")
        assertEquals(Frequency.NONE, rule.frequency)
    }

    @Test
    fun when_toRRule_given_none_then_returnsFreqNone() {
        val rrule = RecurrenceRule.NONE.toRRule()
        assertEquals("FREQ=NONE", rrule)
    }

    @Test
    fun when_toRRule_given_dailyIntervalThree_then_includesInterval() {
        val rrule = RecurrenceRule.daily(interval = 3).toRRule()
        assertEquals("FREQ=DAILY;INTERVAL=3", rrule)
    }

    @Test
    fun when_toRRule_given_weeklyMultipleDays_then_serializesByDayInRfc5545() {
        // 月・水・金
        val rrule = RecurrenceRule.weekly(byDay = listOf(1, 3, 5)).toRRule()
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE,FR", rrule)
    }

    @Test
    fun when_toRRule_given_sunday_then_serializesSU() {
        val rrule = RecurrenceRule.weekly(byDay = listOf(7)).toRRule()
        assertEquals("FREQ=WEEKLY;BYDAY=SU", rrule)
    }

    @Test
    fun when_toRRule_given_absoluteMonthly_then_serializesByMonthDay() {
        val rrule = RecurrenceRule.monthly(byMonthDay = listOf(15)).toRRule()
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=15", rrule)
    }

    @Test
    fun when_toRRule_given_absoluteYearly_then_serializesMonthAndDay() {
        // yearly は byMonth のみ指定できるため、日付はコンストラクタで直接与える
        val rrule = RecurrenceRule(
            frequency = Frequency.YEARLY,
            byMonth = listOf(4),
            byMonthDay = listOf(1),
        ).toRRule()
        assertEquals("FREQ=YEARLY;BYMONTHDAY=1;BYMONTH=4", rrule)
    }

    @Test
    fun when_toRRule_given_count_then_serializesCount() {
        val rule = RecurrenceRule(frequency = Frequency.DAILY, count = 10)
        assertEquals("FREQ=DAILY;COUNT=10", rule.toRRule())
    }

    @Test
    fun when_roundtrip_given_weeklyMoWeFr_then_preservesRule() {
        val original = "FREQ=WEEKLY;BYDAY=MO,WE,FR"
        val roundTripped = RecurrenceRule.fromRRule(original).toRRule()
        assertEquals(original, roundTripped)
    }

    @Test
    fun when_roundtrip_given_dailyIntervalTwo_then_preservesRule() {
        val original = "FREQ=DAILY;INTERVAL=2"
        val roundTripped = RecurrenceRule.fromRRule(original).toRRule()
        assertEquals(original, roundTripped)
    }

    @Test
    fun when_roundtrip_given_absoluteMonthly_then_preservesRule() {
        val original = "FREQ=MONTHLY;BYMONTHDAY=15"
        val roundTripped = RecurrenceRule.fromRRule(original).toRRule()
        assertEquals(original, roundTripped)
    }

    @Test
    fun when_roundtrip_given_absoluteYearly_then_preservesRule() {
        // 出力順は byMonthDay → byMonth なので期待値もその順
        val original = "FREQ=YEARLY;BYMONTHDAY=1;BYMONTH=4"
        val roundTripped = RecurrenceRule.fromRRule(original).toRRule()
        assertEquals(original, roundTripped)
    }
}
