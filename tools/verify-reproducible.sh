#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
#
# SPDX-License-Identifier: AGPL-3.0-or-later

# Local equivalent of the "verify" job of .github/workflows/reproducible.yml:
# builds the release APK twice, from two independent clones located in
# different paths, and fails unless both APKs are byte-for-byte identical.
#
# The clones are made from the committed state (HEAD) of this repository, so
# untracked or ignored files (keystore.properties, local.properties, build
# outputs, keystores) never leak into the builds: the APKs are unsigned.
#
# Usage: tools/verify-reproducible.sh [--keep] [--ref <git-ref>]
#   --keep        keep the temporary directory (clones and APKs) after the run
#   --ref <ref>   build this commit/tag instead of HEAD (default: HEAD)
#
# Requires: git, a JDK matching .github/.java-version, the Android SDK
# (ANDROID_HOME or ANDROID_SDK_ROOT, or sdk.dir in local.properties).

# Needs real bash (arrays, process substitution): `sh script` would run it in
# POSIX mode or under another shell.
if [ -z "${BASH_VERSION:-}" ] || shopt -oq posix; then
	exec bash "$0" "$@"
fi

set -euo pipefail

KEEP=0
REF="HEAD"
while [ $# -gt 0 ]; do
	case "$1" in
	--keep) KEEP=1 ;;
	--ref)
		REF="${2:?--ref requires a value}"
		shift
		;;
	-h | --help)
		sed -n '/^# Local equivalent/,/^# (ANDROID_HOME/p' "$0" | sed 's/^# \{0,1\}//'
		exit 0
		;;
	*)
		echo "Unknown argument: $1" >&2
		exit 2
		;;
	esac
	shift
done

# The script lives in tools/: the repository root is its parent directory.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Resolve the Android SDK location, as Gradle would.
if [ -z "${ANDROID_HOME:-}" ]; then
	if [ -n "${ANDROID_SDK_ROOT:-}" ]; then
		ANDROID_HOME="$ANDROID_SDK_ROOT"
	elif [ -f local.properties ]; then
		ANDROID_HOME="$(sed -n 's/^sdk\.dir=//p' local.properties | tail -n 1)"
	fi
fi
: "${ANDROID_HOME:?ANDROID_HOME is not set and no sdk.dir found in local.properties}"
export ANDROID_HOME

COMMIT="$(git rev-parse --verify "$REF^{commit}")"

if [ -n "$(git status --porcelain --untracked-files=no)" ] && [ "$REF" = "HEAD" ]; then
	echo "⚠️  Uncommitted changes in tracked files are NOT part of the builds (only $COMMIT is built)." >&2
fi

WORK="$(mktemp -d -t fwknop-repro.XXXXXX)"
cleanup() {
	if [ "$KEEP" -eq 1 ]; then
		echo "Kept: $WORK"
	else
		rm -rf "$WORK"
	fi
}
trap cleanup EXIT

# Two clones in different paths (the paths must not influence the output).
DIR_A="$WORK/a"
DIR_B="$WORK/b/nested-path"
mkdir -p "$DIR_B"

clone() {
	git clone --quiet --no-hardlinks "$ROOT" "$1"
	git -C "$1" -c advice.detachedHead=false checkout --quiet "$COMMIT"
}

build() {
	local dir="$1"
	echo "==> Building in $dir"
	"$dir/gradlew" -p "$dir" clean :app:assembleRelease --stacktrace
}

find_apk() {
	local matches=("$1"/app/build/outputs/apk/release/app-release*.apk)
	if [ ! -f "${matches[0]}" ]; then
		echo "::error::No release APK found in $1." >&2
		return 1
	fi
	if [ "${#matches[@]}" -ne 1 ]; then
		echo "::error::Several release APKs found in $1: ${matches[*]}" >&2
		return 1
	fi
	echo "${matches[0]}"
}

echo "==> Cloning $COMMIT into two independent copies"
clone "$DIR_A"
clone "$DIR_B"

build "$DIR_A"
APK_A="$(find_apk "$DIR_A")"
"$ROOT/tools/check-apk-unsigned.sh" "$APK_A"

build "$DIR_B"
APK_B="$(find_apk "$DIR_B")"
"$ROOT/tools/check-apk-unsigned.sh" "$APK_B"

echo "==> Comparing"
sha256sum "$APK_A" "$APK_B"
if cmp -s "$APK_A" "$APK_B"; then
	echo "✅ Reproducible: the two builds are byte-for-byte identical."
else
	echo "❌ Build is not reproducible — the two APKs differ." >&2
	cmp "$APK_A" "$APK_B" || true
	echo "Entries that differ (CRC-32, name):" >&2
	diff <(unzip -lv "$APK_A" | awk '{ print $7, $8 }') \
		<(unzip -lv "$APK_B" | awk '{ print $7, $8 }') || true
	# Keep the APKs available for diffoscope & co.
	KEEP=1
	exit 1
fi
