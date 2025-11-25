#!/bin/bash

# 에뮬레이터 재시작 및 해상도 설정 스크립트
# 검정 여백 제거를 위해 에뮬레이터를 재시작합니다

export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/platform-tools

echo "=== 에뮬레이터 재시작 및 해상도 설정 ==="
echo ""

# 실행 중인 에뮬레이터 확인
DEVICES=$(adb devices | grep -v "List" | grep "device" | awk '{print $1}')

if [ ! -z "$DEVICES" ]; then
    echo "실행 중인 에뮬레이터 종료 중..."
    for device in $DEVICES; do
        echo "기기 $device 종료 중..."
        adb -s $device emu kill 2>/dev/null || pkill -f "emulator.*$device" 2>/dev/null || true
    done
    echo "기다리는 중..."
    sleep 3
fi

# AVD 목록 확인
AVD_NAME=$(emulator -list-avds 2>/dev/null | head -1)

if [ -z "$AVD_NAME" ]; then
    echo "✗ 사용 가능한 에뮬레이터를 찾을 수 없습니다."
    exit 1
fi

echo "에뮬레이터 시작: $AVD_NAME"
echo "해상도: 1072 x 1448, DPI: 300"
echo ""

# 에뮬레이터를 백그라운드로 시작
emulator -avd "$AVD_NAME" -scale 1.0 > /dev/null 2>&1 &

echo "에뮬레이터 부팅 중... (약 30초~1분 소요)"
echo ""

# 부팅 완료 대기
timeout=90
elapsed=0
while [ $elapsed -lt $timeout ]; do
    if adb devices 2>/dev/null | grep -q "device$"; then
        echo ""
        echo "✓ 에뮬레이터 부팅 완료"
        break
    fi
    sleep 2
    elapsed=$((elapsed + 2))
    if [ $((elapsed % 10)) -eq 0 ]; then
        echo -n " ${elapsed}초..."
    else
        echo -n "."
    fi
done
echo ""

if adb devices 2>/dev/null | grep -q "device$"; then
    # 해상도 및 DPI 설정
    echo ""
    echo "해상도 설정 중..."
    adb wait-for-device shell wm size 1072x1448 2>/dev/null
    adb shell wm density 300 2>/dev/null
    
    # 화면 회전 고정 (세로 모드)
    adb shell settings put system user_rotation 0 2>/dev/null
    
    echo "✓ 해상도 설정 완료: 1072x1448, DPI: 300"
    echo ""
    echo "현재 설정:"
    adb shell wm size
    adb shell wm density
    echo ""
    echo "검정 여백이 계속 나타나면:"
    echo "1. Android Studio의 에뮬레이터 창에서 View > Zoom > Fit to Window"
    echo "2. 또는 에뮬레이터 도구바에서 확대/축소 옵션 조정"
else
    echo ""
    echo "⚠ 에뮬레이터가 아직 부팅 중입니다."
    echo "  다음 명령어로 확인하세요: adb devices"
fi

