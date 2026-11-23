# 키즈링고 (KidsLingo)

초등 1학년 아이가 알파벳부터 첫 영어 문장까지 놀이처럼 익히는 학습 앱입니다.
`www/index.html` 한 파일이 앱의 전부이고, 같은 화면을 두 가지 방법으로 쓸 수 있습니다.

| 쓰는 방법 | 여는 법 | 음성 |
| --- | --- | --- |
| 안드로이드 앱 | `kids_lingo.apk` 설치 | 기기 안의 안드로이드 TTS 엔진(영어·한국어) |
| 웹 브라우저 | `www/index.html` 열기 | Web Speech API(`speechSynthesis`) |

## 무엇이 들어 있나

- **10단계 학습 맵** — 알파벳 대문자/소문자 짝, 첫소리 파닉스, 숫자·색깔, 동물, 음식, 가족·학교, 동작, 3글자 단어 조립, 첫 영어 문장.
- **매번 새 문제** — 스테이지마다 20~26개 항목 풀에서 6문제(객관식·징검다리 짝맞추기·블록 조립)를 즉석에서 만듭니다.
- **음성** — 문제를 열면 영어 발음을 자동으로 읽어 주고, 모든 카드·보기를 눌러 다시 들을 수 있습니다. 한국어 뜻은 "뜻을 한국어로 듣기"로 읽어 줍니다. 속도는 🐢 버튼으로 0.75x → 1.0x → 0.6x 순서로 바뀝니다.
- **진행 저장** — 하트·보석·클리어 횟수는 기기(WebView `localStorage`)에 저장됩니다.
- **인터넷 없이 동작** — Tailwind, 폰트(Jua·Nunito), 폭죽 효과까지 `www/` 안에 들어 있습니다.

## 안드로이드 앱 빌드

Gradle 없이 JDK 17 + build-tools 34 로 조립합니다. 웹 자산(`www/`)은 빌드할 때
`assets/www` 로 복사되므로 따로 복사해 둘 필요가 없습니다.

```bash
export JAVA_HOME=/workspace/work/apk-build/jdk-17.0.21.1+2
export BUILD_TOOLS=/workspace/work/apk-build/bt/android-14
export ANDROID_JAR=/workspace/work/apk-build/plat/android-34/android.jar
export KS_PASS='<키 비밀번호>'
android/tools/build-apk.sh /workspace/downloads/kids_lingo.apk
```

- 패키지 `com.kidslingo.app`, 앱 이름 `키즈링고`, minSdk 24 / targetSdk 34.
- 서명: `apksigner` v1·v2·v3. 키 파일은 이 저장소에 넣지 않습니다(`.gitignore`). 같은 키로 서명해야
  새 버전을 덮어 설치할 수 있으므로 키를 잃지 않게 보관하세요.
- 아이콘은 `python3 android/tools/make_icons.py` 로 `icon-source.png` 에서 다시 만듭니다.

## 음성이 안 나올 때

APK 는 기기의 안드로이드 TTS 엔진을 씁니다. 기기에 영어 음성 데이터가 없으면
앱 아래쪽에 안내 배너와 "음성 설정 열기" 버튼이 나타납니다.
`설정 → 언어 및 입력 → 텍스트 음성 변환` 에서 **Google 음성 서비스**를 설치하고
영어(미국) 음성 데이터를 내려받으면 됩니다.
