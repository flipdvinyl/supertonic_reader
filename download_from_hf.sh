#!/bin/bash

# Hugging Face에서 Supertonic 모델 다운로드

echo "=== Hugging Face에서 Supertonic 모델 다운로드 ==="
echo ""

# Python과 huggingface_hub가 필요합니다
if ! command -v python3 &> /dev/null; then
    echo "Python3가 설치되어 있지 않습니다."
    echo "설치: brew install python3"
    exit 1
fi

# huggingface_hub 설치 확인
if ! python3 -c "import huggingface_hub" 2>/dev/null; then
    echo "huggingface_hub 설치 중..."
    pip3 install huggingface_hub
fi

echo "모델 파일 다운로드 중..."
echo ""

python3 << 'PYTHON'
from huggingface_hub import hf_hub_download
import os

repo_id = "Supertone/supertonic"
base_dir = "app/src/main/assets"

files = [
    "onnx/duration_predictor.onnx",
    "onnx/text_encoder.onnx",
    "onnx/vector_estimator.onnx",
    "onnx/vocoder.onnx",
    "onnx/tts.json",
    "onnx/unicode_indexer.json",
    "voice_styles/M1.json",
    "voice_styles/F1.json",
    "voice_styles/M2.json",
    "voice_styles/F2.json",
]

for file in files:
    try:
        local_path = os.path.join(base_dir, file)
        os.makedirs(os.path.dirname(local_path), exist_ok=True)
        print(f"다운로드 중: {file}")
        hf_hub_download(repo_id=repo_id, filename=file, local_dir=base_dir, local_dir_use_symlinks=False)
        print(f"✓ 완료: {file}")
    except Exception as e:
        print(f"✗ 실패: {file} - {e}")

print("\n다운로드 완료!")
PYTHON

