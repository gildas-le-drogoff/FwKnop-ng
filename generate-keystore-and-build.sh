#!/bin/bash

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

RELEASE_KEYSTORE_PROPS="keystore.properties"
RELEASE_CERT_SHA256="$(sed -n 's/^AllowedAPKSigningKeys: *//p' "${PROJECT_DIR}/metadata/fr.gildasledrogoff.fwknop.yml")"

UPLOAD_KEYSTORE_FILE="play-upload-keystore.jks"
UPLOAD_KEYSTORE_PROPS="play-upload.properties"
UPLOAD_KEY_ALIAS="upload"

VERSION_NAME="$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' "${PROJECT_DIR}/app/build.gradle.kts")"
VERSION_CODE="$(sed -n 's/^ *versionCode = \([0-9]*\)/\1/p' "${PROJECT_DIR}/app/build.gradle.kts")"

RELEASE_APK="FwKnop-ng-${VERSION_NAME}.apk"
RELEASE_AAB="FwKnop-ng-${VERSION_NAME}.aab"
ARTIFACT_BASENAME="0_Fwknop_ng_Android"
DATE_TAG="$(date +%F_%H)"
FINAL_APK="${ARTIFACT_BASENAME}.apk"
STAMPED_APK="${ARTIFACT_BASENAME}_${DATE_TAG}.apk"
FINAL_DEBUG_APK="${ARTIFACT_BASENAME}_debug.apk"
SYNC_HOST="mercury"
SYNC_DIR="/mnt/NUMBER_4/Sync_Gildas_s_Phone/Orion/0_AppDev"

RED=$'\033[0;31m'
GREEN=$'\033[0;32m'
PURPLE=$'\033[0;35m'
YELLOW=$'\033[1;33m'
NC=$'\033[0m'

log() { printf '%b\n' "$1"; }
die() {
	log "${RED}$1${NC}"
	exit 1
}

prop() { sed -n "s/^$2=//p" "$1"; }

apksigner() {
	local tools
	tools="$(find "${ANDROID_HOME:-${HOME}/Android/Sdk}/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n1)"
	[ -x "${tools}/apksigner" ] || die "apksigner not found (Android SDK build-tools)."
	"${tools}/apksigner" "$@"
}

check_release_keystore() {
	[ -f "$RELEASE_KEYSTORE_PROPS" ] ||
		die "${RELEASE_KEYSTORE_PROPS} missing: restore the release key from backup (do not generate a new one)."
	local store
	store="$(prop "$RELEASE_KEYSTORE_PROPS" storeFile)"
	[ -f "${store#../}" ] || die "Release keystore ${store#../} missing: restore it from backup."
	chmod 600 "$RELEASE_KEYSTORE_PROPS" "${store#../}"
}

generate_upload_keystore() {
	local password
	password="$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32)"
	(
		umask 077
		keytool -genkeypair -v -keystore "$UPLOAD_KEYSTORE_FILE" -alias "$UPLOAD_KEY_ALIAS" \
			-keyalg RSA -keysize 8192 -validity 10950 \
			-storepass "$password" -keypass "$password" \
			-dname "CN=FwKnop-ng Play Upload Key,O=ledrogoff,C=FR"
		cat > "$UPLOAD_KEYSTORE_PROPS" << EOF
storeFile=${UPLOAD_KEYSTORE_FILE}
storePassword=${password}
keyAlias=${UPLOAD_KEY_ALIAS}
keyPassword=${password}
EOF
	)
	keytool -J-Duser.language=en -export -rfc -keystore "$UPLOAD_KEYSTORE_FILE" -alias "$UPLOAD_KEY_ALIAS" \
		-storepass "$password" -file play-upload-certificate.pem
	log "${YELLOW}⚠️  NEW Play upload keystore generated (one-shot)."
	log "⚠️  Register play-upload-certificate.pem as the upload key in the Play Console."
	log "⚠️  Back up ${UPLOAD_KEYSTORE_FILE} and ${UPLOAD_KEYSTORE_PROPS} somewhere safe NOW.${NC}"
}

setup_upload_keystore() {
	if [ -f "$UPLOAD_KEYSTORE_FILE" ] && [ -f "$UPLOAD_KEYSTORE_PROPS" ]; then
		log "${GREEN}Reusing existing Play upload keystore.${NC}"
		return
	fi
	[ ! -f "$UPLOAD_KEYSTORE_FILE" ] ||
		die "${UPLOAD_KEYSTORE_FILE} exists but ${UPLOAD_KEYSTORE_PROPS} is missing: restore it from backup."
	log "${GREEN}Generating Play upload keystore (one-shot)...${NC}"
	generate_upload_keystore
}

check_release_tree() {
	if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
		log "${YELLOW}⚠️  Working tree has uncommitted changes: the APK will not match the F-Droid build.${NC}"
	elif [ "$(git rev-parse HEAD)" != "$(git rev-parse -q --verify "v${VERSION_NAME}^{commit}" || true)" ]; then
		log "${YELLOW}⚠️  HEAD is not tag v${VERSION_NAME}: do not publish this APK as a release.${NC}"
	fi
}

check_apk_signature() {
	local digest
	digest="$(apksigner verify --print-certs "$1" 2> /dev/null |
		sed -n 's/^Signer #1 certificate SHA-256 digest: //p; s/^V2 Signer: certificate SHA-256 digest: //p' | head -n1)"
	[ "$digest" = "$RELEASE_CERT_SHA256" ] ||
		die "APK signed with ${digest:-nothing}, expected ${RELEASE_CERT_SHA256} (AllowedAPKSigningKeys)."
}

check_aab_signature() {
	local expected actual
	expected="$(keytool -J-Duser.language=en -list -keystore "$UPLOAD_KEYSTORE_FILE" -alias "$UPLOAD_KEY_ALIAS" \
		-storepass "$(prop "$UPLOAD_KEYSTORE_PROPS" storePassword)" | sed -n 's/.*(SHA-256): //p')"
	actual="$(keytool -J-Duser.language=en -printcert -jarfile "$1" | sed -n 's/^[[:space:]]*SHA256: //p' | head -n1)"
	[ -n "$expected" ] && [ "$actual" = "$expected" ] ||
		die "AAB signed with ${actual:-nothing}, expected the Play upload key ${expected}."
}

build_apks() {
	log "${GREEN}Build release APK (F-Droid/GitHub) + debug APK...${NC}"
	./gradlew assembleDebug assembleRelease --console=plain -q
}

build_aab() {
	log "${GREEN}Build release AAB (Google Play)...${NC}"
	env \
		"ORG_GRADLE_PROJECT_android.injected.signing.store.file=${PROJECT_DIR}/$(prop "$UPLOAD_KEYSTORE_PROPS" storeFile)" \
		"ORG_GRADLE_PROJECT_android.injected.signing.store.password=$(prop "$UPLOAD_KEYSTORE_PROPS" storePassword)" \
		"ORG_GRADLE_PROJECT_android.injected.signing.key.alias=$(prop "$UPLOAD_KEYSTORE_PROPS" keyAlias)" \
		"ORG_GRADLE_PROJECT_android.injected.signing.key.password=$(prop "$UPLOAD_KEYSTORE_PROPS" keyPassword)" \
		./gradlew bundleRelease --console=plain -q
}

push_to_phone() {
	local prefix=""
	[ "$(hostname -s)" = "$SYNC_HOST" ] || prefix="${SYNC_HOST}:"
	rsync -ah -v --stats --info=progress2 "${PROJECT_DIR}/${FINAL_APK}" "${prefix}${SYNC_DIR}/${STAMPED_APK}"
	rsync -ah -v --stats --info=progress2 "${PROJECT_DIR}/${FINAL_APK}" "${prefix}${SYNC_DIR}/${FINAL_APK}"
	# rsync -ah -v --stats --info=progress2 "${PROJECT_DIR}/${FINAL_DEBUG_APK}" "${prefix}${SYNC_DIR}/${FINAL_DEBUG_APK}"
}

main() {
	cd "$PROJECT_DIR"
	[ -n "$VERSION_NAME" ] && [ -n "$VERSION_CODE" ] || die "versionName/versionCode not found in app/build.gradle.kts."
	[ -n "$RELEASE_CERT_SHA256" ] || die "AllowedAPKSigningKeys not found in metadata/."
	log "${GREEN}FwKnop-ng ${PURPLE}${VERSION_NAME}${GREEN} (versionCode ${VERSION_CODE})${NC}"

	check_release_keystore
	setup_upload_keystore
	check_release_tree

	log "${GREEN}Format code...${NC}"
	# ktlint -F ./app/src 2> /dev/null || true

	build_apks
	local apk="app/build/outputs/apk/release/app-release.apk"
	local debug_apk="app/build/outputs/apk/debug/app-debug.apk"
	[ -f "$apk" ] || die "Release APK build failed or not found."
	check_apk_signature "$apk"
	cp "$apk" "$RELEASE_APK"
	cp "$apk" "$FINAL_APK"
	cp "$debug_apk" "$FINAL_DEBUG_APK"

	build_aab
	local aab="app/build/outputs/bundle/release/app-release.aab"
	[ -f "$aab" ] || die "Release AAB build failed or not found."
	check_aab_signature "$aab"
	cp "$aab" "$RELEASE_AAB"

	log "${GREEN}✅ APK (F-Droid/GitHub) -> ${PURPLE}${PROJECT_DIR}/${RELEASE_APK}${NC}"
	log "${GREEN}✅ AAB (Google Play)    -> ${PURPLE}${PROJECT_DIR}/${RELEASE_AAB}${NC}"
	log "${GREEN}✅ APK (debug)          -> ${PURPLE}${PROJECT_DIR}/${FINAL_DEBUG_APK}${NC}"
	log "${RED}__________________________${NC}"

	[ "${1:-}" = "--no-push" ] || push_to_phone
}

main "$@"
