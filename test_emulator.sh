#!/bin/bash

# Android 에뮬레이터 테스트 스크립트

set -e

PROJECT_DIR="/Users/d/android_ebook"
cd "$PROJECT_DIR"

# Java 17 사용 설정
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo "")
if [ -z "$JAVA_HOME" ]; then
    echo "⚠ Java 17을 찾을 수 없습니다. Java 17을 설치하세요: brew install --cask temurin@17"
    echo "   현재 Java 버전:"
    java -version 2>&1 | head -1
fi

echo "=== Android Ebook Reader 테스트 스크립트 ==="
echo ""

# 1. Android SDK 확인 및 환경 변수 설정
if [ -z "$ANDROID_HOME" ]; then
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
        export PATH=$PATH:$ANDROID_HOME/emulator
        export PATH=$PATH:$ANDROID_HOME/platform-tools
        export PATH=$PATH:$ANDROID_HOME/tools
        export PATH=$PATH:$ANDROID_HOME/tools/bin
        echo "✓ Android SDK 경로 설정: $ANDROID_HOME"
    else
        echo "✗ Android SDK를 찾을 수 없습니다."
        echo "  Android Studio를 설치하고 SETUP.md를 참고하세요."
        exit 1
    fi
else
    echo "✓ Android SDK: $ANDROID_HOME"
    # PATH에 추가 (이미 설정되어 있어도 안전하게)
    export PATH=$PATH:$ANDROID_HOME/emulator
    export PATH=$PATH:$ANDROID_HOME/platform-tools
fi

# 2. ADB 확인
if ! command -v adb &> /dev/null; then
    echo "✗ ADB를 찾을 수 없습니다."
    exit 1
fi
echo "✓ ADB 확인됨"

# 3. 에뮬레이터 확인
if ! command -v emulator &> /dev/null; then
    echo "✗ 에뮬레이터를 찾을 수 없습니다."
    exit 1
fi

# 4. 사용 가능한 에뮬레이터 목록
echo ""
echo "사용 가능한 에뮬레이터:"
emulator -list-avds || echo "  에뮬레이터가 없습니다. Android Studio에서 생성하세요."

# 5. 실행 중인 에뮬레이터 확인
echo ""
echo "실행 중인 기기:"
adb devices

# 6. Gradle wrapper 확인
if [ ! -f "gradlew" ]; then
    echo ""
    echo "✗ Gradle wrapper를 찾을 수 없습니다."
    echo "  gradlew 파일이 프로젝트 루트에 있어야 합니다."
    exit 1
fi

# Gradle wrapper 실행 권한 확인
if [ ! -x "gradlew" ]; then
    chmod +x gradlew
    echo "✓ Gradle wrapper 실행 권한 부여"
fi

# 7. 빌드
echo ""
echo "APK 빌드 중..."
./gradlew assembleDebug

# 8. APK 설치
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo ""
    echo "APK 설치 중..."
    adb install -r "$APK_PATH"
    
    echo ""
    echo "앱 실행 중..."
    adb shell am start -n com.supertone.ebook/.MainActivity
    
    echo ""
    echo "✓ 완료! 에뮬레이터에서 앱을 확인하세요."
else
    echo "✗ APK 파일을 찾을 수 없습니다."
    exit 1
fi

