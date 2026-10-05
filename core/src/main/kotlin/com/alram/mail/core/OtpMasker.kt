package com.alram.mail.core

object OtpMasker {
    private val hint = Regex(
        "인증|코드|비밀번호|승인|확인번호|일회용|otp|code|verification|verify|passcode|security|one-time",
        RegexOption.IGNORE_CASE,
    )
    private val digits = Regex("(?<![\\d-])\\d{4,8}(?![\\d-])")

    /** 인증번호로 보이는 문구가 있을 때만 4~8자리 숫자를 가린다. */
    fun mask(text: String): String =
        if (hint.containsMatchIn(text)) digits.replace(text) { "•".repeat(it.value.length) } else text
}

object Fingerprint {
    /** 중복 알림 판별용 64비트 FNV-1a 해시. */
    fun of(packageName: String, title: String, text: String): String {
        var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        for (c in "$packageName|${title.trim()}|${text.trim()}") {
            h = (h xor c.code.toLong()) * 0x100000001b3L
        }
        return java.lang.Long.toHexString(h)
    }
}
