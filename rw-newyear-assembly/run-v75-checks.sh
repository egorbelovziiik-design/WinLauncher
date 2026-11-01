#!/usr/bin/env bash
# Disposable Paper 1.21.4 regression groups. Set KG_TEST_DIR and KG_API_CP;
# the caller supplies Paper, its matching Mojang JAR and minecraft-protocol.
set -euo pipefail
: "${KG_TEST_DIR:?}" "${KG_API_CP:?}"
GROUP="${1:?expected release, gameplay, economy or protection}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TEST="$ROOT/rw-newyear-assembly/core-src/src/test/paper-integration"
KG="$KG_TEST_DIR"
LOG="$KG/logs-$GROUP"
mkdir -p "$LOG"
COMMON=(--paper "$KG/paper.jar" --vanilla "$KG/vanilla.jar" --api-classpath "$KG_API_CP" --node-modules "$KG/node/node_modules" --accept-eula)

run_logged() {
  local label="$1"; shift
  echo "::group::$label"
  "$@" 2>&1 | tee "$LOG/$label.log"
  echo "::endgroup::"
}
integration() {
  local name="$1"; shift
  run_logged "$name" python "$TEST/$name.py" "${COMMON[@]}" "$@"
}

case "$GROUP" in
  release)
    run_logged package-update python "$ROOT/rw-newyear-assembly/build-core-update.py"
    cp "$ROOT/rw-newyear-assembly/ОБНОВЛЕНИЕ_7.5.1.md" "$ROOT/rw-newyear-assembly/server/ОБНОВЛЕНИЕ_7.5.1.md"
    run_logged package-server python "$ROOT/rw-newyear-assembly/build-assembly.py"
    run_logged verify-distribution python "$ROOT/rw-newyear-assembly/core-src/src/test/test-distribution-v751.py"
    integration test-tree-season
    integration test-tree-admin-donations
    integration test-release-upgrade --core-old "$ROOT/kinggrief/public/KingGriefCore-v7.4.0.jar"
    integration test-full-assembly --assembly "$ROOT/rw-newyear-assembly/rw-newyear-full-assembly-v7.5.1.zip" --core-version 7.5.1
    ;;
  gameplay)
    integration test-batch-features
    integration test-snow-memory
    integration test-build-contest
    integration test-calendar
    integration test-winter-fishing
    integration test-train-stats
    integration test-parkour --clean
    ;;
  economy)
    integration test-auto-buyer
    integration test-buyer-sale
    integration test-mining-safety
    integration test-claim-break
    ;;
  protection)
    curl --fail --location --retry 3 'https://cdn.modrinth.com/data/DKY9btbd/versions/f9NoeotB/worldguard-bukkit-7.0.13-dist.jar' -o "$KG/worldguard.jar"
    echo '75bd095a490b8b193a89b6b6244ecb7e7539c33afc0ac9066950cdb1a0aea8aba8073843d7d91fbb7cda80a305e7cabc2de60a2988e8c21dfaeb75b692f0d1b5  '"$KG/worldguard.jar" | sha512sum -c -
    curl --fail --location --retry 3 'https://cdn.modrinth.com/data/1u6JkXh5/versions/4jRlujfz/worldedit-bukkit-7.3.10.jar' -o "$KG/worldedit.jar"
    echo 'e88bf56ab13ac547cecce6cfea23b0836c85eec9648e99f1578f63537719dab17bc6976c4d68a4f2b8d4f9318c54a2d8d740ccaedfedfcaa16dccaf5dba002fa  '"$KG/worldedit.jar" | sha512sum -c -
    integration test-connect-security
    integration test-combat-protection
    integration test-combat-worldguard --worldguard "$KG/worldguard.jar" --worldedit "$KG/worldedit.jar"
    integration test-frost-forge
    integration test-orbs
    ;;
  *) echo "Unknown test group: $GROUP" >&2; exit 2 ;;
esac

{
  echo "=== $GROUP ==="
  for f in "$LOG"/*.log; do
    printf '%s: ' "$(basename "$f" .log)"
    grep -E '(RESULT PASS|INTEGRATION: PASS|FULL ASSEMBLY SMOKE: PASS|^OK$)' "$f" | tail -4 | tr '\n' ';' || true
    echo
  done
} > "$KG/report-$GROUP.txt"
cat "$KG/report-$GROUP.txt"
if [[ "$GROUP" == release ]]; then
  cd "$ROOT"
  tar -cf "$KG/distribution.tar" \
    rw-newyear-assembly/KingGriefCore.jar \
    rw-newyear-assembly/plugins/KingGriefCore.jar \
    rw-newyear-assembly/server/plugins/KingGriefCore.jar \
    kinggrief/public/KingGriefCore.jar \
    kinggrief/public/KingGriefCore-v7.5.1.jar \
    rw-newyear-assembly/KingConnect.jar \
    rw-newyear-assembly/plugins/KingConnect.jar \
    rw-newyear-assembly/server/plugins/KingConnect.jar \
    kinggrief/public/KingConnect.jar \
    kinggrief/public/KingConnect-v2.3.1.jar \
    rw-newyear-assembly/KingGriefCore-7.5.1-update.zip \
    kinggrief/public/KingGriefCore-7.5.1-update.zip \
    rw-newyear-assembly/rw-newyear-full-assembly-v7.5.1.zip \
    kinggrief/public/rw-newyear-full-assembly-v7.5.1.zip \
    rw-newyear-assembly/ОБНОВЛЕНИЕ_7.5.1.md \
    rw-newyear-assembly/server/ОБНОВЛЕНИЕ_7.5.1.md
fi
