#!/usr/bin/env bash
# Roadmap project IDs for `gh project item-add` / `item-edit`, looked up once
# so sessions don't have to rediscover them. IDs are not secrets.
#
# The project is shared with the backend repo and lives under the `Daycompass`
# org, not the personal `@me` account: https://github.com/orgs/Daycompass/projects/1
# Looked up via:
#   gh project list --owner Daycompass --format json
#   gh project field-list 1 --owner Daycompass --format json
#
# Usage: source scripts/gh-project.sh

set -euo pipefail

PROJECT_OWNER="Daycompass"
PROJECT_NUMBER="1"
PROJECT_ID="PVT_kwDOFKUMEs4BoOkY"
STATUS_FIELD_ID="PVTSSF_lADOFKUMEs4BoOkYzhmvoxU"
STATUS_OPTION_TODO="f75ad846"
STATUS_OPTION_IN_PROGRESS="47fc9ee4"
STATUS_OPTION_DONE="98236657"
