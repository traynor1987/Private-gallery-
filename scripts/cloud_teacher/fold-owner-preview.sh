#!/usr/bin/env bash
# Owner-only 2026-11-25 deployment preparation. The container cannot enable paid analysis.
set -euo pipefail
set +x
unset SSLKEYLOGFILE
cd "$(dirname "$0")"

if [[ $EUID -ne 0 || ! -t 0 || ! -t 1 ]]; then
    echo 'Run from your existing Fold Termux SSH session: sudo bash fold-owner-preview.sh "$SSH_CONNECTION"' >&2
    exit 2
fi
if [[ $# -eq 1 ]]; then exec python3 -I fold_preview_lock.py "$1"; fi
nova_lock=/run/lock/nova-fold-owner-preview.lock
if [[ $# -ne 2 || $2 != --lock-held || -L "$nova_lock" || ! -f /proc/$$/fd/9 ||
      ! "$nova_lock" -ef /proc/$$/fd/9 || "$(stat -Lc '%u:%h' /proc/$$/fd/9)" != 0:1 ]]; then
    echo 'Start the preview with the shared execution lock.' >&2
    exit 2
fi
flock -n 9 || { echo 'A Fold preview is already running.' >&2; exit 1; }

nova_reviewed=26553075df3c530fd81a98640e59ddf09c545215
nova_worktree=$(cd ../.. && pwd -P)
nova_git=(git -c "safe.directory=$nova_worktree")
"${nova_git[@]}" diff --exit-code "$nova_reviewed" -- server.py budget.py protocol.py Dockerfile .dockerignore deploy.sh
nova_git_status=$("${nova_git[@]}" status --porcelain=v1 --untracked-files=all --ignored=matching)
test -z "$nova_git_status"
for nova_tool in curl docker flock nginx nft openssl python3 ss stat timeout; do
    command -v "$nova_tool" >/dev/null || { echo "Required for the temporary preview: $nova_tool" >&2; exit 1; }
done

nova_owner=$(python3 - "$1" <<'PY'
from ipaddress import IPv4Address
import sys

try:
    peer, peer_port, host, host_port = sys.argv[1].split()
    owner = IPv4Address(peer)
    IPv4Address(host)
    if not owner.is_global or not all(0 < int(p) < 65536 for p in (peer_port, host_port)):
        raise ValueError
except ValueError:
    sys.exit('Open the existing SSH connection over IPv4 from the same Fold network first.')
print(owner)
PY
)

nova_host=brain.jamestraynor.co.uk
nova_port=18443
nova_cert="/etc/letsencrypt/live/$nova_host/fullchain.pem"
nova_tls_key="/etc/letsencrypt/live/$nova_host/privkey.pem"
test -s "$nova_cert" && test -s "$nova_tls_key" || { echo 'Existing Nova Brain TLS files were not found.' >&2; exit 1; }
openssl x509 -in "$nova_cert" -noout -checkhost "$nova_host" -checkend 1800 >/dev/null

nova_listeners=$(ss -H -ltn "sport = :$nova_port")
[[ -z "$nova_listeners" ]] || { echo "Port $nova_port is already in use." >&2; exit 1; }
nova_published=$(docker ps --format '{{.Ports}}')
if [[ "$nova_published" == *":$nova_port->"* ]]; then
    echo "Docker already has a binding for port $nova_port." >&2
    exit 1
fi
nova_networks=$(docker network ls --format '{{.Name}}')
nova_volumes=$(docker volume ls --format '{{.Name}}')
if grep -Eqi 'nova.*preview|fold.*preview|approval-2026' <<<"$nova_networks"$'\n'"$nova_volumes"; then
    echo 'Existing Docker preview resources require recovery review. No fresh ledger was created.' >&2
    exit 1
fi
nova_containers=$(docker ps -aq)
nova_docker_root=$(docker info --format '{{.DockerRootDir}}')
nova_state_mounts=()
while IFS= read -r nova_id; do
    [[ -n "$nova_id" ]] || continue
    nova_mounts=$(docker container inspect --format '{{range .Mounts}}{{println .Source .Destination}}{{end}}' "$nova_id")
    if grep -Eqi '/preview-control|nova.*preview|approval-2026' <<<"$nova_mounts"; then
        echo 'Existing Docker preview mounts require recovery review. No fresh ledger was created.' >&2
        exit 1
    fi
    nova_state_sources=$(docker container inspect --format '{{range .Mounts}}{{if eq .Destination "/state"}}{{println .Source}}{{end}}{{end}}' "$nova_id")
    while IFS= read -r nova_source; do
        if [[ -n "$nova_source" ]]; then nova_state_mounts+=("$nova_source"); fi
    done <<<"$nova_state_sources"
done <<<"$nova_containers"
nova_tables=$(nft list tables)
[[ "$nova_tables" != *'table inet nova_fold_owner_preview'* ]] || {
    echo 'An existing preview firewall table requires recovery review. No fresh ledger was created.' >&2
    exit 1
}
python3 -I fold_preview_preflight.py "$nova_docker_root" "${nova_state_mounts[@]}"
umask 077
nova_state_root=/var/lib/nova-fold-owner-preview
nova_state="$nova_state_root/approval-20261125"

nova_attempt=$(openssl rand -hex 6)
[[ "$nova_attempt" =~ ^[a-f0-9]{12}$ ]]
nova_docker_label="uk.co.traynor.nova-fold-preview-attempt=$nova_attempt"
nova_network="nova-fold-owner-preview-net-$nova_attempt"
nova_container="nova-fold-owner-preview-app-$nova_attempt"
nova_probe_container="nova-fold-owner-preview-check-$nova_attempt"
nova_bridge="nfp$nova_attempt"
nova_table="nova_fold_owner_preview_$nova_attempt"
[[ ! -e "/sys/class/net/$nova_bridge" ]] || { echo 'The temporary preview bridge name is occupied.' >&2; exit 1; }

nova_dir=$(mktemp -d /run/nova-fold-owner-preview.XXXXXXXX)
nova_pid=''
cleanup_preview_firewall() {
    local table
    if table=$(nft list table inet "$nova_table" 2>/dev/null) &&
        [[ "$table" == *"comment \"nova-fold-owner-preview:$nova_attempt\""* ]]; then
        nft delete table inet "$nova_table"
    fi
}
cleanup() {
    trap '' INT TERM HUP
    local preview_pid="${nova_pid:-${!:-}}" container container_owner network_owner
    if [[ -n "$preview_pid" && "$(jobs -p)" == "$preview_pid" ]]; then
        kill -TERM "$preview_pid" 2>/dev/null || true
        wait "$preview_pid" 2>/dev/null || true
    fi
    for container in "$nova_container" "$nova_probe_container"; do
        if container_owner=$(docker container inspect --format '{{index .Config.Labels "uk.co.traynor.nova-fold-preview-attempt"}}' "$container" 2>/dev/null) &&
            [[ "$container_owner" == "$nova_attempt" ]]; then
            docker rm -f "$container" >/dev/null || echo 'Could not remove a temporary preview container.' >&2
        fi
    done
    cleanup_preview_firewall || echo 'Could not remove the temporary preview firewall table.' >&2
    if network_owner=$(docker network inspect --format '{{index .Labels "uk.co.traynor.nova-fold-preview-attempt"}}' "$nova_network" 2>/dev/null) &&
        [[ "$network_owner" == "$nova_attempt" ]]; then
        docker network rm "$nova_network" >/dev/null || echo 'Could not remove the temporary preview network.' >&2
    fi
    rm -rf -- "$nova_dir"
    if [[ -d "$nova_state" ]]; then
        printf 'Prepared preview state is retained at %s\n' "$nova_state"
    fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP

install -d -m 0710 -o root -g 10001 "$nova_dir/control"
nova_image=nova-cloud-teacher-preview:26553075
if ! docker image inspect "$nova_image" >/dev/null 2>&1; then docker build --tag "$nova_image" .; fi
nova_expected=$(sha256sum server.py budget.py protocol.py)
nova_packaged=$(docker run --rm --name "$nova_probe_container" --label "$nova_docker_label" \
    --network none --entrypoint sha256sum "$nova_image" server.py budget.py protocol.py)
[[ "$nova_packaged" == "$nova_expected" ]] || { echo 'Existing Docker image differs from the reviewed backend.' >&2; exit 1; }
nova_image=$(docker image inspect --format '{{.Id}}' "$nova_image")

if [[ ! -e "$nova_state_root" ]]; then mkdir -m 0700 -- "$nova_state_root"; fi
[[ -d "$nova_state_root" && ! -L "$nova_state_root" && "$(stat -c '%u:%a' "$nova_state_root")" == 0:700 ]]
mkdir -m 0700 -- "$nova_state"
install -d -m 0700 -o 10001 -g 10001 "$nova_state/ledger"
(set -o noclobber; openssl rand -hex 32 > "$nova_state/owner-token")
install -m 0400 -o 10001 -g 10001 "$nova_state/owner-token" "$nova_dir/control/owner-token"

docker network create --driver bridge --ipv6=false --internal --label "$nova_docker_label" \
    --opt "com.docker.network.bridge.name=$nova_bridge" "$nova_network" >/dev/null
nova_network_status=$(docker network inspect --format '{{.Driver}}:{{.Internal}}:{{index .Options "com.docker.network.bridge.name"}}' "$nova_network")
[[ "$nova_network_status" == "bridge:true:$nova_bridge" && -d "/sys/class/net/$nova_bridge/bridge" ]] || {
    echo 'Docker did not create the dedicated internal preview bridge.' >&2
    exit 1
}
nova_end=$(( $(date +%s) + 1800 ))
docker run --detach --init --network "$nova_network" --name "$nova_container" --label "$nova_docker_label" \
    --user 10001:10001 --read-only --cap-drop ALL --security-opt no-new-privileges:true \
    --memory 128m --pids-limit 80 --log-driver none -e "NOVA_FOLD_PREVIEW_END=$nova_end" \
    --mount "type=bind,src=$(pwd)/fold_owner_preview.py,dst=/app/fold_owner_preview.py,readonly" \
    --mount "type=bind,src=$nova_dir/control,dst=/preview-control,readonly" \
    --mount "type=bind,src=$nova_state/ledger,dst=/state" \
    --entrypoint python "$nova_image" /app/fold_owner_preview.py >/dev/null
nova_preview_ports=$(docker port "$nova_container")
[[ -z "$nova_preview_ports" ]] || { echo 'Preview container has an unexpected published port.' >&2; exit 1; }
nova_backend=$(docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$nova_container")
python3 -c 'from ipaddress import IPv4Address; import sys
if not IPv4Address(sys.argv[1]).is_private:
    sys.exit("Preview container is missing its private IPv4 address.")' "$nova_backend"

cat > "$nova_dir/nginx.conf" <<NGINX
user www-data;
worker_processes 1;
pid $nova_dir/nginx.pid;
error_log /dev/null crit;
events { worker_connections 32; }
http {
    access_log off;
    client_body_temp_path $nova_dir/client;
    proxy_temp_path $nova_dir/proxy;
    limit_req_zone \$binary_remote_addr zone=nova_fold_preview:1m rate=12r/m;
    map \$remote_addr \$nova_fold_client { default 0; $nova_owner 1; }
    server {
        listen 0.0.0.0:$nova_port ssl;
        server_name $nova_host;
        ssl_certificate $nova_cert;
        ssl_certificate_key $nova_tls_key;
        ssl_protocols TLSv1.2 TLSv1.3;
        allow 127.0.0.1;
        allow $nova_owner;
        deny all;
        client_max_body_size 2k;
        client_body_timeout 8s;
        limit_req_status 429;
        location ~ ^/(health|guardian/(status|analyze)|assistant/ask)\$ {
            limit_req zone=nova_fold_preview burst=6 nodelay;
            proxy_pass http://$nova_backend:8787;
            proxy_set_header Host $nova_host;
            proxy_set_header Authorization \$http_authorization;
            proxy_set_header X-Nova-Fold-Client \$nova_fold_client;
            proxy_buffering off;
            proxy_next_upstream off;
            proxy_connect_timeout 3s;
            proxy_read_timeout 25s;
        }
        location / { return 404; }
    }
}
NGINX

nginx -t -p "$nova_dir/" -c "$nova_dir/nginx.conf"
nft -f - <<NFT
create table inet $nova_table { comment "nova-fold-owner-preview:$nova_attempt"; }
add chain inet $nova_table input { type filter hook input priority -300; policy accept; }
add chain inet $nova_table private_backend { type filter hook forward priority -300; policy accept; }
add rule inet $nova_table input iifname != "lo" ip saddr != $nova_owner tcp dport $nova_port drop
add rule inet $nova_table input meta nfproto ipv6 tcp dport $nova_port drop
add rule inet $nova_table private_backend oifname "$nova_bridge" ip daddr $nova_backend tcp dport 8787 drop
NFT
timeout --kill-after=5s "$(( nova_end - $(date +%s) ))s" \
    nginx -p "$nova_dir/" -c "$nova_dir/nginx.conf" -g 'daemon off;' 9>&- &
nova_pid=$!

nova_origin="https://$nova_host:$nova_port"
nova_curl=(curl --disable --silent --show-error --max-time 5 --noproxy '*' --resolve "$nova_host:$nova_port:127.0.0.1")
nova_auth() { printf 'Authorization: Bearer %s\n' "$(< "$nova_state/owner-token")"; }
get_budget() {
    nova_auth | "${nova_curl[@]}" --fail --header @- "$nova_origin/$1" |
        python3 -c '
import json
import sys

status = json.load(sys.stdin)
if (status["status"] != "authenticated" or status["assistant_available"] is not True
        or status["model"] != "gpt-5-mini-2025-08-07"
        or status["analysis_enabled"] is not False
        or status["cloud_teacher"]["automatic_analysis_enabled"] is not False
        or not isinstance(status["fold_owner_preview"]["phone_checked"], bool)):
    sys.exit("Disarmed preview status could not be verified.")
budget = status["budget"]
if (budget["funded_allowance_micro_usd"] != 250000
        or budget["configured_spending_limit_micro_usd"] != 250000
        or budget["configured_ceiling_micro_usd"] != 250000
        or budget["monthly_spending_limit_micro_usd"] != 250000
        or budget["approved_request_limit"] != 2
        or budget["request_reservation_micro_usd"] != 102000
        or budget["approved_requests_remaining"] != 2
        or budget["remaining_micro_usd"] != 250000
        or budget["expired"] or budget["paid_requests_halted"]):
    sys.exit("Fresh disarmed ledger does not match the approved preparation limits.")
print("checked" if status["fold_owner_preview"]["phone_checked"] else "pending")
'
}

"${nova_curl[@]}" --fail --retry 3 --retry-connrefused "$nova_origin/health" >/dev/null
for nova_route in guardian/status assistant/ask; do
    test "$("${nova_curl[@]}" -o /dev/null -w '%{http_code}' "$nova_origin/$nova_route")" = 401
    get_budget "$nova_route" >/dev/null
done

printf '\nDisarmed Owner Preview ready at %s\n' "$nova_origin"
printf '%s\n' 'On the Fold, open NovaOS Settings → Nova Brain → Owner Preview. Disable an existing preview connection before setting up the new one.'
printf 'Preview token for the app only: %s\n' "$(< "$nova_state/owner-token")" > /dev/tty
printf '%s\n' 'Tap Check preview connection. Both routes should authenticate and paid analysis should show as off.'
printf '%s\n' 'Ctrl-C closes the preview. It also expires automatically, retaining the prepared ledger.'
nova_remaining=$(( nova_end - $(date +%s) ))
if (( nova_remaining > 0 )) && read -r -t "$nova_remaining" -p 'After tapping Check on the Fold, press Enter here for the connection receipt. ' nova_confirm; then
    if [[ "$(get_budget assistant/ask)" == checked ]]; then
        printf '%s\n' 'Both authenticated Fold routes verified. Paid analysis off.'
    else
        printf '%s\n' 'Both authenticated Fold routes have not been verified in this session.'
    fi
fi
wait "$nova_pid" || [[ $? -eq 124 ]]
