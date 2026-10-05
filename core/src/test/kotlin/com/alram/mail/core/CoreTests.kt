package com.alram.mail.core

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0, s: Int = 0): Long =
    ZonedDateTime.of(y, mo, d, h, mi, s, 0, SEOUL).toInstant().toEpochMilli()

private fun notif(
    pkg: String = "com.kakao.talk",
    label: String = "카카오톡",
    title: String = "홍길동",
    text: String = "안녕",
    conversation: String = "",
    postedAt: Long = 0,
    ongoing: Boolean = false,
    summary: Boolean = false,
) = CapturedNotification(
    key = "$pkg:$title:$postedAt", packageName = pkg, appLabel = label, title = title, text = text,
    conversation = conversation, postedAt = postedAt, isOngoing = ongoing, isGroupSummary = summary,
)

class RuleEngineTest {
    private val now = at(2026, 10, 5, 12, 0)
    private val s = GlobalSettings()

    @Test fun `기본 설정이면 즉시 모드로 전송`() {
        val d = RuleEngine.decide(notif(), null, s, now, SEOUL)
        assertIs<Decision.Send>(d)
        assertEquals(DeliveryMode.INSTANT, d.mode)
        assertEquals(now + 4000, d.dueAt)
    }

    @Test fun `마스터 스위치와 일시중지`() {
        assertEquals(Decision.Drop(DropReason.MASTER_OFF),
            RuleEngine.decide(notif(), null, s.copy(masterEnabled = false), now, SEOUL))
        assertEquals(Decision.Drop(DropReason.PAUSED),
            RuleEngine.decide(notif(), null, s.copy(pausedUntil = now + 1), now, SEOUL))
        assertIs<Decision.Send>(RuleEngine.decide(notif(), null, s.copy(pausedUntil = now), now, SEOUL))
    }

    @Test fun `앱별 끄기는 전역 기본값보다 우선`() {
        val off = AppRule("com.kakao.talk", enabled = false)
        assertEquals(Decision.Drop(DropReason.APP_OFF), RuleEngine.decide(notif(), off, s, now, SEOUL))
        val on = AppRule("com.kakao.talk", enabled = true)
        assertIs<Decision.Send>(RuleEngine.decide(notif(), on, s.copy(newAppsEnabled = false), now, SEOUL))
        assertEquals(Decision.Drop(DropReason.APP_OFF),
            RuleEngine.decide(notif(), null, s.copy(newAppsEnabled = false), now, SEOUL))
    }

    @Test fun `진행중과 그룹요약과 빈 알림 제외`() {
        assertEquals(Decision.Drop(DropReason.ONGOING), RuleEngine.decide(notif(ongoing = true), null, s, now, SEOUL))
        assertEquals(Decision.Drop(DropReason.GROUP_SUMMARY), RuleEngine.decide(notif(summary = true), null, s, now, SEOUL))
        assertEquals(Decision.Drop(DropReason.EMPTY), RuleEngine.decide(notif(title = " ", text = ""), null, s, now, SEOUL))
        assertIs<Decision.Send>(RuleEngine.decide(notif(ongoing = true), null, s.copy(skipOngoing = false), now, SEOUL))
    }

    @Test fun `키워드 필터`() {
        val rule = AppRule("p", includeKeywords = listOf("결제", "OTP"), excludeKeywords = listOf("광고"))
        assertIs<Decision.Send>(RuleEngine.decide(notif(text = "결제 완료"), rule, s, now, SEOUL))
        assertIs<Decision.Send>(RuleEngine.decide(notif(text = "your otp is"), rule, s, now, SEOUL))
        assertEquals(Decision.Drop(DropReason.NO_INCLUDE_MATCH), RuleEngine.decide(notif(text = "안녕"), rule, s, now, SEOUL))
        assertEquals(Decision.Drop(DropReason.EXCLUDED_KEYWORD), RuleEngine.decide(notif(text = "(광고) 결제"), rule, s, now, SEOUL))
    }

    @Test fun `앱별 전달 방식과 본문 설정이 적용된다`() {
        val rule = AppRule("p", mode = DeliveryMode.BATCH, bodyMode = BodyMode.TITLE_ONLY, maskOtp = true,
            recipients = listOf("a@b.c"))
        val d = RuleEngine.decide(notif(), rule, s, now, SEOUL) as Decision.Send
        assertEquals(DeliveryMode.BATCH, d.mode)
        assertEquals(BodyMode.TITLE_ONLY, d.bodyMode)
        assertTrue(d.maskOtp)
        assertEquals(listOf("a@b.c"), d.recipients)
        assertEquals(at(2026, 10, 5, 12, 15), d.dueAt)
    }

    @Test fun `방해금지 중 즉시 알림은 끝난 뒤 묶음으로`() {
        val quiet = QuietHours(enabled = true, startMinute = 23 * 60, endMinute = 7 * 60)
        val night = at(2026, 10, 5, 23, 30)
        val d = RuleEngine.decide(notif(), null, s.copy(quiet = quiet), night, SEOUL) as Decision.Send
        assertEquals(DeliveryMode.BATCH, d.mode)
        assertEquals(at(2026, 10, 6, 7, 0), d.dueAt)

        val early = at(2026, 10, 6, 3, 0)
        val d2 = RuleEngine.decide(notif(), null, s.copy(quiet = quiet), early, SEOUL) as Decision.Send
        assertEquals(at(2026, 10, 6, 7, 0), d2.dueAt)
    }

    @Test fun `방해금지 버리기 정책`() {
        val quiet = QuietHours(enabled = true, startMinute = 23 * 60, endMinute = 7 * 60, policy = QuietPolicy.DROP)
        assertEquals(Decision.Drop(DropReason.QUIET_DROP),
            RuleEngine.decide(notif(), null, s.copy(quiet = quiet), at(2026, 10, 5, 23, 30), SEOUL))
        assertIs<Decision.Send>(RuleEngine.decide(notif(), null, s.copy(quiet = quiet), now, SEOUL))
    }

    @Test fun `하루 요약 시각이 방해금지 안이면 뒤로 미룬다`() {
        val quiet = QuietHours(enabled = true, startMinute = 20 * 60, endMinute = 8 * 60)
        val rule = AppRule("p", mode = DeliveryMode.DAILY)
        val d = RuleEngine.decide(notif(), rule, s.copy(quiet = quiet, dailyMinute = 21 * 60), now, SEOUL) as Decision.Send
        assertEquals(at(2026, 10, 6, 8, 0), d.dueAt)
    }
}

class QuietHoursTest {
    @Test fun `같은 날 구간과 요일 제한`() {
        // 2026-10-05 는 월요일(1)
        val q = QuietHours(enabled = true, startMinute = 9 * 60, endMinute = 18 * 60, days = setOf(1))
        assertTrue(q.contains(at(2026, 10, 5, 10), SEOUL))
        assertFalse(q.contains(at(2026, 10, 5, 18), SEOUL))
        assertFalse(q.contains(at(2026, 10, 6, 10), SEOUL))
    }

    @Test fun `자정을 넘는 구간은 시작 요일 기준`() {
        val q = QuietHours(enabled = true, startMinute = 23 * 60, endMinute = 7 * 60, days = setOf(5)) // 금요일 밤만
        assertTrue(q.contains(at(2026, 10, 9, 23, 30), SEOUL))  // 금 23:30
        assertTrue(q.contains(at(2026, 10, 10, 6, 0), SEOUL))   // 토 06:00
        assertFalse(q.contains(at(2026, 10, 10, 23, 30), SEOUL)) // 토 23:30
        assertFalse(q.contains(at(2026, 10, 9, 6, 0), SEOUL))   // 금 06:00 (목요일 밤은 제외)
    }

    @Test fun `꺼져 있으면 항상 false`() {
        assertFalse(QuietHours(enabled = false).contains(at(2026, 10, 5, 23, 30), SEOUL))
    }
}

class SchedulerTest {
    @Test fun `묶음 슬롯은 자정 기준 정렬`() {
        assertEquals(at(2026, 10, 5, 12, 15), Scheduler.nextBatchSlot(at(2026, 10, 5, 12, 0), 15, SEOUL))
        assertEquals(at(2026, 10, 5, 12, 15), Scheduler.nextBatchSlot(at(2026, 10, 5, 12, 14, 59), 15, SEOUL))
        assertEquals(at(2026, 10, 6, 0, 0), Scheduler.nextBatchSlot(at(2026, 10, 5, 23, 50), 15, SEOUL))
    }

    @Test fun `하루 요약은 오늘 또는 내일`() {
        assertEquals(at(2026, 10, 5, 21, 0), Scheduler.nextDaily(at(2026, 10, 5, 12), 21 * 60, SEOUL))
        assertEquals(at(2026, 10, 6, 21, 0), Scheduler.nextDaily(at(2026, 10, 5, 21, 0), 21 * 60, SEOUL))
    }

    @Test fun `재시도 대기는 두 배씩 늘고 15분에서 멈춘다`() {
        assertEquals(15_000, Backoff.delayMs(1))
        assertEquals(30_000, Backoff.delayMs(2))
        assertEquals(15 * 60_000L, Backoff.delayMs(9))
        assertEquals(15 * 60_000L, Backoff.delayMs(100))
    }
}

class OtpMaskerTest {
    @Test fun `인증 문구가 있으면 숫자를 가린다`() {
        assertEquals("[Web발신] 인증번호 [••••••] 입니다", OtpMasker.mask("[Web발신] 인증번호 [123456] 입니다"))
        assertEquals("Your code is ••••", OtpMasker.mask("Your code is 4821"))
    }

    @Test fun `일반 숫자와 전화번호는 그대로`() {
        assertEquals("오후 3시 회의 12345", OtpMasker.mask("오후 3시 회의 12345"))
        assertEquals("인증 문의 010-1234-5678", OtpMasker.mask("인증 문의 010-1234-5678"))
    }
}

class FingerprintTest {
    @Test fun `같은 내용은 같고 다르면 다르다`() {
        assertEquals(Fingerprint.of("a", "t", "x"), Fingerprint.of("a", " t ", "x "))
        assertTrue(Fingerprint.of("a", "t", "x") != Fingerprint.of("a", "t", "y"))
        assertTrue(Fingerprint.of("a", "t", "x") != Fingerprint.of("b", "t", "x"))
    }
}

class MailPlannerTest {
    private fun q(id: Long, mode: DeliveryMode, pkg: String = "p1", title: String = "A", t: Long = id,
                  rcpt: List<String> = emptyList()) =
        QueuedItem(id, notif(pkg = pkg, label = pkg, title = title, postedAt = t), mode, 0, rcpt)

    private val me = listOf("me@gmail.com")

    @Test fun `즉시 알림은 대화방별로 묶는다`() {
        val plan = MailPlanner.plan(
            listOf(q(1, DeliveryMode.INSTANT, title = "A"), q(2, DeliveryMode.INSTANT, title = "A"),
                q(3, DeliveryMode.INSTANT, title = "B")), me, 10, 100)
        assertEquals(2, plan.size)
        assertEquals(listOf(1L, 2L), plan[0].itemIds)
        assertEquals(MailKind.INSTANT, plan[0].kind)
    }

    @Test fun `묶음과 요약은 각각 한 통`() {
        val plan = MailPlanner.plan(
            listOf(q(1, DeliveryMode.BATCH, pkg = "a"), q(2, DeliveryMode.BATCH, pkg = "b"), q(3, DeliveryMode.DAILY)),
            me, 10, 100)
        assertEquals(listOf(MailKind.BATCH, MailKind.DAILY), plan.map { it.kind })
        assertEquals(listOf(1L, 2L), plan[0].itemIds)
    }

    @Test fun `수신 주소가 다르면 섞지 않는다`() {
        val plan = MailPlanner.plan(
            listOf(q(1, DeliveryMode.BATCH), q(2, DeliveryMode.BATCH, rcpt = listOf("x@y.z"))), me, 10, 100)
        assertEquals(2, plan.size)
        assertEquals(setOf(me, listOf("x@y.z")), plan.map { it.recipients }.toSet())
    }

    @Test fun `항목이 많으면 나눠 보낸다`() {
        val items = (1L..5L).map { q(it, DeliveryMode.BATCH) }
        assertEquals(listOf(2, 2, 1), MailPlanner.plan(items, me, 10, 2).map { it.itemIds.size })
    }

    @Test fun `메일 한도를 넘으면 즉시 알림을 묶음으로 합친다`() {
        val items = (1L..6L).map { q(it, DeliveryMode.INSTANT, title = "T$it") }
        val plan = MailPlanner.plan(items, me, 3, 100)
        assertEquals(1, plan.size)
        assertEquals(MailKind.BATCH, plan[0].kind)
        assertEquals(6, plan[0].itemIds.size)
    }

    @Test fun `수신 주소가 하나도 없으면 보내지 않는다`() {
        assertTrue(MailPlanner.plan(listOf(q(1, DeliveryMode.INSTANT)), emptyList(), 10, 100).isEmpty())
        assertTrue(MailPlanner.plan(listOf(q(1, DeliveryMode.INSTANT)), me, 0, 100).isEmpty())
    }
}

class MailComposerTest {
    private val now = at(2026, 10, 5, 12, 0)

    @Test fun `즉시 메일 제목`() {
        val c = MailComposer.compose(MailKind.INSTANT, listOf(notif(postedAt = now)), SEOUL, now)
        assertEquals("[카카오톡] 홍길동", c.subject)
        assertTrue(c.text.contains("안녕"))
        val c2 = MailComposer.compose(MailKind.INSTANT,
            listOf(notif(postedAt = now), notif(text = "또", postedAt = now + 1)), SEOUL, now)
        assertEquals("[카카오톡] 홍길동 · 2건", c2.subject)
    }

    @Test fun `제목이 비면 본문 첫 줄을 쓰고 개행은 제거`() {
        val c = MailComposer.compose(MailKind.INSTANT, listOf(notif(title = "", text = "첫줄\n둘째줄", postedAt = now)), SEOUL, now)
        assertEquals("[카카오톡] 첫줄", c.subject)
        val c2 = MailComposer.compose(MailKind.INSTANT, listOf(notif(title = "a\r\nb", postedAt = now)), SEOUL, now)
        assertFalse(c2.subject.contains("\n") || c2.subject.contains("\r"))
    }

    @Test fun `모음 제목은 앱별 개수 요약`() {
        val items = listOf(
            notif(label = "카카오톡", postedAt = 1), notif(label = "카카오톡", postedAt = 2),
            notif(pkg = "b", label = "쿠팡", postedAt = 3),
        )
        val c = MailComposer.compose(MailKind.BATCH, items, SEOUL, now)
        assertEquals("[알림 모음] 3건 · 카카오톡 2, 쿠팡 1", c.subject)
        val d = MailComposer.compose(MailKind.DAILY, items, SEOUL, now)
        assertTrue(d.subject.startsWith("[오늘의 알림] 10월 5일 · 3건"))
    }

    @Test fun `HTML 은 이스케이프된다`() {
        val c = MailComposer.compose(MailKind.INSTANT, listOf(notif(title = "<b>x</b>", text = "a & b", postedAt = now)), SEOUL, now)
        assertTrue(c.html.contains("&lt;b&gt;x&lt;/b&gt;"))
        assertTrue(c.html.contains("a &amp; b"))
        assertFalse(c.html.contains("<b>x</b>"))
    }

    @Test fun `앱이 많으면 외 N개 앱`() {
        val items = (1..5).map { notif(pkg = "p$it", label = "앱$it", postedAt = it.toLong()) }
        assertTrue(MailComposer.compose(MailKind.BATCH, items, SEOUL, now).subject.endsWith("외 2개 앱"))
    }
}

class LoopGuardTest {
    private val gmail = "com.google.android.gm"

    @Test fun `내가 보낸 메일의 Gmail 알림은 되먹임으로 판단`() {
        val n = notif(pkg = gmail, label = "Gmail", title = "Alram Mail", text = "[카카오톡] 홍길동\n안녕")
        assertTrue(LoopGuard.isEcho(n, emptyList()))
    }

    @Test fun `발신자 이름이 달라 보여도 최근 보낸 제목이 있으면 되먹임`() {
        val n = notif(pkg = gmail, label = "Gmail", title = "나", text = "[카카오톡] 홍길동 · 2건")
        assertTrue(LoopGuard.isEcho(n, listOf("[카카오톡] 홍길동 · 2건")))
    }

    @Test fun `긴 제목은 앞부분만 맞아도 되먹임`() {
        val subject = "[알림 모음] 12건 · 카카오톡 5, 쿠팡 3, 배민 2 외 2개 앱"
        val n = notif(pkg = gmail, label = "Gmail", title = "나", text = subject.take(30) + "…")
        assertTrue(LoopGuard.isEcho(n, listOf(subject)))
    }

    @Test fun `평범한 알림은 통과`() {
        val n = notif(title = "홍길동", text = "내일 몇 시에 봐?")
        assertFalse(LoopGuard.isEcho(n, listOf("[카카오톡] 홍길동", "[알림 모음] 3건 · 카카오톡 3")))
        // 너무 짧은 제목은 오탐 위험이 있어 비교하지 않는다.
        assertFalse(LoopGuard.isEcho(notif(text = "ok"), listOf("ok")))
    }

    @Test fun `메일 앱 목록에 Gmail 포함`() {
        assertTrue(gmail in LoopGuard.MAIL_APPS)
        assertFalse("com.kakao.talk" in LoopGuard.MAIL_APPS)
    }
}
