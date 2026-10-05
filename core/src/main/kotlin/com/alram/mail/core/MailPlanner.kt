package com.alram.mail.core

enum class MailKind { INSTANT, BATCH, DAILY }

data class PlannedMail(
    val kind: MailKind,
    val recipients: List<String>,
    val itemIds: List<Long>,
    val items: List<CapturedNotification>,
)

object MailPlanner {
    /**
     * 발송 대상 항목을 실제로 보낼 메일 목록으로 묶는다.
     * - 즉시: 같은 앱·대화방끼리 한 통
     * - 묶음/하루 요약: 수신 주소별로 한 통 (항목이 많으면 [maxItemsPerMail] 단위로 분할)
     * - 메일 수가 [maxMails] 를 넘으면 즉시 알림도 묶음 메일로 합쳐서 한도를 지킨다.
     */
    fun plan(
        due: List<QueuedItem>,
        defaultRecipients: List<String>,
        maxMails: Int,
        maxItemsPerMail: Int,
    ): List<PlannedMail> {
        if (maxMails <= 0 || due.isEmpty()) return emptyList()
        val perMail = maxItemsPerMail.coerceAtLeast(1)
        val byRecipients = due.groupBy { it.recipients.ifEmpty { defaultRecipients } }

        fun build(foldInstants: Boolean): List<PlannedMail> = buildList {
            for ((rcpt, items) in byRecipients) {
                if (rcpt.isEmpty()) continue
                val sorted = items.sortedWith(compareBy({ it.notification.postedAt }, { it.id }))
                val instants = sorted.filter { it.mode == DeliveryMode.INSTANT }
                val batch = sorted.filter { it.mode == DeliveryMode.BATCH }
                val daily = sorted.filter { it.mode == DeliveryMode.DAILY }

                if (!foldInstants) {
                    instants
                        .groupBy { it.notification.packageName + "|" + it.notification.conversationKey }
                        .values
                        .forEach { g -> g.chunked(perMail).forEach { add(mail(MailKind.INSTANT, rcpt, it)) } }
                }
                val digest = if (foldInstants) {
                    (batch + instants).sortedWith(compareBy({ it.notification.postedAt }, { it.id }))
                } else batch
                digest.chunked(perMail).forEach { add(mail(MailKind.BATCH, rcpt, it)) }
                daily.chunked(perMail).forEach { add(mail(MailKind.DAILY, rcpt, it)) }
            }
        }

        var plan = build(foldInstants = false)
        if (plan.size > maxMails) plan = build(foldInstants = true)
        return plan.take(maxMails)
    }

    private fun mail(kind: MailKind, rcpt: List<String>, items: List<QueuedItem>) = PlannedMail(
        kind = kind,
        recipients = rcpt,
        itemIds = items.map { it.id },
        items = items.map { it.notification },
    )
}
