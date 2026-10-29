#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd -- "$ROOT_DIR"
MONEROPAY_REPO="https://gitlab.com/moneropay/moneropay.git"

# Monero Mainnet or Stagenet
existing_network=mainnet
if [ -f .env ]; then
  existing_network="$(awk -F= '$1 == "MONERO_NETWORK" { print substr($0, index($0, "=") + 1); exit }' .env)"
  existing_network="${existing_network:-mainnet}"
fi
network="${1:-$existing_network}"
if [ "$#" -gt 1 ]; then
  echo "Usage: ./setup.sh [mainnet|stagenet]" >&2
  exit 1
fi
case "$network" in
  mainnet) network_flag="" ;;
  stagenet) network_flag="--stagenet" ;;
  *) echo "Unknown Monero network: $network" >&2; exit 1 ;;
esac

if [ "$network" != "$existing_network" ] && [ -e "$ROOT_DIR/moneropay/data/wallet/wallet" ]; then
  echo "Cannot switch from $existing_network to $network with an existing wallet. Use a separate checkout with fresh wallet and database data for the other network." >&2
  exit 1
fi

set_env() {
  local key="$1" value="$2" temp_file
  temp_file="$(mktemp "$ROOT_DIR/.env.tmp.XXXXXX")"
  if awk -v key="$key" -v value="$value" '
    index($0, key "=") == 1 { print key "=" value; found = 1; next }
    { print }
    END { if (!found) print key "=" value }
  ' .env > "$temp_file" && cat "$temp_file" > .env; then
    rm -f "$temp_file"
  else
    rm -f "$temp_file"
    return 1
  fi
}

gen_secret() {
  openssl rand -hex 32
}

gen_password() {
  openssl rand -hex 16
}

gen_user() {
  openssl rand -hex 6
}

fill_if_empty() {
  local key="$1"
  local value="$2"

  if grep -q "^${key}=$" .env; then
    local temp_file
    temp_file="$(mktemp "$ROOT_DIR/.env.tmp.XXXXXX")"
    if awk -v key="$key" -v value="$value" '
      $0 == key "=" { print key "=" value; next }
      { print }
    ' .env > "$temp_file" && cat "$temp_file" > .env; then
      rm -f "$temp_file"
    else
      rm -f "$temp_file"
      return 1
    fi
  elif ! grep -q "^${key}=" .env; then
    echo "${key}=${value}" >> .env
  fi
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1"
    exit 1
  }
}

require_command git
require_command docker
require_command openssl

if ! docker compose version >/dev/null 2>&1; then
  echo "docker compose is required"
  exit 1
fi

[ -f .env ] || cp .env.example .env

set_env "MONERO_NETWORK" "$network"
set_env "MONERO_WALLET_RPC_NETWORK_FLAG" "$network_flag"
if [ "$#" -gt 0 ]; then
  case "$network" in
    mainnet) daemon_host=xmr-node.cakewallet.com; daemon_port=18081 ;;
    stagenet) daemon_host=node.monerodevs.org; daemon_port=38089 ;;
  esac
  set_env "MONERO_DAEMON_RPC_HOSTNAME" "$daemon_host"
  set_env "MONERO_DAEMON_RPC_PORT" "$daemon_port"
  set_env "MONERO_DAEMON_RPC_ENDPOINT" "http://$daemon_host:$daemon_port/json_rpc"
  set_env "MONERO_DAEMON_RPC_USERNAME" ""
  set_env "MONERO_DAEMON_RPC_PASSWORD" ""
fi

if [ ! -d "$ROOT_DIR/moneropay" ]; then
  git clone "$MONEROPAY_REPO"
fi

# ------------------------
# Backend database
# ------------------------
fill_if_empty "DB_HOST" "backend-db"
fill_if_empty "DB_USER" "$(gen_user)"
fill_if_empty "DB_PASSWORD" "$(gen_password)"
fill_if_empty "DB_NAME" "$(gen_user)"
fill_if_empty "DB_PORT" "5432"

# ------------------------
# MoneroPay database
# ------------------------
fill_if_empty "MONEROPAY_POSTGRES_USERNAME" "$(gen_user)"
fill_if_empty "MONEROPAY_POSTGRES_PASSWORD" "$(gen_password)"
fill_if_empty "MONEROPAY_POSTGRES_DATABASE" "$(gen_user)"

# ------------------------
# Backend admin
# ------------------------
fill_if_empty "ADMIN_NAME" "admin"
fill_if_empty "ADMIN_PASSWORD" "$(gen_password)"

# ------------------------
# Backend JWT secrets
# ------------------------
fill_if_empty "JWT_SECRET" "$(gen_secret)"
fill_if_empty "JWT_REFRESH_SECRET" "$(gen_secret)"
fill_if_empty "JWT_MONEROPAY_SECRET" "$(gen_secret)"

# -----------------------------------------------------------------
# Set User ID for Docker Compose permissions
# -----------------------------------------------------------------
fill_if_empty "CURRENT_UID" "$(id -u)"

# ------------------------
# Wallet RPC login
# ------------------------
fill_if_empty "MONERO_WALLET_RPC_USERNAME" "$(gen_user)"
fill_if_empty "MONERO_WALLET_RPC_PASSWORD" "$(gen_secret)"

echo
echo "Setup complete."
echo "Monero network: $network"
echo
echo "Admin login (stored in .env):"
echo "  name:     $(grep -m1 '^ADMIN_NAME=' .env | cut -d= -f2-)"
echo "  password: $(grep -m1 '^ADMIN_PASSWORD=' .env | cut -d= -f2-)"
echo
echo "Review .env and make sure values are correct for your environment."
echo
echo "Validate your compose file with:"
echo "  docker compose config"
echo
echo "Start everything with:"
echo "  docker compose up -d --build"
