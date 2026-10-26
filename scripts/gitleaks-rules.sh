#!/bin/sh
# Verifies the "daycompass-app-token" rule in .gitleaks.toml (same rule as
# the backend repo).
#
# Run it manually, or let CI run it:
#   scripts/gitleaks-rules.sh
#
# Every candidate token is generated from fresh randomness at runtime and
# piped through `gitleaks stdin`. Nothing token-shaped is ever written to
# a file, so this test needs no .gitleaks.toml allowlist entry of its own
# and cannot itself become a committed secret.
#
# What is pinned here, for each rule/prefix pair:
#   * the widened prefix: <prefix><letter>_ must be caught for every
#     letter a-z, not just the handful that happen to be in use today
#   * the body length: exactly 43 base64url characters
#   * that the rule survives the `[extend] useDefault` merge
#   * the known, intended over-match on a 44-character body (the regex
#     is unanchored, so its first 43 characters still match)

set -eu

if ! command -v gitleaks >/dev/null 2>&1; then
    echo "gitleaks-rules: gitleaks is not installed or not on PATH." >&2
    echo "gitleaks-rules: install a release from https://github.com/gitleaks/gitleaks, then retry." >&2
    exit 1
fi

repo_root=$(git rev-parse --show-toplevel)
config="${repo_root}/.gitleaks.toml"
failures=0

# 32 bytes of randomness as base64url without padding, which is always
# exactly 43 characters.
random_body() {
    head -c 32 /dev/urandom | base64 | tr '+/' '-_' | tr -d '=\n'
}

# Echoes "detected" or "clean" for one candidate string, scanning with
# only the named rule enabled. That isolation is what guarantees a
# result can never come from one of gitleaks' default rules, or from
# the other of our two rules, instead.
scan_with_rule_only() {
    rule="$1"
    candidate="$2"
    if printf '%s\n' "${candidate}" | gitleaks stdin \
        --no-banner --redact --log-level error \
        --config "${config}" --enable-rule "${rule}" >/dev/null 2>&1
    then
        echo clean
    else
        echo detected
    fi
}

# Same, but with the full merged ruleset, to prove the rule is still
# live after `[extend] useDefault = true`.
scan_full_config() {
    if printf '%s\n' "$1" | gitleaks stdin \
        --no-banner --redact --log-level error \
        --config "${config}" >/dev/null 2>&1
    then
        echo clean
    else
        echo detected
    fi
}

expect() {
    rule=$1
    want=$2
    label=$3
    candidate=$4
    got=$(scan_with_rule_only "${rule}" "${candidate}")
    if [ "${got}" = "${want}" ]; then
        echo "ok       ${label}"
    else
        echo "FAILED   ${label}: expected ${want}, got ${got}" >&2
        failures=$((failures + 1))
    fi
}

# Runs the full set of checks for one rule/prefix pair. `prefix` is the
# two-character stem before the `<letter>_` (here always "dc").
check_rule() {
    rule="$1"
    prefix="$2"

    echo "gitleaks-rules: [${rule}] checking every ${prefix}<letter>_ prefix is detected"
    for letter in a b c d e f g h i j k l m n o p q r s t u v w x y z; do
        expect "${rule}" detected "prefix ${prefix}${letter}_" \
            "token: ${prefix}${letter}_$(random_body)"
    done

    echo "gitleaks-rules: [${rule}] checking shapes that must NOT be detected"
    # 42 characters: one short of a real token body.
    short=$(random_body | cut -c1-42)
    expect "${rule}" clean "42-character body" "token: ${prefix}s_${short}"
    # The prefix letter must be lowercase.
    expect "${rule}" clean "uppercase prefix letter" "token: ${prefix}S_$(random_body)"
    # The prefix must be a letter, not a digit.
    expect "${rule}" clean "digit in prefix" "token: ${prefix}1_$(random_body)"
    # A character outside the base64url alphabet breaks the run into two
    # pieces, neither of them 43 characters long.
    body=$(random_body)
    expect "${rule}" clean "body interrupted by a dot" \
        "token: ${prefix}s_$(printf '%s' "${body}" | cut -c1-20).$(printf '%s' "${body}" | cut -c22-43)"

    echo "gitleaks-rules: [${rule}] checking intended over-matches"
    # 44 characters. The regex is unanchored on purpose, so the first 43
    # of them match. Over-matching is the right failure direction for a
    # secret scanner, so this is asserted rather than tolerated.
    expect "${rule}" detected "44-character body" "token: ${prefix}s_$(random_body)Z"
    expect "${rule}" detected "token embedded mid-line" \
        "Authorization: Bearer ${prefix}s_$(random_body) (trailing text)"

    echo "gitleaks-rules: [${rule}] checking the rule survives the useDefault merge"
    got=$(scan_full_config "token: ${prefix}b_$(random_body)")
    if [ "${got}" = "detected" ]; then
        echo "ok       full config still detects ${prefix}b_"
    else
        echo "FAILED   full config: expected detected, got ${got}" >&2
        failures=$((failures + 1))
    fi
}

check_rule daycompass-app-token dc

if [ "${failures}" -ne 0 ]; then
    echo "gitleaks-rules: ${failures} check(s) failed." >&2
    exit 1
fi

echo "gitleaks-rules: all checks passed."
