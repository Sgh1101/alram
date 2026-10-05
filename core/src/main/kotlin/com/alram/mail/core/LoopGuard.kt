package com.alram.mail.core

/**
 * 이 앱이 보낸 메일이 메일 앱 알림으로 다시 잡혀서 또 메일이 되는 되먹임(무한 반복)을 막는다.
 * 1) 메일 발신자 이름([SENDER_NAME])이 들어간 알림은 버린다.
 * 2) 최근에 보낸 메일 제목이 그대로 들어간 알림도 버린다. (발신자 이름이 "나" 등으로 바뀌어 보이는 경우 대비)
 */
object LoopGuard {
    /** 보내는 메일의 표시 이름. 메일 앱 알림에서는 보낸 사람으로 나타난다. */
    const val SENDER_NAME = "Alram Mail"

    /** 메일 앱. 메일을 메일로 다시 보낼 필요가 없으므로 처음에는 꺼진 상태로 등록한다. */
    val MAIL_APPS: Set<String> = setOf(
        "com.google.android.gm",                // Gmail
        "com.google.android.gm.lite",           // Gmail Go
        "com.samsung.android.email.provider",   // 삼성 이메일
        "com.microsoft.office.outlook",         // Outlook
        "com.nhn.android.mail",                 // 네이버 메일
        "net.daum.android.mail",                // 다음 메일
        "com.yahoo.mobile.client.android.mail", // Yahoo 메일
        "ch.protonmail.android",                // Proton Mail
    )

    fun isEcho(n: CapturedNotification, recentSubjects: Collection<String>): Boolean {
        val haystack = "${n.title}\n${n.text}\n${n.subText}\n${n.conversation}"
        if (haystack.contains(SENDER_NAME, ignoreCase = true)) return true
        return recentSubjects.any { raw ->
            val subject = raw.trim()
            subject.length >= 6 &&
                (haystack.contains(subject) || (subject.length > 24 && haystack.contains(subject.take(24))))
        }
    }
}
