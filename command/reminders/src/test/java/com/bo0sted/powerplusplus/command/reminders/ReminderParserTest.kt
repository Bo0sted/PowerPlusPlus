package com.bo0sted.powerplusplus.command.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class ReminderParserTest {

    /** Monday 12 October 2026, 14:30. */
    private val now = LocalDateTime.of(2026, 10, 12, 14, 30)

    private fun check(transcript: String, text: String, time: LocalDateTime?) {
        assertEquals(ParsedReminder(text, time), ReminderParser.parse(transcript, now))
    }

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, month, day, hour, minute)

    @Test
    fun theExampleRequest() = check("Remind me at 8pm tomorrow to call Mom.", "Call Mom", at(10, 13, 20))

    @Test
    fun timeAfterTheTask() = check("remind me to call mom at 8 p.m. tomorrow", "Call mom", at(10, 13, 20))

    @Test
    fun relativeTimes() {
        check("Remind me in 10 minutes to take the pizza out.", "Take the pizza out", at(10, 12, 14, 40))
        check("Remind me in two hours to stretch", "Stretch", at(10, 12, 16, 30))
        check("remind me in half an hour to check the oven", "Check the oven", at(10, 12, 15))
        check("Remind me in a week to water the cactus", "Water the cactus", at(10, 19, 14, 30))
    }

    @Test
    fun anAmbiguousHourTodayIsTheNextOne() {
        check("Remind me at 8 to call mom", "Call mom", at(10, 12, 20)) // 8:00 has passed.
        check("Remind me at 3 to leave", "Leave", at(10, 12, 15))
        check("Remind me at 2 to leave", "Leave", at(10, 13, 2)) // 2:00 and 14:00 have both passed.
    }

    @Test
    fun anAmbiguousHourOnANamedDayIsTheUsualOne() {
        check("remind me at 2 tomorrow to call the dentist", "Call the dentist", at(10, 13, 14))
        check("Set a reminder for 7:30 tomorrow to go running", "Go running", at(10, 13, 7, 30))
    }

    @Test
    fun partsOfTheDay() {
        check("remind me tomorrow morning to water the plants", "Water the plants", at(10, 13, 9))
        check("remind me tonight to lock the door", "Lock the door", at(10, 12, 20))
        check("Remind me at 8 tonight to lock the door", "Lock the door", at(10, 12, 20))
    }

    @Test
    fun namedDays() {
        check("Remind me on Friday at 3:15 pm to submit the report", "Submit the report", at(10, 16, 15, 15))
        check("Remind me Monday to pay rent", "Pay rent", at(10, 19, 9)) // Today is Monday: next week's.
        check("remind me on October 20th at noon to renew the passport", "Renew the passport", at(10, 20, 12))
        check("Remind me at 9:00 AM on the 3rd of November to book flights", "Book flights", at(11, 3, 9))
        check("Remind me on March 1 to file taxes", "File taxes", LocalDateTime.of(2027, 3, 1, 9, 0))
    }

    @Test
    fun midnightIsTheComingOne() = check("Remind me at midnight to sleep", "Sleep", at(10, 13, 0))

    @Test
    fun twentyFourHourTimes() = check("Remind me at 21:45 to stretch", "Stretch", at(10, 12, 21, 45))

    @Test
    fun noTimeSpoken() = check("Remind me to buy milk.", "Buy milk", null)

    @Test
    fun politeAndOtherPhrasings() {
        check("Hey, can you remind me to call Sam at 6pm?", "Call Sam", at(10, 12, 18))
        check("Set a reminder for me to feed the cat in 5 minutes", "Feed the cat", at(10, 12, 14, 35))
    }

    @Test
    fun otherSpeechIsNotAReminder() {
        assertNull(ReminderParser.parse("Call mom at 8pm", now))
        assertNull(ReminderParser.parse("What's the weather tomorrow?", now))
    }
}
