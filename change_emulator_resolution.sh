#!/bin/bash

# Android 에뮬레이터 해상도 변경 스크립트
# 해상도: 1072 x 1448
# DPI: 300

export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools

echo "=== Android 에뮬레이터 해상도 변경 ==="
echo ""

# 실행 중인 에뮬레이터 확인
DEVICES=$(adb devices | grep -v "List" | grep "device" | awk '{print $1}')

if [ -z "$DEVICES" ]; then
    echo "✗ 실행 중인 에뮬레이터를 찾을 수 없습니다."
    echo ""
    echo "에뮬레이터를 먼저 시작하세요:"
    echo "  ./start_emulator.sh"
    echo ""
    echo "또는 Android Studio에서:"
    echo "  Tools > Device Manager > 에뮬레이터 시작"
    exit 1
fi

echo "발견된 기기:"
adb devices | grep "device"

echo ""
echo "해상도 변경 중..."
echo "  크기: 1072 x 1448"
echo "  DPI: 300"
echo ""

# 각 기기에 대해 해상도 변경
for device in $DEVICES; do
    echo "기기 $device:"
    
    # 해상도 변경
    adb -s $device shell wm size 1072x1448
    if [ $? -eq 0 ]; then
        echo "  ✓ 해상도 변경 완료: 1072x1448"
    else
        echo "  ✗ 해상도 변경 실패"
    fi
    
    # DPI 변경
    adb -s $device shell wm density 300
    if [ $? -eq 0 ]; then
        echo "  ✓ DPI 변경 완료: 300"
    else
        echo "  ✗ DPI 변경 실패"
    fi
    
    echo ""
done

# 현재 설정 확인
echo "현재 해상도 설정:"
adb shell wm size
adb shell wm density

echo ""
echo "변경 사항을 적용하려면 에뮬레이터를 재시작하는 것을 권장합니다."

