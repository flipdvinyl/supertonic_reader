#!/bin/bash

# Android 환경 변수 설정 스크립트
# 이 스크립트를 실행하거나 ~/.zshrc에 추가하세요

export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/tools
export PATH=$PATH:$ANDROID_HOME/tools/bin

echo "✓ Android 환경 변수 설정 완료"
echo "  ANDROID_HOME=$ANDROID_HOME"
echo ""
echo "현재 세션에서만 적용됩니다."
echo "영구적으로 적용하려면 ~/.zshrc에 다음을 추가하세요:"
echo ""
echo "export ANDROID_HOME=\$HOME/Library/Android/sdk"
echo "export PATH=\$PATH:\$ANDROID_HOME/emulator"
echo "export PATH=\$PATH:\$ANDROID_HOME/platform-tools"
echo "export PATH=\$PATH:\$ANDROID_HOME/tools"
echo "export PATH=\$PATH:\$ANDROID_HOME/tools/bin"


