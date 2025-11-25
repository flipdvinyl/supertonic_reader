#!/bin/bash

# Android Studio 설치 스크립트 (Homebrew 사용)

echo "=== Android Studio 설치 스크립트 ==="
echo ""
echo "이 스크립트는 Homebrew를 사용하여 Android Studio를 설치합니다."
echo "설치에는 시간이 걸릴 수 있습니다 (수백 MB 다운로드)."
echo ""
read -p "계속하시겠습니까? (y/N): " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "취소되었습니다."
    exit 1
fi

echo ""
echo "Android Studio 설치 중..."
brew install --cask android-studio

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ Android Studio 설치 완료!"
    echo ""
    echo "다음 단계:"
    echo "1. Android Studio를 실행하세요"
    echo "2. SDK Manager에서 Android SDK를 설치하세요"
    echo "3. Device Manager에서 에뮬레이터를 생성하세요"
    echo "4. QUICK_START.md를 참고하여 프로젝트를 열고 실행하세요"
else
    echo ""
    echo "✗ 설치 실패. 수동으로 설치하세요:"
    echo "  https://developer.android.com/studio"
fi


