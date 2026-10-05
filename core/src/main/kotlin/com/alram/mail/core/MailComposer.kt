package com.alram.mail.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class MailContent(val subject: String, val text: String, val html: String)

object MailComposer {
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val dateTimeFmt = DateTimeFormatter.ofPattern("M/d HH:mm")
    private val dateFmt = DateTimeFormatter.ofPattern("M월 d일")

    fun compose(kind: MailKind, items: List<CapturedNotification>, zone: ZoneId, now: Long): MailContent {
        require(items.isNotEmpty()) { "items must not be empty" }
        val sorted = items.sortedBy { it.postedAt }
        val subject = when (kind) {
            MailKind.INSTANT -> instantSubject(sorted)
            MailKind.BATCH -> "[알림 모음] ${sorted.size}건${appSummary(sorted)}"
            MailKind.DAILY -> "[오늘의 알림] ${Instant.ofEpochMilli(now).atZone(zone).format(dateFmt)} · ${sorted.size}건${appSummary(sorted)}"
        }.oneLine().truncate(150)
        val sections = sorted.groupBy { it.packageName }.values
            .sortedByDescending { g -> g.size }
        return MailContent(
            subject = subject,
            text = plainBody(sections, zone, now),
            html = htmlBody(sections, zone, now, kind),
        )
    }

    private fun instantSubject(items: List<CapturedNotification>): String {
        val first = items.first()
        val head = first.title.ifBlank { first.text.lineSequence().firstOrNull().orEmpty() }
            .ifBlank { "새 알림" }
        val tail = if (items.size > 1) " · ${items.size}건" else ""
        return "[${first.appLabel}] ${head.truncate(90)}$tail"
    }

    private fun appSummary(items: List<CapturedNotification>): String {
        val counts = items.groupBy { it.appLabel }.mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
        if (counts.isEmpty()) return ""
        val top = counts.take(3).joinToString(", ") { "${it.key} ${it.value}" }
        val rest = counts.size - 3
        return " · $top" + if (rest > 0) " 외 ${rest}개 앱" else ""
    }

    private fun timeLabel(ms: Long, zone: ZoneId, now: Long): String {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        val n = Instant.ofEpochMilli(now).atZone(zone)
        return if (t.toLocalDate() == n.toLocalDate()) t.format(timeFmt) else t.format(dateTimeFmt)
    }

    private fun plainBody(sections: Collection<List<CapturedNotification>>, zone: ZoneId, now: Long): String =
        buildString {
            for (g in sections) {
                appendLine("■ ${g.first().appLabel} (${g.size})")
                for (n in g) {
                    val head = n.title.ifBlank { "(제목 없음)" }
                    appendLine("  ${timeLabel(n.postedAt, zone, now)}  $head")
                    if (n.text.isNotBlank()) n.text.lines().forEach { appendLine("        $it") }
                    if (n.subText.isNotBlank()) appendLine("        · ${n.subText}")
                }
                appendLine()
            }
            append("— Alram Mail")
        }

    private fun htmlBody(
        sections: Collection<List<CapturedNotification>>,
        zone: ZoneId,
        now: Long,
        kind: MailKind,
    ): String = buildString {
        append("""<div style="font-family:-apple-system,'Segoe UI',Roboto,'Noto Sans KR',sans-serif;max-width:560px;margin:0 auto;color:#1c1b1a;">""")
        for (g in sections) {
            append("""<div style="margin:0 0 20px;">""")
            append("""<div style="font-size:12px;font-weight:600;letter-spacing:.2px;color:#0f766e;margin:0 0 6px;">""")
            append(esc(g.first().appLabel))
            if (kind != MailKind.INSTANT || g.size > 1) append(""" <span style="color:#8a8985;font-weight:500;">· ${g.size}</span>""")
            append("</div>")
            for (n in g) {
                append("""<div style="border-left:3px solid #cfe5e2;padding:2px 0 2px 12px;margin:0 0 10px;">""")
                append("""<div style="font-size:15px;font-weight:600;line-height:1.4;">${esc(n.title.ifBlank { "(제목 없음)" })}""")
                append(""" <span style="font-size:11px;font-weight:400;color:#8a8985;">${esc(timeLabel(n.postedAt, zone, now))}</span></div>""")
                if (n.text.isNotBlank()) {
                    append("""<div style="font-size:14px;line-height:1.5;color:#3f3e3b;white-space:pre-wrap;">${esc(n.text)}</div>""")
                }
                if (n.subText.isNotBlank()) {
                    append("""<div style="font-size:12px;color:#8a8985;margin-top:2px;">${esc(n.subText)}</div>""")
                }
                append("</div>")
            }
            append("</div>")
        }
        append("""<div style="font-size:11px;color:#a8a7a3;border-top:1px solid #e7e6e2;padding-top:8px;">Alram Mail</div></div>""")
    }

    private fun esc(s: String): String = buildString(s.length + 16) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(c)
        }
    }

    private fun String.oneLine(): String = replace(Regex("\\s+"), " ").trim()

    private fun String.truncate(max: Int): String =
        if (length <= max) this else take(max - 1).trimEnd() + "…"
}
