#!/bin/bash

# Java 17 사용 스크립트

echo "=== Java 17 사용 설정 ==="
echo ""

# Java 17 설치 확인
if [ -d "/Library/Java/JavaVirtualMachines/temurin-17.jdk" ]; then
    echo "✓ Java 17이 설치되어 있습니다!"
    export JAVA_HOME=$(/usr/libexec/java_home -v 17)
    echo "JAVA_HOME=$JAVA_HOME"
    echo ""
    echo "현재 Java 버전:"
    java -version
    echo ""
    echo "✓ Java 17로 설정되었습니다."
    echo ""
    echo "이제 다음 명령어로 빌드하세요:"
    echo "  ./gradlew assembleDebug"
else
    echo "✗ Java 17이 설치되어 있지 않습니다."
    echo ""
    echo "Java 17 설치 방법:"
    echo "  1. 터미널에서 다음 명령어 실행:"
    echo "     brew install --cask temurin@17"
    echo ""
    echo "  2. 설치 후 이 스크립트를 다시 실행하거나:"
    echo "     export JAVA_HOME=\$(/usr/libexec/java_home -v 17)"
    echo ""
    echo "  3. 영구적으로 적용하려면 ~/.zshrc에 추가:"
    echo "     export JAVA_HOME=\$(/usr/libexec/java_home -v 17)"
fi


