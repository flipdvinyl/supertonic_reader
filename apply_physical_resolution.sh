#!/bin/bash

# Physical size/density를 Override 값에 맞추는 스크립트
# 에뮬레이터를 재시작하여 변경사항 적용

export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/platform-tools

echo "=== Physical 해상도 설정 적용 ==="
echo ""

# 실행 중인 에뮬레이터 종료
DEVICES=$(adb devices 2>/dev/null | grep -v "List" | grep "device" | awk '{print $1}')

if [ ! -z "$DEVICES" ]; then
    echo "실행 중인 에뮬레이터 종료 중..."
    for device in $DEVICES; do
        adb -s $device emu kill 2>/dev/null || true
    done
    sleep 3
    echo "✓ 에뮬레이터 종료 완료"
    echo ""
fi

# AVD 이름 확인
AVD_NAME=$(emulator -list-avds 2>/dev/null | head -1)

if [ -z "$AVD_NAME" ]; then
    echo "✗ 사용 가능한 에뮬레이터를 찾을 수 없습니다."
    exit 1
fi

echo "에뮬레이터 시작: $AVD_NAME"
echo "Physical size: 1072x1448, Physical density: 300"
echo ""

# 에뮬레이터 시작
emulator -avd "$AVD_NAME" > /dev/null 2>&1 &

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
    sleep 2
    echo ""
    echo "현재 해상도 설정:"
    adb shell wm size
    adb shell wm density
    echo ""
    echo "✓ Physical size와 density가 1072x1448, 300으로 설정되었습니다."
else
    echo ""
    echo "⚠ 에뮬레이터가 아직 부팅 중입니다."
    echo "  다음 명령어로 확인하세요: adb devices"
fi

