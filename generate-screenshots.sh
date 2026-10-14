#!/bin/bash

# SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
#
# SPDX-License-Identifier: AGPL-3.0-or-later

# Generates fastlane screenshots (F-Droid and Google Play) in English and
# French from a running emulator, for phone and 7" / 10" tablets.
# Usage: ./generate-screenshots.sh [--build] [phone] [sevenInch] [tenInch]
#   --build : build and install the debug APK before taking screenshots.
#   Formats: the ones to capture (all by default).
# The emulator screen is resized (wm size / density) so that each format
# complies with Google Play (9:16 or 16:9, 1080 px minimum, 8 screenshots max,
# 8 MB max); PNGs are written without an alpha channel or metadata
# (24-bit RGB), which F-Droid also accepts.
# The app data is cleared on the emulator, replaced with demo configs
# (RFC 5737 documentation addresses, no real network access), then cleared
# again at the end.
# ANDROID_SERIAL lets you choose the emulator if several are running.
# Requires adb and ImageMagick; optipng, if present, shrinks the files.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACKAGE="fr.gildasledrogoff.fwknop"
ACTIVITY="${PACKAGE}/.presentation.MainActivity"
METADATA_DIR="${PROJECT_DIR}/fastlane/metadata/android"
RES_DIR="${PROJECT_DIR}/app/src/main/res"
UI_DUMP="/sdcard/fwknop-ui.xml"
# fastlane locale -> Android locale -> resource folder.
LOCALES=("en-US:en-US:values" "fr-FR:fr-FR:values-fr")
# Format -> width -> height -> density. Google Play: 9:16 or 16:9, 1080 px
# minimum per side. Phone: 411 x 731 dp; 7" tablet: 617 x 1097 dp
# in portrait; 10" tablet: 1280 x 720 dp in landscape (multi-column grid).
FORM_FACTORS=("phone:1080:1920:420" "sevenInch:1080:1920:280" "tenInch:2560:1440:320")
# Google Play limit per screenshot.
MAX_BYTES=$((8 * 1024 * 1024))
# Fixed source IP: the knock queries no resolution service, and
# no real public IP appears on screen.
DEMO_SOURCE_IP="198.51.100.23"
# Config typed in the screenshots: UDP to an IP, sent without network.
KNOCKED="home"
# Demo fwknop configs: nickname|server|protocol|Rijndael key|HMAC key.
DEMO_CONFIGS=(
	"home|203.0.113.10|UDP|demoRijndaelKey42|demoHmacKey42"
	"office|vpn.example.com|TCP|officeRijndael77|officeHmac77"
)
# Demo Wake-on-LAN configs: nickname|MAC address.
DEMO_WAKE_CONFIGS=("nas|02:42:ac:11:00:02")

RED=$'\033[0;31m'
GREEN=$'\033[0;32m'
PURPLE=$'\033[0;35m'
NC=$'\033[0m'

log() { printf '%b\n' "$1"; }
# On stderr: stays visible from a command substitution.
die() {
	log "${RED}$1${NC}" >&2
	exit 1
}

# --- Device ----------------------------------------------------------------

pick_emulator() {
	if [ -z "${ANDROID_SERIAL:-}" ]; then
		ANDROID_SERIAL="$(adb devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')"
	fi
	[ -n "$ANDROID_SERIAL" ] || die "No emulator running (adb devices)."
	export ANDROID_SERIAL
	local qemu
	qemu="$(adb shell 'getprop ro.boot.qemu; getprop ro.kernel.qemu' | tr -d '\r\n')"
	[[ $qemu == *1* ]] || die "${ANDROID_SERIAL} is not an emulator: refusing to wipe its data."
	local sdk
	sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
	[ "$sdk" -ge 33 ] || die "API ${sdk}: per-app language requires API 33+."
	log "${GREEN}Emulator: ${PURPLE}${ANDROID_SERIAL}${GREEN} (API ${sdk})${NC}"
}

install_app() {
	log "${GREEN}Building + installing the debug APK...${NC}"
	(cd "$PROJECT_DIR" && ./gradlew :app:installDebug --console=plain -q)
}

require_installed() {
	adb shell pm path "$PACKAGE" | grep -q '^package:' ||
		die "${PACKAGE} missing from the emulator: rerun with --build."
	adb shell dumpsys package "$PACKAGE" | grep -q 'pkgFlags=.*DEBUGGABLE' ||
		die "Non-debug APK installed (FLAG_SECURE active): rerun with --build."
}

# Simulates the format: screencap then renders exactly this size.
SCREEN_W=0
SCREEN_H=0
DENSITY=160
set_screen() {
	SCREEN_W="$1"
	SCREEN_H="$2"
	DENSITY="$3"
	adb shell wm size "${SCREEN_W}x${SCREEN_H}"
	adb shell wm density "$DENSITY"
	sleep 2
}

# Natural orientation, regardless of the emulator's sensor.
ROTATION=""
lock_rotation() {
	ROTATION="$(adb shell settings get system accelerometer_rotation | tr -d '\r')"
	adb shell settings put system accelerometer_rotation 0
	adb shell settings put system user_rotation 0
}

set_animations() {
	local scale
	for scale in window_animation_scale transition_animation_scale animator_duration_scale; do
		adb shell settings put global "$scale" "$1"
	done
}

demo_mode() {
	adb shell am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null
}

# Reapplied after each resize: the recreated system UI would otherwise
# duplicate the network icons. The emulator's mobile network is
# hidden ("3G" icon), Wi-Fi is shown connected (no "!").
enter_demo_mode() {
	adb shell settings put global sysui_demo_allowed 1
	demo_mode exit
	demo_mode enter
	demo_mode clock -e hhmm 1200
	demo_mode battery -e level 100 -e plugged false
	demo_mode network -e wifi show -e level 4 -e fully true
	demo_mode network -e mobile hide
	demo_mode notifications -e visible false
	sleep 1
}

restore_device() {
	demo_mode exit || true
	set_animations 1 || true
	adb shell wm size reset || true
	adb shell wm density reset || true
	if [[ $ROTATION =~ ^[01]$ ]]; then
		adb shell settings put system accelerometer_rotation "$ROTATION" || true
	fi
	adb shell rm -f "$UI_DUMP" || true
	[ -z "$TMP_DIR" ] || rm -rf "$TMP_DIR"
}

# --- Text resources --------------------------------------------------------

# Raw value of a <string>, possibly spanning multiple lines, whitespace
# collapsed as aapt does: string_value <values folder> <name>.
string_value() {
	tr '\n' ' ' < "${RES_DIR}/$1/strings.xml" | sed 's/<string /\n&/g' |
		sed -n "s|^<string name=\"$2\"[^>]*>[[:space:]]*\(.*\)</string *>.*|\1|p" |
		sed -e 's/[[:space:]]\+/ /g' -e 's/^ //' -e 's/ $//' | head -n1
}

# Translated Android string: string_res <values folder> <name> [%1$s argument].
string_res() {
	local value
	value="$(string_value "$1" "$2")"
	[ -n "$value" ] || value="$(string_value values "$2")"
	[ -n "$value" ] || die "String not found: $2"
	value="${value//\\\'/\'}"
	value="${value//\\\"/\"}"
	value="${value//\\u00A0/$'\xc2\xa0'}"
	value="${value//&amp;/&}"
	value="${value//&lt;/<}"
	value="${value//&gt;/>}"
	printf '%s' "${value//%1\$s/${3:-}}"
}

# --- UI --------------------------------------------------------------------

ui_nodes() {
	adb shell uiautomator dump "$UI_DUMP" > /dev/null 2>&1 || return 0
	adb exec-out cat "$UI_DUMP" | grep -o '<node [^>]*>' |
		sed -e "s/&apos;/'/g" -e "s/&#39;/'/g" -e 's/&quot;/"/g' -e 's/&amp;/\&/g'
}

# Center of the first node whose text or content-desc exactly equals the label.
node_center() {
	grep -F -e "text=\"$1\"" -e "content-desc=\"$1\"" | head -n1 | bounds_center
}

# Center of the first node whose text starts with the label.
node_prefix_center() {
	grep -F -e "text=\"$1" | head -n1 | bounds_center
}

bounds_center() {
	sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
		awk '{ print int(($1 + $3) / 2), int(($2 + $4) / 2) }'
}

# Bottom of the first node whose text or content-desc equals the label.
node_bottom() {
	grep -F -e "text=\"$1\"" -e "content-desc=\"$1\"" | head -n1 |
		sed -E 's/.*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,([0-9]+)\]".*/\1/'
}

find_text() {
	ui_nodes | node_center "$1" || true
}

dp() {
	printf '%s' $(($1 * DENSITY / 160))
}

# The ExtendedFloatingActionButton's label is not exposed to uiautomator:
# the FAB is the last clickable node on screen, in its bottom-right quarter.
tap_fab() {
	local point i
	for ((i = 0; i < 10; i++)); do
		point="$(ui_nodes | grep 'clickable="true"' | tail -n1 | bounds_center |
			awk -v w="$SCREEN_W" -v h="$SCREEN_H" '$1 > w / 2 && $2 > h / 2' || true)"
		if [ -n "$point" ]; then
			# shellcheck disable=SC2086
			tap $point
			return 0
		fi
		sleep 1
	done
	die "Floating action button not found."
}

wait_for_text() {
	local label="$1" tries="${2:-30}" i
	for ((i = 0; i < tries; i++)); do
		[ -n "$(find_text "$label")" ] && return 0
		sleep 1
	done
	die "Text not found on screen: \"${label}\""
}

wait_for_prefix() {
	local label="$1" tries="${2:-30}" i
	for ((i = 0; i < tries; i++)); do
		[ -n "$(ui_nodes | node_prefix_center "$label" || true)" ] && return 0
		sleep 0.3
	done
	die "Text not found on screen: \"${label}...\""
}

tap() {
	adb shell input tap "$1" "$2"
}

tap_text() {
	local point
	wait_for_text "$1" "${2:-30}"
	point="$(find_text "$1")"
	# shellcheck disable=SC2086
	tap $point
}

# Slow swipe ending with a pause: zero velocity at release,
# so no inertia, the content stops exactly under the finger.
drag() {
	local x="$1" from="$2" to="$3" steps=12 i cmd
	cmd="input motionevent DOWN ${x} ${from}"
	for ((i = 1; i <= steps; i++)); do
		cmd+="; input motionevent MOVE ${x} $((from + (to - from) * i / steps))"
	done
	cmd+="; sleep 0.4; input motionevent UP ${x} ${to}"
	adb shell "$cmd"
	sleep 1
}

scroll_down() {
	hide_keyboard
	drag $((SCREEN_W / 2)) $((SCREEN_H * 70 / 100)) $((SCREEN_H * 35 / 100))
}

# Scrolls until the label is visible, then taps it.
scroll_to_and_tap() {
	local point i
	hide_keyboard
	for ((i = 0; i < 8; i++)); do
		point="$(find_text "$1")"
		if [ -n "$point" ]; then
			# shellcheck disable=SC2086
			tap $point
			return 0
		fi
		scroll_down
	done
	die "Element not found after scrolling: \"$1\""
}

# Brings the section header just below the top bar (located by its
# back button), to frame a screenshot on the following sections.
scroll_to_top() {
	local res="$1" label="$2" nodes point bar top x y i
	for ((i = 0; i < 8; i++)); do
		nodes="$(ui_nodes)"
		point="$(node_center "$label" <<< "$nodes" || true)"
		if [ -n "$point" ]; then
			read -r x y <<< "$point"
			bar="$(node_bottom "$(string_res "$res" action_back)" <<< "$nodes" || true)"
			top=$((${bar:-$((SCREEN_H / 8))} + $(dp 48)))
			[ "$y" -le "$top" ] || drag "$x" "$y" "$top"
			return 0
		fi
		scroll_down
	done
	die "Section not found after scrolling: \"${label}\""
}

hide_keyboard() {
	if adb shell dumpsys input_method | grep -q 'mInputShown=true'; then
		adb shell input keyevent KEYCODE_BACK
		sleep 1
	fi
}

type_text() {
	adb shell input text "${1// /%s}"
	sleep 0.5
}

fill_field() {
	scroll_to_and_tap "$1"
	sleep 0.5
	type_text "$2"
	hide_keyboard
}

# Chooses an option in a dropdown showing the current value.
choose_option() {
	[ "$1" = "$2" ] && return 0
	scroll_to_and_tap "$1"
	sleep 1
	tap_text "$2" 5
	sleep 1
}

go_back() {
	hide_keyboard
	adb shell input keyevent KEYCODE_BACK
	sleep 1
}

# --- App -------------------------------------------------------------------

reset_app() {
	log "${GREEN}Clearing the app data...${NC}"
	adb shell am force-stop "$PACKAGE"
	adb shell pm clear "$PACKAGE" > /dev/null
	adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS 2> /dev/null || true
}

set_app_locale() {
	adb shell cmd locale set-app-locales "$PACKAGE" --locales "$1"
}

# Launches the app and returns to the list (the help screen opens by itself on first launch).
launch_app() {
	local res="$1" home back nodes stable=0 i
	home="$(string_res "$res" app_name)"
	back="$(string_res "$res" action_back)"
	adb shell am force-stop "$PACKAGE"
	# FLAG_SECURE lifted by the extra (debug APK only), otherwise black screenshots.
	adb shell am start -W -n "$ACTIVITY" --ez screenshots true > /dev/null
	# The help screen may open after the list is displayed: require a stable list.
	for ((i = 0; i < 30; i++)); do
		nodes="$(ui_nodes)"
		if [ -n "$(node_center "$back" <<< "$nodes" || true)" ]; then
			stable=0
			go_back
		elif [ -n "$(node_center "$home" <<< "$nodes" || true)" ]; then
			stable=$((stable + 1))
			[ "$stable" -ge 3 ] && return 0
		fi
		sleep 1
	done
	die "Config list never displayed."
}

wait_for_list() {
	wait_for_text "$(string_res "$1" app_name)"
}

# The lock suggestion appears with a delay above the list.
dismiss_lock_suggestion() {
	local later point i
	later="$(string_res "$1" action_later)"
	for ((i = 0; i < 5; i++)); do
		point="$(find_text "$later")"
		if [ -n "$point" ]; then
			# shellcheck disable=SC2086
			tap $point
			sleep 1
			return 0
		fi
		sleep 1
	done
}

# Opens the editor for a new config of the chosen type from the dialog.
new_config() {
	local res="$1" kind="$2"
	tap_fab
	tap_text "$(string_res "$res" "$kind")"
	wait_for_text "$(string_res "$res" field_nickname)"
}

save_config() {
	hide_keyboard
	tap_fab
	wait_for_list "$1"
}

create_config() {
	local res="$1" nickname server protocol key hmac
	IFS='|' read -r nickname server protocol key hmac <<< "$2"
	log "  ${PURPLE}${nickname}${NC}"
	new_config "$res" kind_fwknop
	fill_field "$(string_res "$res" field_nickname)" "$nickname"
	fill_field "$(string_res "$res" field_server_address)" "$server"
	choose_option "UDP" "$protocol"
	choose_option "$(string_res "$res" source_ip_resolve)" "$(string_res "$res" source_ip_allow)"
	fill_field "$(string_res "$res" field_ip_to_allow)" "$DEMO_SOURCE_IP"
	fill_field "$(string_res "$res" field_rijndael_key)" "$key"
	fill_field "$(string_res "$res" field_hmac_key)" "$hmac"
	save_config "$res"
}

create_wake_config() {
	local res="$1" nickname mac
	IFS='|' read -r nickname mac <<< "$2"
	log "  ${PURPLE}${nickname}${NC}"
	new_config "$res" kind_wake_on_lan
	fill_field "$(string_res "$res" field_nickname)" "$nickname"
	fill_field "$(string_res "$res" field_wake_mac)" "$mac"
	save_config "$res"
}

seed_demo_configs() {
	local res="$1" config
	log "${GREEN}Creating demo configs...${NC}"
	for config in "${DEMO_CONFIGS[@]}"; do
		create_config "$res" "$config"
	done
	for config in "${DEMO_WAKE_CONFIGS[@]}"; do
		create_wake_config "$res" "$config"
	done
	# A first knock, so the history doesn't show just one entry.
	knock "$res" "$KNOCKED"
	sleep 5
}

# Triggers a config and waits for the confirmation (snackbar "Packet sent to...").
knock() {
	local res="$1" nickname="$2"
	tap_text "$(string_res "$res" a11y_knock_config "$nickname")"
	wait_for_prefix "$(string_res "$res" knock_sent "$nickname")"
}

open_card_menu() {
	tap_text "$(string_res "$1" action_more "$2")"
	sleep 1
}

# --- Screenshots -----------------------------------------------------------

TMP_DIR=""
MAGICK=()
IDENTIFY=()
OPTIPNG=""
find_image_tools() {
	if command -v magick > /dev/null; then
		MAGICK=(magick)
		IDENTIFY=(magick identify)
	elif command -v convert > /dev/null && command -v identify > /dev/null; then
		MAGICK=(convert)
		IDENTIFY=(identify)
	else
		die "ImageMagick not found in PATH."
	fi
	OPTIPNG="$(command -v optipng || true)"
}

# Waits until the screen stops moving (two identical images in a row).
stable_screencap() {
	local prev="${TMP_DIR}/prev.png" cur="${TMP_DIR}/cur.png" i
	sleep 1
	adb exec-out screencap -p > "$prev"
	for ((i = 0; i < 10; i++)); do
		sleep 0.5
		adb exec-out screencap -p > "$cur"
		cmp -s "$prev" "$cur" && break
		mv "$cur" "$prev"
	done
	[ "$i" -lt 10 ] || log "  ${RED}Screen still moving, keeping the last image.${NC}"
	mv "$prev" "$1"
}

# Google Play rejects the alpha channel: 24-bit RGB, without date metadata
# so that an unchanged screen gives an identical file.
# capture <file> [now]: "now" grabs the screen without waiting (snackbar).
capture() {
	local file="$1" raw="${TMP_DIR}/raw.png" info size
	if [ "${2:-}" = now ]; then
		adb exec-out screencap -p > "$raw"
	else
		stable_screencap "$raw"
	fi
	"${MAGICK[@]}" "$raw" -alpha off -strip \
		-define png:exclude-chunk=date,time "PNG24:${file}"
	# -nc -nb: keeps 24-bit RGB rather than a palette.
	[ -z "$OPTIPNG" ] || "$OPTIPNG" -quiet -o2 -nc -nb "$file"
	info="$("${IDENTIFY[@]}" -format '%wx%h %[type]' "$file")"
	[ "$info" = "${SCREEN_W}x${SCREEN_H} TrueColor" ] ||
		die "Unexpected screenshot (${info}, expected ${SCREEN_W}x${SCREEN_H} TrueColor): ${file}"
	size="$(stat -c %s "$file")"
	[ "$size" -le "$MAX_BYTES" ] || die "Screenshot larger than 8 MB: ${file}"
	log "  ${GREEN}✅ ${PURPLE}${file#"${PROJECT_DIR}"/}${GREEN} (${info%% *}, $((size / 1024)) KB)${NC}"
}

# At most 8 screenshots (Google Play limit), in store display order.
capture_locale() {
	local fastlane="$1" android="$2" res="$3" form="$4" out
	out="${METADATA_DIR}/${fastlane}/images/${form}Screenshots"
	log "${GREEN}${PURPLE}${fastlane} ${form}${GREEN} screenshots${NC}"
	mkdir -p "$out"
	rm -f "$out"/*.png "$out"/*.jpg
	set_app_locale "$android"
	launch_app "$res"

	# 1. The heart of the app: a knock sent from the config list.
	knock "$res" "$KNOCKED"
	capture "${out}/1_knock.png" now
	sleep 5

	# 2. The three config types: fwknop (SPA), port knocking, Wake-on-LAN.
	tap_fab
	wait_for_text "$(string_res "$res" kind_wake_on_lan)"
	capture "${out}/2_config_types.png"
	tap_text "$(string_res "$res" cancel)"
	sleep 1

	# 3. The editor, framed on the source IP and the Rijndael / HMAC keys.
	tap_text "$KNOCKED"
	wait_for_text "$(string_res "$res" section_server)"
	scroll_to_top "$res" "$(string_res "$res" section_source_ip)"
	capture "${out}/3_config_edit.png"
	go_back
	wait_for_list "$res"

	# 4. The step-by-step test of a config.
	open_card_menu "$res" "$KNOCKED"
	tap_text "$(string_res "$res" action_diagnose)"
	tap_text "$(string_res "$res" diagnostic_run)"
	wait_for_text "$(string_res "$res" diagnostic_success)"
	capture "${out}/4_diagnostic.png"
	go_back
	wait_for_list "$res"

	# 5. The encrypted knock history.
	tap_text "$(string_res "$res" title_history)"
	wait_for_text "$(string_res "$res" section_history_retention)"
	capture "${out}/5_history.png"
	go_back
	wait_for_list "$res"

	# 6. Sharing a config via QR code.
	open_card_menu "$res" "$KNOCKED"
	tap_text "$(string_res "$res" action_export_qr)"
	wait_for_text "$(string_res "$res" title_qr_export)"
	capture "${out}/6_qr_export.png"
	go_back
	wait_for_list "$res"

	# 7. The settings, framed on the locking and encrypted backup.
	tap_text "$(string_res "$res" title_settings)"
	wait_for_text "$(string_res "$res" section_general)"
	scroll_to_top "$res" "$(string_res "$res" section_security)"
	capture "${out}/7_settings.png"
	go_back
	wait_for_list "$res"

	# 8. The built-in help.
	tap_text "$(string_res "$res" title_help)"
	wait_for_text "$(string_res "$res" action_back)"
	capture "${out}/8_help.png"

	adb shell am force-stop "$PACKAGE"
}

capture_form_factor() {
	local form width height density locale fastlane android res
	IFS=':' read -r form width height density <<< "$1"
	set_screen "$width" "$height" "$density"
	enter_demo_mode
	for locale in "${LOCALES[@]}"; do
		IFS=':' read -r fastlane android res <<< "$locale"
		capture_locale "$fastlane" "$android" "$res" "$form"
	done
}

# Configs entered once, in phone format and in English.
prepare_app() {
	set_screen 1080 1920 420
	enter_demo_mode
	reset_app
	set_app_locale en-US
	launch_app values
	dismiss_lock_suggestion values
	seed_demo_configs values
}

main() {
	local build=0 arg entry selected=() forms=()
	for arg in "$@"; do
		case "$arg" in
		--build) build=1 ;;
		-h | --help)
			# Header comment, after the license.
			awk 'NR >= 7 && !/^#/ { exit } NR >= 7 { sub(/^# ?/, ""); print }' "${BASH_SOURCE[0]}"
			exit 0
			;;
		*)
			for entry in "${FORM_FACTORS[@]}"; do
				[ "${entry%%:*}" = "$arg" ] && selected+=("$entry")
			done
			[ "${#selected[@]}" -gt "${#forms[@]}" ] || die "Unknown option or format: ${arg}"
			forms=("${selected[@]}")
			;;
		esac
	done
	[ "${#forms[@]}" -gt 0 ] || forms=("${FORM_FACTORS[@]}")

	command -v adb > /dev/null || die "adb not found in PATH."
	find_image_tools
	pick_emulator
	[ "$build" -eq 0 ] || install_app
	require_installed
	TMP_DIR="$(mktemp -d)"
	trap restore_device EXIT
	set_animations 0
	lock_rotation
	prepare_app
	for entry in "${forms[@]}"; do
		capture_form_factor "$entry"
	done
	reset_app
	log "${GREEN}Done.${NC}"
}

main "$@"
