package com.alram.mail.core

import java.time.ZoneId

object RuleEngine {
    fun decide(
        n: CapturedNotification,
        rule: AppRule?,
        s: GlobalSettings,
        now: Long,
        zone: ZoneId,
    ): Decision {
        if (!s.masterEnabled) return Decision.Drop(DropReason.MASTER_OFF)
        if (now < s.pausedUntil) return Decision.Drop(DropReason.PAUSED)
        if (!(rule?.enabled ?: s.newAppsEnabled)) return Decision.Drop(DropReason.APP_OFF)
        if (n.isOngoing && s.skipOngoing) return Decision.Drop(DropReason.ONGOING)
        if (n.isGroupSummary && s.skipGroupSummary) return Decision.Drop(DropReason.GROUP_SUMMARY)
        if (n.title.isBlank() && n.text.isBlank()) return Decision.Drop(DropReason.EMPTY)

        if (rule != null) {
            val haystack = "${n.title}\n${n.text}\n${n.subText}\n${n.conversation}".lowercase()
            val exclude = rule.excludeKeywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (exclude.any { haystack.contains(it) }) return Decision.Drop(DropReason.EXCLUDED_KEYWORD)
            val include = rule.includeKeywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (include.isNotEmpty() && include.none { haystack.contains(it) }) {
                return Decision.Drop(DropReason.NO_INCLUDE_MATCH)
            }
        }

        var mode = rule?.mode ?: s.defaultMode
        var dueAt = Scheduler.dueAt(mode, now, s, zone)

        val quiet = s.quiet
        if (quiet.enabled) {
            if (quiet.contains(now, zone)) {
                if (quiet.policy == QuietPolicy.DROP) return Decision.Drop(DropReason.QUIET_DROP)
                // 방해금지 중에는 개별 발송 대신 끝난 뒤 한꺼번에 보낸다.
                if (mode == DeliveryMode.INSTANT) mode = DeliveryMode.BATCH
                dueAt = maxOf(dueAt, quiet.endAfter(now, zone))
            }
            // 묶음/요약 발송 시각이 방해금지 안에 떨어지면 끝난 뒤로 미룬다.
            if (quiet.contains(dueAt, zone)) dueAt = quiet.endAfter(dueAt, zone)
        }

        return Decision.Send(
            mode = mode,
            dueAt = dueAt,
            bodyMode = rule?.bodyMode ?: s.defaultBodyMode,
            maskOtp = rule?.maskOtp ?: s.maskOtp,
            recipients = rule?.recipients.orEmpty(),
        )
    }
}
