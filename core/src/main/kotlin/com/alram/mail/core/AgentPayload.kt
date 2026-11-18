package com.alram.mail.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 에이전트 서버로 보낼 알림 한 건. [id] 는 서버에서 중복 저장을 막는 고유 키다. */
data class AgentItem(val id: String, val notification: CapturedNotification)

/**
 * 에이전트 서버(`POST /ingest`)로 보내는 JSON 을 만든다. 외부 라이브러리 없이 직접 쓴다.
 *
 * ```
 * {"version":1,"device":{"id":"…","name":"…"},"sentAt":1700000000000,
 *  "items":[{"id":"…","package":"com.kakao.talk","app":"카카오톡","title":"…","text":"…",
 *            "subText":"…","conversation":"…","category":"msg",
 *            "postedAt":1700000000000,"postedAtIso":"2026-11-18T09:12:00+09:00"}]}
 * ```
 */
object AgentPayload {
    const val VERSION = 1
    private val iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun encode(deviceId: String, deviceName: String, items: List<AgentItem>, zone: ZoneId, now: Long): String =
        buildString {
            append("{\"version\":").append(VERSION)
            append(",\"device\":{\"id\":").str(deviceId).append(",\"name\":").str(deviceName).append('}')
            append(",\"sentAt\":").append(now)
            append(",\"items\":[")
            items.forEachIndexed { i, item ->
                if (i > 0) append(',')
                val n = item.notification
                append("{\"id\":").str(item.id)
                append(",\"package\":").str(n.packageName)
                append(",\"app\":").str(n.appLabel)
                append(",\"title\":").str(n.title)
                append(",\"text\":").str(n.text)
                append(",\"subText\":").str(n.subText)
                append(",\"conversation\":").str(n.conversation)
                append(",\"category\":").str(n.category)
                append(",\"postedAt\":").append(n.postedAt)
                append(",\"postedAtIso\":").str(Instant.ofEpochMilli(n.postedAt).atZone(zone).format(iso))
                append('}')
            }
            append("]}")
        }

    private fun StringBuilder.str(value: String): StringBuilder {
        append('"')
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                // U+2028/2029 는 JSON 에선 문제없지만 JS 소스에 섞이면 줄바꿈이 되어 깨지므로 이스케이프한다.
                ' ', ' ' -> append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                else -> if (ch < ' ') append("\\u").append(ch.code.toString(16).padStart(4, '0')) else append(ch)
            }
        }
        append('"')
        return this
    }
}
