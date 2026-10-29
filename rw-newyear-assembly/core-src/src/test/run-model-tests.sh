#!/bin/sh
# Self-contained unit tests for item migration and URL selection. Requires a JDK.
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CORE=$(CDPATH= cd -- "$HERE/../.." && pwd)
COMMON="$CORE/../common-src/src/main/java/net/kinggrief/common/ResourcePackSettings.java"
MIGRATION="$CORE/src/main/java/net/kinggrief/core/LegacyItemModelMigration.java"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
JAVAC=${JAVAC:-javac}
JAVA=${JAVA:-java}
mkdir -p "$TMP/modern" "$TMP/legacy"
# The fixtures are intentionally tiny and are used ONLY for tests, never in a plugin JAR.
find "$HERE/fixtures/common" "$HERE/fixtures/modern" -name '*.java' > "$TMP/modern-sources"
printf '%s\n' "$COMMON" "$MIGRATION" "$HERE/java/net/kinggrief/core/ModelMigrationTest.java" >> "$TMP/modern-sources"
"$JAVAC" -encoding UTF-8 -d "$TMP/modern" @"$TMP/modern-sources"
"$JAVA" -cp "$TMP/modern" net.kinggrief.core.ModelMigrationTest
find "$HERE/fixtures/common" "$HERE/fixtures/legacy" -name '*.java' > "$TMP/legacy-sources"
printf '%s\n' "$MIGRATION" "$HERE/java/net/kinggrief/core/LegacyApiTest.java" >> "$TMP/legacy-sources"
"$JAVAC" -encoding UTF-8 -d "$TMP/legacy" @"$TMP/legacy-sources"
"$JAVA" -cp "$TMP/legacy" net.kinggrief.core.LegacyApiTest
