# Android 개발 환경 설정 가이드

## 필수 요구사항

1. **Android Studio 설치**
   - https://developer.android.com/studio 에서 다운로드
   - macOS용 .dmg 파일 다운로드 및 설치

2. **Android SDK 설치**
   - Android Studio 실행 후 SDK Manager에서 설치
   - 최소 SDK 24, Target SDK 34 필요

3. **Java JDK 설치**
   - Android Studio에 포함되어 있거나
   - 별도로 OpenJDK 11 이상 설치

## 환경 변수 설정

터미널에서 다음 명령어로 환경 변수를 설정하세요:

```bash
# ~/.zshrc 또는 ~/.bash_profile에 추가
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/tools
export PATH=$PATH:$ANDROID_HOME/tools/bin
```

설정 후:
```bash
source ~/.zshrc  # 또는 source ~/.bash_profile
```

## 에뮬레이터 생성

1. Android Studio 실행
2. Tools > Device Manager
3. Create Device 클릭
4. Phone 카테고리에서 원하는 기기 선택 (예: Pixel 5)
5. 시스템 이미지 선택 (API 34 권장)
6. Finish 클릭

## 프로젝트 열기

1. Android Studio 실행
2. Open an Existing Project
3. `/Users/d/android_ebook` 폴더 선택

## 빌드 및 실행

### 방법 1: Android Studio에서
1. 상단의 Run 버튼 클릭
2. 에뮬레이터 선택
3. 앱이 자동으로 설치되고 실행됨

### 방법 2: 명령줄에서
```bash
cd /Users/d/android_ebook

# Gradle wrapper 생성 (처음 한 번만)
./gradlew wrapper

# 에뮬레이터 시작 (별도 터미널)
emulator -avd <에뮬레이터_이름>

# APK 빌드
./gradlew assembleDebug

# 앱 설치 및 실행
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.supertone.ebook/.MainActivity
```

## 주의사항

- Supertonic 모델 파일이 없으면 TTS 기능은 작동하지 않습니다
- 모델 파일은 `app/src/main/assets/supertonic_model.onnx`에 배치해야 합니다
- 실제 Supertonic Java 구현을 확인하여 `SupertonicTTS.java`를 수정해야 합니다


