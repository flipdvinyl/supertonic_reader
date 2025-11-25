# 빠른 시작 가이드

## 1. Android Studio 설치 (필수)

macOS에서:
```bash
# Homebrew로 설치 (권장)
brew install --cask android-studio

# 또는 공식 사이트에서 다운로드
# https://developer.android.com/studio
```

## 2. 환경 변수 설정

`~/.zshrc` 파일에 추가:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/tools
export PATH=$PATH:$ANDROID_HOME/tools/bin
```

적용:
```bash
source ~/.zshrc
```

## 3. 에뮬레이터 생성

Android Studio 실행 후:
1. **Tools > Device Manager**
2. **Create Device** 클릭
3. **Phone > Pixel 5** 선택
4. **System Image**: API 34 (Android 14) 선택
5. **Finish** 클릭

## 4. 프로젝트 열기

Android Studio에서:
1. **Open**
2. `/Users/d/android_ebook` 폴더 선택

## 5. 실행

### 방법 A: Android Studio에서
- 상단 **Run** 버튼 클릭 (▶️)
- 에뮬레이터 자동 시작 및 앱 실행

### 방법 B: 명령줄에서
```bash
cd /Users/d/android_ebook

# 에뮬레이터 시작 (별도 터미널)
emulator -avd <에뮬레이터_이름> &

# 빌드 및 실행
./test_emulator.sh
```

## 문제 해결

### "Android SDK not found"
- Android Studio를 실행하여 SDK 설치 확인
- 환경 변수 `ANDROID_HOME` 확인

### "Gradle not found"
- Android Studio에서 프로젝트를 열면 자동으로 Gradle wrapper 생성
- 또는 `./gradlew wrapper` 실행

### "모델 파일 없음"
- TTS 기능은 작동하지 않지만 UI는 확인 가능
- 실제 사용을 위해서는 Supertonic 모델 파일 필요

## 다음 단계

1. Supertonic 모델 파일을 `app/src/main/assets/`에 배치
2. `SupertonicTTS.java`를 실제 구현에 맞게 수정
3. https://github.com/supertone-inc/supertonic/tree/main/java 참고


