#!/usr/bin/env bash
# 로컬에서 전체 시스템(postgres, backend :8080·:8081, admin-web :5173)을 docker compose로 띄우고 접속 주소를 알려 준다.
#
#   scripts/local-up.sh            # .env가 없으면 키를 새로 만들어 .env를 만들고 띄운다. 있으면 그대로 쓴다
#   scripts/local-up.sh --fresh    # DB 볼륨을 지우고(데이터 모두 삭제) 새 DB로 띄운다. .env는 그대로 쓴다
#
# 종료: docker compose down  (DB까지 지우려면 docker compose down -v)
set -euo pipefail
cd "$(dirname "$0")/.."

fresh=false
for arg in "$@"; do
  case "$arg" in
    --fresh) fresh=true ;;
    -h|--help) sed -n '2,7p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "알 수 없는 옵션: $arg (--fresh, --help)" >&2; exit 2 ;;
  esac
done

if ! command -v docker >/dev/null 2>&1; then
  echo "docker가 없습니다. Docker Desktop(또는 Docker Engine + Compose v2)을 설치한 뒤 다시 실행하세요." >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "Docker가 실행 중이 아닙니다. Docker Desktop을 켠 뒤 다시 실행하세요." >&2
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  echo "docker compose(v2)가 없습니다. Docker Desktop을 최신으로 올리세요." >&2
  exit 1
fi

# docker-compose.yml이 프로젝트 이름(lead-magnet-crm)을 고정하므로 DB 볼륨은 어느 폴더에서 띄워도 같은 것을 쓴다.
db_volume="lead-magnet-crm_postgres-data"
db_volume_exists=false
if docker volume inspect "$db_volume" >/dev/null 2>&1; then
  db_volume_exists=true
fi

if [[ -f .env ]]; then
  echo "• 기존 .env를 씁니다. (키를 바꾸면 기존 신청자 정보를 읽지 못해 기동이 멈추므로 .env를 다시 만들지 않습니다)"
else
  if [[ "$db_volume_exists" == true && "$fresh" != true ]]; then
    cat >&2 <<EOM
이전에 띄운 DB(docker 볼륨 $db_volume)가 남아 있는데 이 폴더에는 .env가 없습니다.
새로 만든 키로는 그 DB의 신청자 정보를 읽을 수 없어 backend가 기동을 멈춥니다.

  - 처음부터 새로 시작하려면(이전 데이터 삭제):  scripts/local-up.sh --fresh
  - 이전 데이터를 이어 쓰려면: 그때 쓰던 .env를 이 폴더에 복사한 뒤 다시 실행
EOM
    exit 1
  fi
  # README와 같은 방법: 서명 키, 개인정보 암호화 키 두 개, 폼 토큰 키를 서로 다른 값으로 새로 만든다
  sed -e "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48)|" \
      -e "s|^LEAD_ENC_KEY=.*|LEAD_ENC_KEY=$(openssl rand -base64 32)|" \
      -e "s|^LEAD_HMAC_KEY=.*|LEAD_HMAC_KEY=$(openssl rand -base64 32)|" \
      -e "s|^FORM_TOKEN_KEY=.*|FORM_TOKEN_KEY=$(openssl rand -base64 32)|" \
      -e "s|^DEMO_DATA=.*|DEMO_DATA=true|" .env.example > .env
  echo "• .env를 새로 만들었습니다(키 4개 생성, 예시 데이터 켬 DEMO_DATA=true)."
fi

env_value() { sed -n "s/^$1=//p" .env | tail -1; }
admin_web_port="$(env_value ADMIN_WEB_PORT)"; admin_web_port="${admin_web_port:-5173}"
admin_port="$(env_value ADMIN_PORT)"; admin_port="${admin_port:-8080}"
demo_data="$(env_value DEMO_DATA)"
public_port="$(env_value PUBLIC_PORT)"; public_port="${public_port:-8081}"

if [[ "$fresh" == true ]]; then
  echo "• --fresh: 컨테이너와 DB 볼륨을 지웁니다."
  docker compose down -v --remove-orphans
fi

echo "• 이미지를 빌드하고 띄웁니다. 처음에는 몇 분 걸립니다."
if ! docker compose up --build -d; then
  echo >&2
  echo "기동에 실패했습니다. backend 로그 마지막 부분:" >&2
  docker compose logs backend --tail 40 >&2 || true
  if docker compose logs backend 2>/dev/null | grep -q "기존 데이터를 암호화한 키와 다릅니다\|중복 판정 해시를 만든 키와 다릅니다"; then
    echo >&2
    echo "원인: .env의 키가 DB를 만들 때의 키와 다릅니다. 이전 .env를 되돌리거나, 데이터를 지우고 새로 시작하려면 scripts/local-up.sh --fresh" >&2
  fi
  exit 1
fi

echo -n "• backend와 관리자 화면이 준비되기를 기다립니다"
ready=false
for _ in $(seq 1 90); do
  if curl -fsS "http://localhost:${admin_port}/actuator/health" >/dev/null 2>&1 \
    && curl -fsS "http://localhost:${admin_web_port}/" >/dev/null 2>&1; then
    ready=true
    break
  fi
  echo -n "."
  sleep 4
done
echo

if [[ "$ready" != true ]]; then
  echo "6분 안에 준비되지 않았습니다. 상태와 로그를 확인하세요:" >&2
  echo "  docker compose ps" >&2
  echo "  docker compose logs backend --tail 80" >&2
  exit 1
fi

demo_line=""
if [[ "$demo_data" == true ]]; then
  demo_line="
  예시 데이터가 들어 있습니다(DEMO_DATA=true, 빈 DB에 한 번만). 빈 DB로 시작하려면 .env에서 DEMO_DATA=false로 바꾸고 --fresh"
fi

cat <<EOF

준비됐습니다.

  관리자 화면   http://localhost:${admin_web_port}
                로그인: $(env_value ADMIN_SEED_EMAIL) / 비밀번호는 .env의 ADMIN_SEED_PASSWORD
  API 문서      http://localhost:${admin_port}/swagger-ui.html
  공개 폼       http://localhost:${public_port}/f/{slug}   (관리자 화면에서 폼을 게시한 뒤 열립니다)${demo_line}

  처음이라면: 템플릿 → HTML 업로드(e2e/fixtures/ai-form.html을 써도 됩니다) → 캠페인 → 새 폼 → 게시 → 배포 링크
  종료: docker compose down   (데이터까지 지우려면 docker compose down -v)
EOF
