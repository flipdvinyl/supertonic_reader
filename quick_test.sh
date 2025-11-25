#!/bin/bash

# 빠른 테스트 스크립트 - 에뮬레이터 확인 및 앱 설치

export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools

echo "=== 빠른 테스트 ==="
echo ""

# 에뮬레이터 확인
echo "에뮬레이터 상태 확인 중..."
adb devices

echo ""
echo "에뮬레이터가 'device'로 표시되지 않으면:"
echo "  1. ./start_emulator.sh 실행"
echo "  2. 또는 Android Studio에서 에뮬레이터 시작"
echo "  3. 부팅 완료까지 1-2분 대기"
echo ""

# 기기 확인
DEVICE=$(adb devices | grep -w "device" | head -1)
if [ -z "$DEVICE" ]; then
    echo "✗ 에뮬레이터가 실행되지 않았습니다."
    echo ""
    echo "에뮬레이터 시작:"
    echo "  ./start_emulator.sh"
    exit 1
fi

echo "✓ 에뮬레이터 연결됨"
echo ""

# APK 설치
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "APK 빌드 중..."
    export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo "")
    ./gradlew assembleDebug
fi

echo "APK 설치 중..."
adb install -r "$APK_PATH"

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ 설치 완료!"
    echo ""
    echo "앱 실행 중..."
    adb shell am start -n com.supertone.ebook/.MainActivity
    echo ""
    echo "✓ 앱이 실행되었습니다. 에뮬레이터에서 확인하세요!"
else
    echo "✗ 설치 실패"
    exit 1
fi


