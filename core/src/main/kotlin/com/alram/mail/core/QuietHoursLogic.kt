package com.alram.mail.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

private fun zdt(millis: Long, zone: ZoneId): ZonedDateTime =
    Instant.ofEpochMilli(millis).atZone(zone)

private fun ZonedDateTime.minuteOfDay(): Int = hour * 60 + minute

/** [millis] 시점이 방해금지 시간대 안에 있는지. */
fun QuietHours.contains(millis: Long, zone: ZoneId): Boolean {
    if (!enabled || startMinute == endMinute) return false
    val t = zdt(millis, zone)
    val m = t.minuteOfDay()
    val today = t.dayOfWeek.value
    val yesterday = t.minusDays(1).dayOfWeek.value
    return if (startMinute < endMinute) {
        today in days && m in startMinute until endMinute
    } else {
        (m >= startMinute && today in days) || (m < endMinute && yesterday in days)
    }
}

/** [millis] 이 방해금지 시간대 안일 때, 그 시간대가 끝나는 시각(ms). */
fun QuietHours.endAfter(millis: Long, zone: ZoneId): Long {
    val t = zdt(millis, zone)
    val m = t.minuteOfDay()
    val endToday = t.toLocalDate().atStartOfDay(zone).plusMinutes(endMinute.toLong())
    val end = if (startMinute > endMinute && m >= startMinute) endToday.plusDays(1) else endToday
    return end.toInstant().toEpochMilli()
}
