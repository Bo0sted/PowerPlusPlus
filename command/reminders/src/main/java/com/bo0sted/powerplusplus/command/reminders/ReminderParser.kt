package com.bo0sted.powerplusplus.command.reminders

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** What a reminder request said: the task and, if one was spoken, when. */
internal data class ParsedReminder(val text: String, val time: LocalDateTime?)

/**
 * Understands English reminder requests as Whisper writes them: "Remind me at 8pm tomorrow to call
 * Mom.", "remind me in 10 minutes to take the pizza out", "Set a reminder for Friday at 3:15 p.m. to
 * submit the report". Time phrases are found anywhere and removed; what's left is the task. Ambiguous
 * hours ("at 8") become the next such time. Everything is editable in the modal afterwards, so this
 * favours a sensible guess over refusing. Pure; unit-tested.
 */
internal object ReminderParser {

    /** `null` if [transcript] isn't a reminder request. */
    fun parse(transcript: String, now: LocalDateTime): ParsedReminder? {
        val trigger = TRIGGER.find(transcript) ?: return null
        val found = Found()
        var rest = transcript.substring(trigger.range.last + 1)
        for ((pattern, action) in RULES) {
            rest = pattern.replace(rest) { match ->
                found.action(match, now.toLocalDate())
                " "
            }
        }
        return ParsedReminder(task(rest), found.resolve(now))
    }

    private class Found {
        var offset: Duration? = null
        var date: LocalDate? = null
        var hour: Int? = null
        var minute = 0
        var meridiem: Meridiem? = null
        var part: PartOfDay? = null

        fun resolve(now: LocalDateTime): LocalDateTime? {
            offset?.let { return now.plus(it).truncatedTo(ChronoUnit.MINUTES) }
            val today = now.toLocalDate()
            val hour = hour
            if (hour == null) {
                val time = part?.time ?: if (date != null) MORNING else return null
                return LocalDateTime.of(date ?: today, time).rolledPast(now)
            }
            val day = date
            val hour24 = when {
                meridiem == Meridiem.Am -> hour % 12
                meridiem == Meridiem.Pm -> hour % 12 + 12
                hour == 0 || hour > 12 -> hour
                part != null -> if (part == PartOfDay.Morning) hour % 12 else hour % 12 + 12
                // A named day: the hour people usually mean (2 → 14:00, 8 → 8:00).
                day != null && day != today -> if (hour in 1..6) hour + 12 else hour
                // Today: the next time the clock shows this hour.
                else -> {
                    val candidates = listOf(hour % 12, hour % 12 + 12).map { LocalDateTime.of(today, LocalTime.of(it, minute)) }
                    return candidates.firstOrNull { it.isAfter(now) } ?: candidates.first().plusDays(1)
                }
            }
            return LocalDateTime.of(day ?: today, LocalTime.of(hour24, minute)).rolledPast(now)
        }

        /** A time without a named day that has passed today means the same time tomorrow. */
        private fun LocalDateTime.rolledPast(now: LocalDateTime) = if (date == null && !isAfter(now)) plusDays(1) else this
    }

    private enum class Meridiem { Am, Pm }

    private enum class PartOfDay(val time: LocalTime) {
        Morning(MORNING), Afternoon(LocalTime.of(15, 0)), Evening(LocalTime.of(19, 0)), Night(LocalTime.of(20, 0))
    }

    /** What's left once the trigger and every time phrase are gone, without connecting words. */
    private fun task(rest: String): String {
        var text = rest.replace(Regex("\\s+"), " ").trim().trim { it in TRIM }
        while (true) {
            val stripped = LEADING.replace(text, "").trim().trim { it in TRIM }
            val cut = TRAILING.replace(stripped, "").trim().trim { it in TRIM }
            if (cut == text) break
            text = cut
        }
        return text.replaceFirstChar { it.uppercaseChar() }
    }

    private const val TRIM = " ,.;:!?-"
    private val MORNING = LocalTime.of(9, 0)

    private val TRIGGER = Regex(
        """^\W*(?:(?:hey|hi|ok|okay|please|can you|could you|would you|will you|power)\W+)*""" +
            """(?:remind(?:\s+me)?|set\s+(?:a|an|me\s+a)\s+reminder|create\s+(?:a\s+)?reminder|reminder)\b\W*""",
        RegexOption.IGNORE_CASE,
    )
    private val LEADING = Regex("""^(?:to|that|about|of|for|me|i need to|i have to|i should|i must)\b""", RegexOption.IGNORE_CASE)
    private val TRAILING = Regex("""\b(?:at|on|in|by|for|and|around)$""", RegexOption.IGNORE_CASE)

    private val NUMBER_WORDS = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "fifteen" to 15,
        "twenty" to 20, "thirty" to 30, "forty" to 40, "forty-five" to 45, "forty five" to 45, "fifty" to 50,
        "sixty" to 60, "ninety" to 90,
    )
    private val HOUR_WORDS = NUMBER_WORDS.filterValues { it <= 12 }.filterKeys { it != "a" && it != "an" }
    private val NUMBER = """(\d+|${NUMBER_WORDS.keys.sortedByDescending { it.length }.joinToString("|")})"""
    private val HOUR = """(\d{1,2}|${HOUR_WORDS.keys.sortedByDescending { it.length }.joinToString("|")})"""
    private const val AT = """(?:\b(?:at|for|by|around)\s+)"""
    private const val MERIDIEM = """([ap])\.?\s?m\b\.?"""
    private val WEEKDAYS = DayOfWeek.entries.joinToString("|") { it.name.lowercase() }
    private val MONTHS = Month.entries.joinToString("|") { "${it.name.lowercase()}|${it.name.lowercase().take(3)}" }

    private fun number(word: String): Int = word.toIntOrNull() ?: NUMBER_WORDS.getValue(word.lowercase())

    private fun month(word: String): Month = Month.entries.first { it.name.lowercase().startsWith(word.lowercase().take(3)) }

    private fun Found.setTime(hourText: String, minuteText: String?, meridiemText: String?) {
        hour = number(hourText).takeIf { it in 0..23 } ?: return
        minute = minuteText?.toIntOrNull()?.takeIf { it in 0..59 } ?: 0
        meridiem = when (meridiemText?.lowercase()) {
            "a" -> Meridiem.Am
            "p" -> Meridiem.Pm
            else -> null
        }
    }

    private fun Found.setDayOfMonth(today: LocalDate, month: Month, day: Int) {
        if (day !in 1..month.maxLength()) return
        val candidate = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return
        date = if (candidate.isBefore(today)) candidate.plusYears(1) else candidate
    }

    private fun rule(pattern: String, action: Found.(MatchResult, LocalDate) -> Unit) =
        Regex(pattern, RegexOption.IGNORE_CASE) to action

    /** In order: the most specific phrases first, so their parts aren't taken by looser ones. */
    private val RULES: List<Pair<Regex, Found.(MatchResult, LocalDate) -> Unit>> = listOf(
        rule("""\b(?:in|after)\s+half\s+an?\s+hour\b""") { _, _ -> offset = Duration.ofMinutes(30) },
        rule("""\b(?:in|after)\s+$NUMBER\s+(?:more\s+)?(minutes?|mins?|hours?|hrs?|days?|weeks?)\b""") { m, _ ->
            val amount = number(m.groupValues[1]).toLong()
            offset = when (m.groupValues[2].lowercase().first()) {
                'm' -> Duration.ofMinutes(amount)
                'h' -> Duration.ofHours(amount)
                'd' -> Duration.ofDays(amount)
                else -> Duration.ofDays(7 * amount)
            }
        },
        rule("""\b(?:the\s+)?day\s+after\s+tomorrow\b""") { _, today -> date = today.plusDays(2) },
        rule("""\btomorrow\b""") { _, today -> date = today.plusDays(1) },
        rule("""\btonight\b""") { _, today ->
            date = today
            part = PartOfDay.Night
        },
        rule("""\btoday\b""") { _, today -> date = today },
        rule("""\b(?:in\s+the\s+|this\s+|at\s+)?(morning|afternoon|evening|night)\b""") { m, _ ->
            part = PartOfDay.entries.first { it.name.equals(m.groupValues[1], ignoreCase = true) }
        },
        rule("""\b(?:on\s+)?(?:(?:next|this|coming)\s+)?($WEEKDAYS)\b""") { m, today ->
            date = today.with(TemporalAdjusters.next(DayOfWeek.valueOf(m.groupValues[1].uppercase())))
        },
        rule("""\b(?:on\s+)?(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($MONTHS)\b\.?""") { m, today ->
            setDayOfMonth(today, month(m.groupValues[2]), m.groupValues[1].toInt())
        },
        rule("""\b(?:on\s+)?($MONTHS)\.?\s+(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)?\b""") { m, today ->
            setDayOfMonth(today, month(m.groupValues[1]), m.groupValues[2].toInt())
        },
        rule("""$AT?(?:12\s+)?\b(noon|midday|midnight)\b""") { m, _ ->
            hour = if (m.groupValues[1].lowercase() == "midnight") 0 else 12
            minute = 0
            meridiem = null
        },
        rule("""$AT?\b$HOUR(?:[:.](\d{2}))?\s*$MERIDIEM""") { m, _ -> setTime(m.groupValues[1], m.groupValues[2], m.groupValues[3]) },
        rule("""$AT?\b(\d{1,2})[:.](\d{2})\b""") { m, _ -> setTime(m.groupValues[1], m.groupValues[2], null) },
        rule("""$AT$HOUR(?:\s+o'?\s?clock)?\b""") { m, _ -> setTime(m.groupValues[1], null, null) },
    )
}
