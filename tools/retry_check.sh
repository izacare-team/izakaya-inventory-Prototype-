#!/usr/bin/env bash
# 사용법: (1) python tools/gemini_stub.py  (2) ./gradlew bootRun --args="--vision.provider=gemini --vision.gemini.api-key=dummy --vision.gemini.base-url=http://127.0.0.1:9999 --server.port=8081"  (3) bash tools/retry_check.sh
# 재시도/타임아웃 동작 검증. 앱(8081)이 스텁(9999)을 Gemini로 바라보게 띄운 상태에서 실행.
set -u
APP=http://localhost:8081
STUB=http://127.0.0.1:9999
J=$(mktemp)
IMG=$(mktemp --suffix=.jpg)

# 1x1 JPEG (내용은 무의미 — 스텁이 응답을 고정하므로 사진 자체는 중요하지 않다)
printf '\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00\xff\xdb\x00C\x00' > "$IMG"
head -c 200 /dev/urandom >> "$IMG"
printf '\xff\xd9' >> "$IMG"

curl -s -c "$J" -X POST "$APP/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"boss","password":"1234"}' -o /dev/null
echo "로그인 완료"
echo

run() {
  local mode=$1 expect=$2
  curl -s -o /dev/null "$STUB/_mode?m=$mode"
  local t0 code t1 elapsed calls
  t0=$(date +%s%3N)
  code=$(curl -s -o /tmp/scanout -w '%{http_code}' -b "$J" -X POST "$APP/api/audits/scan" -F "image=@$IMG")
  t1=$(date +%s%3N)
  elapsed=$(( t1 - t0 ))
  calls=$(curl -s "$STUB/_stats" | python -c 'import sys,json;print(json.load(sys.stdin)["count"])')
  printf '  %-10s HTTP %-3s  스텁호출 %s회  소요 %sms   (기대: %s)\n' "$mode" "$code" "$calls" "$elapsed" "$expect"
}

echo "=== 시나리오별 결과 ==="
run retry     "201 / 2회 — 재시도로 성공"
run fail4xx   "실패 / 1회 — 4xx는 재시도 안 함"
run always503 "실패 / 2회 — 1회 재시도 후 포기"
run hang      "실패 / 1회 — 15초 읽기 타임아웃"

rm -f "$J" "$IMG"
