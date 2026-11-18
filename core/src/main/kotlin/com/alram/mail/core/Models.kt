package com.alram.mail.core

import java.time.ZoneId

enum class DeliveryMode { INSTANT, BATCH, DAILY }

enum class BodyMode { FULL, TITLE_ONLY }

enum class QuietPolicy { HOLD, DROP }

/** 알림을 어디로 보낼지. AGENT 는 에이전트 서버(웹훅)로 JSON 을 보낸다. */
enum class Destination { MAIL, AGENT, BOTH }

/** 폰에서 수집한 알림 한 건. */
data class CapturedNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val subText: String = "",
    val conversation: String = "",
    val postedAt: Long,
    val category: String = "",
    val isOngoing: Boolean = false,
    val isGroupSummary: Boolean = false,
) {
    /** 같은 대화방/발신자 알림을 한 통으로 묶을 때 쓰는 키. */
    val conversationKey: String get() = conversation.ifBlank { title }
}

/** 앱별 규칙. null 이면 전역 설정을 따른다. */
data class AppRule(
    val packageName: String,
    val enabled: Boolean? = null,
    val mode: DeliveryMode? = null,
    val bodyMode: BodyMode? = null,
    val maskOtp: Boolean? = null,
    val includeKeywords: List<String> = emptyList(),
    val excludeKeywords: List<String> = emptyList(),
    val recipients: List<String> = emptyList(),
    val destination: Destination? = null,
)

data class QuietHours(
    val enabled: Boolean = false,
    /** 자정 기준 분 (0..1439). */
    val startMinute: Int = 23 * 60,
    val endMinute: Int = 7 * 60,
    /** 방해금지가 *시작되는* 요일 (ISO: 월=1 .. 일=7). */
    val days: Set<Int> = (1..7).toSet(),
    val policy: QuietPolicy = QuietPolicy.HOLD,
)

data class GlobalSettings(
    val masterEnabled: Boolean = true,
    /** 이 시각(ms)까지 일시 중지. 0 이면 중지 아님. */
    val pausedUntil: Long = 0L,
    /** 규칙이 따로 없는 앱의 기본 전달 여부. */
    val newAppsEnabled: Boolean = true,
    val defaultMode: DeliveryMode = DeliveryMode.INSTANT,
    val defaultBodyMode: BodyMode = BodyMode.FULL,
    val maskOtp: Boolean = false,
    val batchMinutes: Int = 15,
    /** 하루 요약 발송 시각 (자정 기준 분). */
    val dailyMinute: Int = 21 * 60,
    /** 즉시 모드에서 연달아 오는 알림을 한 통으로 합치는 대기 시간. */
    val burstSeconds: Int = 4,
    /** 같은 내용의 알림을 무시하는 시간. 0 이면 끔. */
    val dedupeSeconds: Int = 60,
    val skipOngoing: Boolean = true,
    val skipGroupSummary: Boolean = true,
    val quiet: QuietHours = QuietHours(),
    /** 수신 주소. 비어 있으면 Gmail 계정 본인에게 보낸다. */
    val recipients: List<String> = emptyList(),
    val dailyMailCap: Int = 400,
    val maxItemsPerMail: Int = 100,
    /** 에이전트 서버로도 보내기. 꺼져 있으면 앱별 설정과 무관하게 메일로만 보낸다. */
    val agentEnabled: Boolean = false,
    /** 앱별로 정하지 않은 경우의 보낼 곳 (에이전트가 켜져 있을 때만 의미가 있다). */
    val defaultDestination: Destination = Destination.BOTH,
)

sealed interface Decision {
    data class Send(
        val mode: DeliveryMode,
        val dueAt: Long,
        val bodyMode: BodyMode,
        val maskOtp: Boolean,
        val recipients: List<String>,
        /** 메일 대기열에 넣을지. false 면 [mode]/[dueAt] 은 의미 없다. */
        val toMail: Boolean = true,
        /** 에이전트 대기열에 넣을지. */
        val toAgent: Boolean = false,
    ) : Decision

    data class Drop(val reason: DropReason) : Decision
}

enum class DropReason {
    MASTER_OFF, PAUSED, APP_OFF, ONGOING, GROUP_SUMMARY, EMPTY,
    EXCLUDED_KEYWORD, NO_INCLUDE_MATCH, QUIET_DROP,
}

/** 발송 대기열의 한 항목. */
data class QueuedItem(
    val id: Long,
    val notification: CapturedNotification,
    val mode: DeliveryMode,
    val dueAt: Long,
    val recipients: List<String>,
    val attempts: Int = 0,
)

