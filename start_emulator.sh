#!/bin/bash

# 에뮬레이터 시작 스크립트

export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/platform-tools

echo "=== Android 에뮬레이터 시작 ==="
echo ""

# 사용 가능한 AVD 목록
echo "사용 가능한 에뮬레이터:"
emulator -list-avds

echo ""
read -p "에뮬레이터 이름을 입력하세요 (또는 Enter로 첫 번째 사용): " avd_name

if [ -z "$avd_name" ]; then
    # 첫 번째 AVD 사용
    avd_name=$(emulator -list-avds | head -1)
    if [ -z "$avd_name" ]; then
        echo "✗ 사용 가능한 에뮬레이터가 없습니다."
        echo "  Android Studio에서 에뮬레이터를 생성하세요:"
        echo "  Tools > Device Manager > Create Device"
        exit 1
    fi
fi

echo ""
echo "에뮬레이터 시작 중: $avd_name"
echo "에뮬레이터가 완전히 부팅될 때까지 기다려주세요..."
echo ""

# 에뮬레이터를 백그라운드로 시작 (해상도 1072x1448, DPI 300)
# -scale 옵션으로 창 크기 자동 조정
emulator -avd "$avd_name" -scale 1.0 > /dev/null 2>&1 &

echo "에뮬레이터가 시작되었습니다."
echo "부팅이 완료될 때까지 기다려주세요 (약 30초~1분)..."
echo ""

# 부팅 완료 대기
echo "부팅 완료 대기 중..."
timeout=60
elapsed=0
while [ $elapsed -lt $timeout ]; do
    if adb devices 2>/dev/null | grep -q "device$"; then
        echo "✓ 에뮬레이터 부팅 완료"
        break
    fi
    sleep 2
    elapsed=$((elapsed + 2))
    echo -n "."
done
echo ""

if adb devices 2>/dev/null | grep -q "device$"; then
    # 해상도 및 DPI 설정
    echo "해상도 설정 중 (1072x1448, DPI 300)..."
    adb wait-for-device shell wm size 1072x1448 2>/dev/null
    adb shell wm density 300 2>/dev/null
    echo "✓ 해상도 설정 완료"
    echo ""
    echo "현재 해상도:"
    adb shell wm size
    adb shell wm density
    echo ""
    echo "앱 설치 명령어:"
    echo "  adb install app/build/outputs/apk/debug/app-debug.apk"
    echo "  adb shell am start -n com.supertone.ebook/.MainActivity"
else
    echo "⚠ 에뮬레이터가 아직 부팅 중입니다."
    echo "  다음 명령어로 확인하세요: adb devices"
fi


