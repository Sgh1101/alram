package com.alram.mail.engine

import android.content.Context
import com.alram.mail.core.BodyMode
import com.alram.mail.core.CapturedNotification
import com.alram.mail.core.Decision
import com.alram.mail.core.Fingerprint
import com.alram.mail.core.OtpMasker
import com.alram.mail.core.RuleEngine
import com.alram.mail.data.AlramDb
import com.alram.mail.data.AppCatalog
import com.alram.mail.data.AppRuleEntity
import com.alram.mail.data.NotificationEntity
import com.alram.mail.data.SettingsRepository
import java.time.ZoneId

/** 수집한 알림 한 건에 규칙을 적용해서 대기열에 넣는다. */
class CaptureProcessor(
    private val context: Context,
    private val db: AlramDb,
    private val settings: SettingsRepository,
    private val dispatcher: Dispatcher,
) {
    suspend fun handle(n: CapturedNotification) {
        val now = System.currentTimeMillis()
        val core = settings.current().core

        // 앱 목록 화면과 "최근 알림 받은 앱" 필터를 위해 어떤 결정이든 앱은 기록한다.
        val rules = db.appRules()
        rules.insertIgnore(
            AppRuleEntity(
                packageName = n.packageName,
                label = n.appLabel,
                isSystem = AppCatalog.isSystemPackage(context.packageManager, n.packageName),
            ),
        )
        rules.touch(n.packageName, n.appLabel, now)

        val rule = rules.get(n.packageName)?.toRule()
        val decision = RuleEngine.decide(n, rule, core, now, ZoneId.systemDefault())
        if (decision !is Decision.Send) return

        var title = n.title
        var text = n.text
        var subText = n.subText
        if (decision.maskOtp) {
            title = OtpMasker.mask(title)
            text = OtpMasker.mask(text)
            subText = OtpMasker.mask(subText)
        }
        if (decision.bodyMode == BodyMode.TITLE_ONLY) {
            text = ""
            subText = ""
        }

        val fingerprint = Fingerprint.of(n.packageName, title, text)
        if (core.dedupeSeconds > 0 &&
            db.notifications().countFingerprintSince(fingerprint, now - core.dedupeSeconds * 1000L) > 0
        ) return

        db.notifications().insert(
            NotificationEntity(
                sbnKey = n.key,
                packageName = n.packageName,
                appLabel = n.appLabel,
                title = title,
                text = text,
                subText = subText,
                conversation = n.conversation,
                category = n.category,
                postedAt = n.postedAt,
                createdAt = now,
                mode = decision.mode.name,
                dueAt = decision.dueAt,
                recipients = decision.recipients.joinToString(","),
                fingerprint = fingerprint,
            ),
        )
        dispatcher.poke()
    }
}
