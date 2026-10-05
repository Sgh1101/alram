# Alram Mail

폰에 오는 **모든 앱 알림을 수집해서 지정한 Gmail 로 바로(또는 모아서) 보내주는** 안드로이드 앱입니다.

## 주요 기능

- 알림 접근(NotificationListenerService)으로 모든 앱의 알림 수집
- **앱별 on/off**, 앱별 전달 방식(즉시 / N분 묶음 / 하루 요약), 키워드 포함·제외 필터, 앱별 수신 주소
- 연달아 오는 같은 대화 알림을 한 통으로 합쳐 발송, 같은 알림 중복 제거
- 방해금지 시간(요일별, 끝난 뒤 몰아서 보내기 / 버리기)
- 인증번호 가리기, 제목만 보내기
- 인터넷이 끊겨도 DB 에 쌓아 두었다가 복구되면 순서대로 재발송(지수 백오프)
- 하루 최대 메일 수 제한(Gmail 한도 보호), 발송 기록/재전송
- 일시 중지(30분·1시간·3시간·내일 아침까지)

## 구조

```
core/   순수 Kotlin: 규칙 엔진, 스케줄러, 메일 구성, 발송 계획기, OTP 마스킹 (JVM 단위 테스트)
app/    Android: 알림 수집 서비스, Room, SMTP 발송, Jetpack Compose UI
```

알림 수집 → `RuleEngine` 판정 → Room 대기열 → `Dispatcher` 가 발송 시각이 된 항목을
`MailPlanner` 로 묶어 Gmail SMTP(465/SSL)로 전송합니다.

## 빌드

```
./gradlew :core:test          # 로직 테스트 (Android SDK 불필요: -PcoreOnly)
./gradlew :app:assembleDebug  # APK
```

GitHub Actions 가 푸시마다 APK 를 빌드해서 `latest-debug` 릴리스에 올립니다.
(고정 디버그 키로 서명하므로 폰에서 앱을 지우지 않고 덮어써서 업데이트할 수 있습니다.)

## 처음 사용하기

1. Google 계정에서 2단계 인증을 켜고 [앱 비밀번호](https://myaccount.google.com/apppasswords)를 만듭니다.
2. 앱을 설치하고 설정 점검 화면의 4단계(Gmail 연결 / 알림 접근 / 알림 표시 / 배터리 제한 해제)를 따라 합니다.
3. 삼성 폰은 설정 → 배터리 → 백그라운드 사용 제한 → 절전 예외 앱에 Alram Mail 을 추가하세요.
