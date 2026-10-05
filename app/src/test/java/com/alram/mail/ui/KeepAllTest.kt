package com.alram.mail.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class KeepAllTest {
    private val wj = '⁠'

    @Test
    fun joinsHangulInsideWordsOnly() {
        assertEquals("합${wj}쳐${wj}요. 한${wj}통", "합쳐요. 한통".keepAll())
        assertEquals("Mail${wj}을 켜${wj}요", "Mail을 켜요".keepAll())
        assertEquals("16${wj}자${wj}리", "16자리".keepAll())
    }

    @Test
    fun leavesOtherTextUntouched() {
        assertEquals("Gmail app", "Gmail app".keepAll())
        assertEquals("가", "가".keepAll())
        // 이모지(서로게이트 쌍)는 건드리지 않는다
        assertEquals("밥 🍚", "밥 🍚".keepAll())
        assertEquals("슬${wj}픔😢끝", "슬픔😢끝".keepAll())
    }

    @Test
    fun removingJoinersGivesOriginal() {
        val s = "인증·코드·OTP 문구가 있는 알림의 4~8자리 숫자를 ••••로 바꿔서 보내요."
        assertEquals(s, s.keepAll().replace("$wj", ""))
    }
}
