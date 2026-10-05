package com.alram.mail.core

import java.time.Instant
import java.time.ZoneId

object Scheduler {
    /** 전달 방식에 따라 이 알림이 언제 발송 대상이 되는지(ms). */
    fun dueAt(mode: DeliveryMode, now: Long, s: GlobalSettings, zone: ZoneId): Long = when (mode) {
        DeliveryMode.INSTANT -> now + s.burstSeconds.coerceAtLeast(0) * 1000L
        DeliveryMode.BATCH -> nextBatchSlot(now, s.batchMinutes, zone)
        DeliveryMode.DAILY -> nextDaily(now, s.dailyMinute, zone)
    }

    /** 자정 기준으로 정렬된 다음 N분 경계. 같은 구간 알림이 한 통으로 묶인다. */
    fun nextBatchSlot(now: Long, minutes: Int, zone: ZoneId): Long {
        val interval = minutes.coerceAtLeast(1) * 60_000L
        val midnight = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val offset = now - midnight
        return midnight + (offset / interval + 1) * interval
    }

    fun nextDaily(now: Long, minuteOfDay: Int, zone: ZoneId): Long {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone)
        val candidate = today.plusMinutes(minuteOfDay.toLong())
        val result = if (candidate.toInstant().toEpochMilli() > now) candidate else candidate.plusDays(1)
        return result.toInstant().toEpochMilli()
    }
}

object Backoff {
    const val MAX_ATTEMPTS = 8

    /** 발송 실패 후 재시도까지 대기 시간: 15초부터 두 배씩, 최대 15분. */
    fun delayMs(attempt: Int): Long {
        val n = attempt.coerceIn(1, 10)
        return minOf(15 * 60_000L, 15_000L shl (n - 1))
    }
}
