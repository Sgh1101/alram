package com.alram.mail

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alram.mail.data.AppRuleEntity
import com.alram.mail.data.NotificationEntity
import com.alram.mail.data.Status
import com.alram.mail.data.ThemeMode
import com.alram.mail.service.NotificationCaptureService
import com.alram.mail.ui.AlramNav
import com.alram.mail.ui.ProvideContainer
import com.alram.mail.ui.theme.AlramTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 실제 화면을 렌더링해서 app/screenshots/ 에 PNG 로 저장한다. (디자인 확인용, 비교 검사는 하지 않음)
 * CI 가 이 이미지들을 묶어 릴리스에 screenshots.zip 으로 올린다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi", application = Application::class)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun shot(name: String) {
        settle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    /** Room/DataStore 는 백그라운드에서 값을 내보내므로 잠깐씩 기다렸다가 화면을 안정시킨다. */
    private fun settle() {
        repeat(10) {
            Thread.sleep(80)
            compose.waitForIdle()
        }
    }

    private fun click(text: String) {
        compose.onNodeWithText(text).performClick()
        settle()
    }

    private fun scrollClick(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
        settle()
    }

    private fun back() {
        compose.onNodeWithContentDescription("뒤로").performClick()
        settle()
    }

    private fun seed(): AppContainer {
        // 필수 설정을 모두 마친 상태로 만든다.
        Settings.Secure.putString(
            app.contentResolver,
            "enabled_notification_listeners",
            ComponentName(app, NotificationCaptureService::class.java).flattenToString(),
        )
        shadowOf(app.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(app.packageName, true)
        app.getSharedPreferences("secret", Context.MODE_PRIVATE).edit().putString("gmail_app_password", "x").commit()

        val c = AppContainer(app)
        val now = System.currentTimeMillis()
        val min = 60_000L
        runBlocking {
            c.settings.update {
                it.copy(
                    gmailAddress = "slgdj1228@gmail.com",
                    setupSeen = true,
                    theme = ThemeMode.LIGHT,
                    core = it.core.copy(recipients = listOf("joeunchan1228@gmail.com")),
                )
            }
            val rules = c.db.appRules()
            listOf(
                AppRuleEntity("com.kakao.talk", "카카오톡", seenCount = 128, lastSeenAt = now - 2 * min),
                AppRuleEntity("com.coupang.mobile", "쿠팡", seenCount = 14, lastSeenAt = now - 40 * min, mode = "BATCH"),
                AppRuleEntity("viva.republica.toss", "토스", seenCount = 9, lastSeenAt = now - 75 * min, maskOtp = true),
                AppRuleEntity("com.Slack", "Slack", seenCount = 31, lastSeenAt = now - 15 * min, includeKeywords = "긴급\n멘션"),
                AppRuleEntity("com.instagram.android", "Instagram", enabled = false, seenCount = 52, lastSeenAt = now - 5 * min),
                AppRuleEntity("com.google.android.gm", "Gmail", enabled = false, isSystem = true),
                AppRuleEntity("com.sec.android.app.clockpackage", "시계", isSystem = true, mode = "DAILY"),
                AppRuleEntity("com.nhn.android.search", "네이버", bodyMode = "TITLE_ONLY"),
            ).forEach { rules.upsert(it) }

            val n = c.db.notifications()
            fun row(
                pkg: String, label: String, title: String, text: String, ago: Long,
                status: String = Status.SENT, error: String? = null, attempts: Int = 0,
            ) = NotificationEntity(
                sbnKey = "$pkg:$title:$ago", packageName = pkg, appLabel = label, title = title, text = text,
                subText = "", conversation = "", category = "", postedAt = now - ago, createdAt = now - ago,
                mode = "INSTANT",
                // 대기 항목은 미래로 잡아서 테스트 중에 실제 발송을 시도하지 않게 한다.
                dueAt = if (status == Status.PENDING) now + 30 * min else now - ago,
                recipients = "", fingerprint = "$pkg$title$ago", status = status, attempts = attempts,
                lastError = error, sentAt = if (status == Status.SENT) now - ago + 4_000 else null,
            )
            listOf(
                row("com.Slack", "Slack", "#release 김민지", "배포 끝났어요. 로그 한 번만 확인 부탁드려요.", 30 * min),
                row("viva.republica.toss", "토스", "입금 50,000원", "홍길동님이 보냈어요 · 잔액 1,284,300원", 75 * min),
                row("com.coupang.mobile", "쿠팡", "오늘 도착 예정", "주문하신 상품이 배송 출발했어요.", 40 * min, Status.PENDING),
                row(
                    "com.nhn.android.search", "네이버", "로그인 알림", "", 3 * 60 * min, Status.FAILED,
                    error = "인터넷에 연결할 수 없어요.", attempts = 8,
                ),
                row("com.kakao.talk", "카카오톡", "엄마", "저녁 먹고 들어오니?", 9 * min),
                row("com.kakao.talk", "카카오톡", "프로젝트 팀방", "이준호: 내일 10시 회의 자료 올렸어요", 2 * min),
            ).forEach { n.insert(it) }
        }
        return c
    }

    @Test
    fun screens() {
        val c = seed()
        compose.setContent {
            val prefs by c.settings.prefs.collectAsState(initial = null)
            ProvideContainer(c) {
                AlramTheme(prefs?.theme ?: ThemeMode.LIGHT) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        prefs?.let { AlramNav(it) }
                    }
                }
            }
        }
        shot("01_home")
        click("앱"); shot("02_apps")
        click("카카오톡"); shot("03_app_detail")
        back()
        click("기록"); shot("04_history")
        click("발송됨"); shot("04b_history_sent")
        click("설정"); shot("05_settings")
        click("slgdj1228@gmail.com"); shot("06_gmail")
        back()
        click("설정 점검"); shot("07_setup")
        scrollClick("시작하기")
        scrollClick("방해금지 시간"); shot("08_quiet")
        back()
        click("홈")

        runBlocking { c.settings.update { it.copy(theme = ThemeMode.DARK) } }
        shot("11_home_dark")
        click("앱"); shot("12_apps_dark")
        click("설정"); shot("13_settings_dark")
        click("홈")

        // 일시 중지 상태의 홈
        runBlocking {
            c.settings.update {
                it.copy(theme = ThemeMode.LIGHT, core = it.core.copy(pausedUntil = System.currentTimeMillis() + 3_600_000))
            }
        }
        shot("21_home_paused")
    }
}
