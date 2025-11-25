#!/bin/bash

# Java 버전 문제 해결 스크립트

echo "=== Java 버전 문제 해결 ==="
echo ""
echo "현재 Java 버전:"
java -version 2>&1 | head -1
echo ""
echo "문제: Java 25는 Gradle 8.4와 호환되지 않습니다."
echo "해결: Java 17 또는 21을 설치하고 사용하세요."
echo ""

# Java 17 설치 확인
if [ -d "/Library/Java/JavaVirtualMachines/temurin-17.jdk" ]; then
    echo "✓ Java 17이 이미 설치되어 있습니다!"
    echo ""
    echo "Java 17을 사용하도록 설정하려면:"
    echo "  export JAVA_HOME=\$(/usr/libexec/java_home -v 17)"
    echo "  또는 ~/.zshrc에 추가하세요"
    exit 0
fi

echo "Java 17 설치 중..."
brew install --cask temurin@17

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ Java 17 설치 완료!"
    echo ""
    echo "Java 17을 사용하도록 설정:"
    echo "  export JAVA_HOME=\$(/usr/libexec/java_home -v 17)"
    echo ""
    echo "현재 세션에서만 적용하려면 위 명령어를 실행하세요."
    echo "영구적으로 적용하려면 ~/.zshrc에 추가하세요."
else
    echo ""
    echo "✗ 설치 실패. 수동으로 설치하세요:"
    echo "  brew install --cask temurin@17"
fi


