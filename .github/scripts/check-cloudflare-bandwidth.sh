#!/usr/bin/env bash
set -euo pipefail

health_file="${1:-build/site/v1/health.json}"
threshold=5000000
window_days=30
dataset="news_shorts_feed_requests"

if [[ ! -f "$health_file" ]]; then
  printf 'Health report is missing: %s\n' "$health_file" >&2
  exit 1
fi

write_result() {
  local status="$1"
  local responses="$2"
  local detail="$3"
  local updated
  updated="$(mktemp "${health_file}.XXXXXX")"
  jq \
    --arg status "$status" \
    --arg detail "$detail" \
    --argjson responses "$responses" \
    --argjson threshold "$threshold" \
    --argjson windowDays "$window_days" \
    '.feedPageResponses30d = $responses
      | .feedPageResponseThreshold = $threshold
      | .bandwidthCheck = {
          status: $status,
          detail: $detail,
          provider: "cloudflare-analytics-engine",
          windowDays: $windowDays
        }
      | if $status == "failed" then
          .failedChecks = ((.failedChecks // []) + ["bandwidth: " + $detail])
        elif $status == "skipped" then
          .warningChecks = ((.warningChecks // []) + ["bandwidth: " + $detail])
        else . end' \
    "$health_file" > "$updated"
  mv "$updated" "$health_file"
}

if [[ -z "${CLOUDFLARE_API_TOKEN:-}" ]]; then
  write_result "skipped" "null" "CLOUDFLARE_API_TOKEN is not configured"
  exit 0
fi

if [[ -z "${CLOUDFLARE_ACCOUNT_ID:-}" ]]; then
  write_result "failed" "null" "CLOUDFLARE_ACCOUNT_ID is not configured"
  exit 1
fi

query="SELECT SUM(_sample_interval) AS responses FROM ${dataset} WHERE timestamp >= NOW() - INTERVAL '${window_days}' DAY FORMAT JSON"
if ! api_response="$(curl --fail-with-body --silent --show-error \
  --request POST \
  --url "https://api.cloudflare.com/client/v4/accounts/${CLOUDFLARE_ACCOUNT_ID}/analytics_engine/sql" \
  --header "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}" \
  --data "$query")"; then
  write_result "failed" "null" "Cloudflare Analytics query failed"
  exit 1
fi

if ! responses="$(jq -er '((.data[0].responses // 0) | tonumber | floor)' <<< "$api_response")"; then
  write_result "failed" "null" "Cloudflare Analytics returned no usable response count"
  exit 1
fi

if (( responses > threshold )); then
  write_result "failed" "$responses" "Rolling feed-page responses exceed the 5000000 ceiling"
  printf 'Feed bandwidth tripwire: %s responses in %s days exceeds %s\n' \
    "$responses" "$window_days" "$threshold" >&2
  exit 1
fi

write_result "passed" "$responses" "Rolling feed-page responses are within the ceiling"
printf 'Feed bandwidth tripwire: %s responses in %s days (limit %s)\n' \
  "$responses" "$window_days" "$threshold"
