#!/bin/bash

# Supertonic 모델 파일 다운로드 스크립트

echo "=== Supertonic 모델 파일 다운로드 ==="
echo ""

ASSETS_DIR="app/src/main/assets"
ONNX_DIR="$ASSETS_DIR/onnx"
VOICE_DIR="$ASSETS_DIR/voice_styles"

# 디렉토리 생성
mkdir -p "$ONNX_DIR"
mkdir -p "$VOICE_DIR"

echo "모델 파일 다운로드 중..."
echo ""

# Hugging Face에서 모델 다운로드 시도
# 참고: 실제 Hugging Face API를 사용하거나 직접 다운로드 링크가 필요합니다

echo "Hugging Face에서 모델 파일을 다운로드하려면:"
echo "1. https://huggingface.co/spaces/Supertone/supertonic 방문"
echo "2. 모델 파일들을 다운로드"
echo "3. 다음 위치에 배치:"
echo "   - $ONNX_DIR/"
echo "   - $VOICE_DIR/"
echo ""

# 대안: GitHub에서 assets 폴더 확인
echo "또는 GitHub 저장소에서 확인:"
echo "git clone https://github.com/supertone-inc/supertonic.git"
echo "cp -r supertonic/assets/* $ASSETS_DIR/"
echo ""

# 최소한의 더미 JSON 파일 생성 (테스트용)
if [ ! -f "$ONNX_DIR/tts.json" ]; then
    echo "테스트용 더미 tts.json 생성 중..."
    cat > "$ONNX_DIR/tts.json" << 'JSON'
{
  "ae": {
    "sample_rate": 24000,
    "base_chunk_size": 240
  },
  "ttl": {
    "chunk_compress_factor": 4,
    "latent_dim": 64
  }
}
JSON
    echo "✓ 더미 tts.json 생성 완료"
fi

if [ ! -f "$ONNX_DIR/unicode_indexer.json" ]; then
    echo "테스트용 더미 unicode_indexer.json 생성 중..."
    echo "[0,1,2,3,4,5,6,7,8,9]" > "$ONNX_DIR/unicode_indexer.json"
    echo "✓ 더미 unicode_indexer.json 생성 완료"
fi

echo ""
echo "⚠️  실제 ONNX 모델 파일(.onnx)은 수동으로 다운로드해야 합니다."
echo ""
echo "다운로드 위치:"
echo "  - $ONNX_DIR/duration_predictor.onnx"
echo "  - $ONNX_DIR/text_encoder.onnx"
echo "  - $ONNX_DIR/vector_estimator.onnx"
echo "  - $ONNX_DIR/vocoder.onnx"
echo ""
echo "목소리 스타일 파일:"
echo "  - $VOICE_DIR/M1.json"
echo "  - $VOICE_DIR/F1.json"
echo "  - $VOICE_DIR/M2.json"
echo "  - $VOICE_DIR/F2.json"


