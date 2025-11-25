#!/bin/bash

# 필수 요구사항 확인 스크립트

echo "=== Android 개발 환경 요구사항 확인 ==="
echo ""

# 1. Java 확인
echo "1. Java 확인:"
if command -v java &> /dev/null; then
    java -version 2>&1 | head -1
    echo "   ✓ Java 설치됨"
else
    echo "   ✗ Java가 설치되어 있지 않습니다."
    echo "     설치: brew install --cask temurin"
    MISSING=true
fi

echo ""

# 2. Android SDK 확인
echo "2. Android SDK 확인:"
if [ -n "$ANDROID_HOME" ]; then
    echo "   ✓ ANDROID_HOME: $ANDROID_HOME"
elif [ -d "$HOME/Library/Android/sdk" ]; then
    echo "   ✓ Android SDK 발견: $HOME/Library/Android/sdk"
    echo "   ⚠ 환경 변수 ANDROID_HOME 설정 필요"
else
    echo "   ✗ Android SDK를 찾을 수 없습니다."
    echo "     Android Studio를 설치하세요: brew install --cask android-studio"
    MISSING=true
fi

echo ""

# 3. ADB 확인
echo "3. ADB 확인:"
if command -v adb &> /dev/null; then
    adb version | head -1
    echo "   ✓ ADB 설치됨"
else
    echo "   ✗ ADB를 찾을 수 없습니다."
    echo "     Android SDK 설치 필요"
    MISSING=true
fi

echo ""

# 4. 에뮬레이터 확인
echo "4. 에뮬레이터 확인:"
if command -v emulator &> /dev/null; then
    echo "   ✓ 에뮬레이터 명령어 사용 가능"
    echo "   사용 가능한 AVD:"
    emulator -list-avds 2>/dev/null || echo "     (없음 - Android Studio에서 생성 필요)"
else
    echo "   ✗ 에뮬레이터를 찾을 수 없습니다."
    echo "     Android SDK 설치 필요"
    MISSING=true
fi

echo ""

# 5. Gradle wrapper 확인
echo "5. Gradle wrapper 확인:"
if [ -f "gradlew" ]; then
    echo "   ✓ Gradle wrapper 발견"
    if [ -x "gradlew" ]; then
        echo "   ✓ 실행 권한 있음"
    else
        echo "   ⚠ 실행 권한 없음 (자동 수정됨)"
        chmod +x gradlew
    fi
else
    echo "   ✗ Gradle wrapper를 찾을 수 없습니다."
    MISSING=true
fi

echo ""

# 결과 요약
if [ "$MISSING" = true ]; then
    echo "=== 요약 ==="
    echo "일부 요구사항이 누락되었습니다."
    echo ""
    echo "빠른 설치:"
    echo "  1. Java: brew install --cask temurin"
    echo "  2. Android Studio: brew install --cask android-studio"
    echo ""
    echo "설치 후:"
    echo "  - Android Studio 실행"
    echo "  - SDK Manager에서 Android SDK 설치"
    echo "  - Device Manager에서 에뮬레이터 생성"
    echo "  - 환경 변수 설정 (QUICK_START.md 참고)"
    exit 1
else
    echo "=== 요약 ==="
    echo "✓ 모든 필수 요구사항이 충족되었습니다!"
    echo ""
    echo "다음 단계:"
    echo "  ./test_emulator.sh  # 에뮬레이터에서 앱 실행"
    exit 0
fi


