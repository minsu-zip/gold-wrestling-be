#!/usr/bin/env bash
# 빈 Ubuntu 24.04 EC2를 배포 가능한 상태로 만드는 멱등 초기 세팅 스크립트 (INFRA-06).
# 근거: docs/decisions.md D-18(서버에는 git 없이 compose·Caddyfile·.env·backups/만 둔다),
#       .planning/REQUIREMENTS.md INFRA-06(Docker+compose 플러그인·스왑 2GB·타임존·배포 디렉토리).
#
# 실행 방법 (레포 클론 없이 파일 하나만 파이프로 전달):
#   ssh ubuntu@<host> 'bash -s' < deploy/server-setup.sh
#
# 두 번 실행해도 안전하다 — 블록마다 "이미 되어 있으면 스킵" 가드를 두었다(아래 각 블록 주석 참조).
# 2회 실행 결과가 같은지는 이 스크립트가 스스로 증명하지 않는다 — 07-06 실서버 배포가 직접 두 번
# 돌려 비교한다. 종료 요약 출력에 실행 시각처럼 매번 달라지는 값을 넣지 않은 것도 그 비교를 위해서다.
set -euo pipefail

# --- 1. Docker Engine + compose 플러그인 ---
# 가드: command -v docker — 이미 설치돼 있으면 apt 저장소 등록·설치를 전부 건너뛴다.
# 공식 절차(docs.docker.com/engine/install/ubuntu)를 그대로 따른다. 코드네임은 하드코딩하지 않고
# 서버의 /etc/os-release에서 동적으로 읽어(VERSION_CODENAME) Ubuntu 버전이 바뀌어도 그대로 쓸 수 있게 한다.
if ! command -v docker &>/dev/null; then
  sudo apt-get update
  sudo apt-get install -y ca-certificates curl
  sudo install -m 0755 -d /etc/apt/keyrings
  sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  sudo chmod a+r /etc/apt/keyrings/docker.asc
  echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
    $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
    sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
  sudo apt-get update
  sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi

# --- 2. 스왑 2GB ---
# 가드 두 개가 별개인 이유: 스왑은 지금 켜져 있지만(swapon --show에 보임) /etc/fstab에 없어서
# 재부팅하면 사라지는 상태가 실재한다. 파일 생성 여부와 fstab 등록 여부는 서로 다른 실패 지점이라
# 하나로 합치면 그 상태를 놓친다.
if ! swapon --show | grep -q '/swapfile'; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
fi
grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab > /dev/null

# --- 3. 타임존 (CLAUDE.md 시간대 규칙: Asia/Seoul 명시) ---
# 별도 가드 없음 — timedatectl set-timezone은 이미 같은 타임존이어도 같은 결과를 내는
# 연산 자체가 멱등인 명령이다(여러 번 실행해도 상태가 달라지지 않는다).
sudo timedatectl set-timezone Asia/Seoul

# --- 4. ubuntu 사용자를 docker 그룹에 가입 ---
# 별도 가드 없음 — usermod -aG도 같은 그룹을 다시 추가해도 결과가 같은 멱등 연산이다.
# 주의: 이 변경은 현재 세션에는 바로 반영되지 않는다 — 재로그인(또는 새 SSH 세션)해야
# docker 명령을 sudo 없이 쓸 수 있다. 아래 종료 메시지에서 다시 안내한다.
sudo usermod -aG docker ubuntu

# --- 5. 배포 디렉토리 (서버에서 버전관리 명령을 쓰지 않는다 — D-18) ---
# backups/는 Phase 9(S3 백업·복구)가 쓸 자리만 미리 만들어 둔다. 이 스크립트는 백업 로직을 구현하지 않는다.
# .env는 없을 때만 빈 파일로 만든다 — 이미 있으면 절대 손대지 않는다(운영 시크릿 소실 방지).
sudo mkdir -p /opt/gold-wrestling/backups
sudo chown -R ubuntu:ubuntu /opt/gold-wrestling
[ -f /opt/gold-wrestling/.env ] || touch /opt/gold-wrestling/.env

# --- 종료 요약 ---
# 07-06이 "2회 실행 결과가 같다"를 판정할 때 비교할 출력이다. 실행 시각 등 매번 달라지는
# 값은 의도적으로 넣지 않는다.
echo "=== docker ==="
docker --version
docker compose version
echo "=== swap ==="
swapon --show
echo "=== timezone ==="
timedatectl | grep 'Time zone'
echo "=== /opt/gold-wrestling ==="
ls -la /opt/gold-wrestling
echo "=== 완료 ==="
echo "docker 그룹 가입은 재로그인 후에 적용됩니다. 'exit' 후 다시 ssh 접속하세요."
