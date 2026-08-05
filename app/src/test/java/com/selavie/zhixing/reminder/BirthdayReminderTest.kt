package com.selavie.zhixing.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.*

class BirthdayReminderTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `birthday schedules at eight local time this year`() {
        val now = ZonedDateTime.of(2026, 8, 5, 7, 0, 0, 0, zone)
        val trigger = BirthdayReminder.nextBirthdayAtEight(LocalDate.of(1998, 8, 5), now)
        assertEquals(ZonedDateTime.of(2026, 8, 5, 8, 0, 0, 0, zone), trigger)
    }

    @Test
    fun `birthday schedules next year after eight`() {
        val now = ZonedDateTime.of(2026, 8, 5, 9, 0, 0, 0, zone)
        val trigger = BirthdayReminder.nextBirthdayAtEight(LocalDate.of(1998, 8, 5), now)
        assertEquals(ZonedDateTime.of(2027, 8, 5, 8, 0, 0, 0, zone), trigger)
    }
}
