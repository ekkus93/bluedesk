#!/usr/bin/env bash
set -euo pipefail

# BlueDeck Task 22.6 — exact-SHA physical Classic HID validation.
#
# Run this script on the Linux/BlueZ development host with the paired Android
# phone attached over USB/ADB. The script does not test the current checkout's
# source: it creates a detached temporary worktree at TARGET_SHA so documentation
# or harness commits on master cannot contaminate the physical evidence.

TARGET_SHA="${TARGET_SHA:-953df07df97779c7cc85f3f9bc1acb1e77821c7d}"
HID_HOST_ADDRESS="${HID_HOST_ADDRESS:-E8:FB:1C:25:E4:C2}"
HID_PHONE_ADDRESS="${HID_PHONE_ADDRESS:-8C:6A:3B:5E:D3:48}"
BLUEZ_ADAPTER="${BLUEZ_ADAPTER:-hci0}"
ADB_SERIAL="${ADB_SERIAL:-}"
HOST_CONNECT_WATCH_TIMEOUT_SECONDS="${HOST_CONNECT_WATCH_TIMEOUT_SECONDS:-600}"
APP_ID="com.augustusmachin.android_bt_kbmouse"
HID_UUID="00001124-0000-1000-8000-00805f9b34fb"
PHYSICAL_TEST_CLASS="com.augustusmachin.android_bt_kbmouse.BluetoothHidSendReportTest"
EXPECTED_TESTS=13

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
EVIDENCE_DIR="$REPO_ROOT/build/task22_6_physical_hid_evidence/$TIMESTAMP"
WORKTREE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/bluedeck-task22.6.XXXXXX")"
WATCH_PID=""

mkdir -p "$EVIDENCE_DIR"
exec > >(tee -a "$EVIDENCE_DIR/run.log") 2>&1

cleanup() {
    local rc=$?
    if [[ -n "$WATCH_PID" ]] && kill -0 "$WATCH_PID" 2>/dev/null; then
        kill "$WATCH_PID" 2>/dev/null || true
        wait "$WATCH_PID" 2>/dev/null || true
    fi
    if git -C "$REPO_ROOT" worktree list --porcelain | grep -Fq "worktree $WORKTREE_DIR"; then
        git -C "$REPO_ROOT" worktree remove --force "$WORKTREE_DIR" >/dev/null 2>&1 || true
    else
        rm -rf "$WORKTREE_DIR"
    fi
    echo "Evidence directory: $EVIDENCE_DIR"
    exit "$rc"
}
trap cleanup EXIT INT TERM

fail() {
    echo "ERROR: $*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

normalize_mac() {
    printf '%s' "$1" | tr '[:lower:]' '[:upper:]'
}

for cmd in git adb bluetoothctl dbus-send python3 java; do
    require_command "$cmd"
done

[[ "$(uname -s)" == "Linux" ]] || fail "Task 22.6 host automation currently requires Linux/BlueZ"

HID_HOST_ADDRESS="$(normalize_mac "$HID_HOST_ADDRESS")"
HID_PHONE_ADDRESS="$(normalize_mac "$HID_PHONE_ADDRESS")"

cat <<EOF
BlueDeck Task 22.6 physical Classic HID validation
Target implementation SHA: $TARGET_SHA
Expected Linux host BT:    $HID_HOST_ADDRESS
Expected Android phone BT: $HID_PHONE_ADDRESS
Evidence directory:        $EVIDENCE_DIR
EOF

# Resolve the exact implementation commit without changing the caller's checkout.
if ! git -C "$REPO_ROOT" cat-file -e "${TARGET_SHA}^{commit}" 2>/dev/null; then
    echo "Target SHA is not available locally; fetching it from origin."
    git -C "$REPO_ROOT" fetch --no-tags origin "$TARGET_SHA"
fi
RESOLVED_TARGET="$(git -C "$REPO_ROOT" rev-parse "${TARGET_SHA}^{commit}")"
[[ "$RESOLVED_TARGET" == "$TARGET_SHA" ]] || fail "Target SHA resolved to $RESOLVED_TARGET, expected $TARGET_SHA"

git -C "$REPO_ROOT" worktree add --detach "$WORKTREE_DIR" "$TARGET_SHA" >/dev/null
WORKTREE_HEAD="$(git -C "$WORKTREE_DIR" rev-parse HEAD)"
[[ "$WORKTREE_HEAD" == "$TARGET_SHA" ]] || fail "Detached worktree HEAD is $WORKTREE_HEAD, not $TARGET_SHA"

# Resolve one ADB device unless explicitly selected.
mapfile -t CONNECTED_SERIALS < <(adb devices | awk 'NR > 1 && $2 == "device" {print $1}')
if [[ -z "$ADB_SERIAL" ]]; then
    [[ ${#CONNECTED_SERIALS[@]} -eq 1 ]] || fail "Expected exactly one authorized ADB device; found ${#CONNECTED_SERIALS[@]}. Set ADB_SERIAL explicitly if needed."
    ADB_SERIAL="${CONNECTED_SERIALS[0]}"
fi
ADB=(adb -s "$ADB_SERIAL")
"${ADB[@]}" get-state | grep -qx device || fail "ADB device $ADB_SERIAL is not online/authorized"

PHONE_MODEL="$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')"
PHONE_API="$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
PHONE_ANDROID="$("${ADB[@]}" shell getprop ro.build.version.release | tr -d '\r')"
PHONE_BT_ENABLED="$("${ADB[@]}" shell settings get global bluetooth_on 2>/dev/null | tr -d '\r' || true)"

printf 'ADB serial: %s\nPhone model: %s\nAndroid: %s (API %s)\n' "$ADB_SERIAL" "$PHONE_MODEL" "$PHONE_ANDROID" "$PHONE_API"
[[ "$PHONE_BT_ENABLED" == "1" ]] || fail "Android Bluetooth does not appear enabled (global bluetooth_on=$PHONE_BT_ENABLED)"

# Validate that this is the expected BlueZ host and that the phone is paired.
bluetoothctl show | tee "$EVIDENCE_DIR/bluetoothctl-show.txt"
ACTUAL_HOST_ADDRESS="$(bluetoothctl show | awk '/^Controller / {print toupper($2); exit}')"
[[ -n "$ACTUAL_HOST_ADDRESS" ]] || fail "Could not resolve the local BlueZ controller address"
[[ "$ACTUAL_HOST_ADDRESS" == "$HID_HOST_ADDRESS" ]] || fail "BlueZ host is $ACTUAL_HOST_ADDRESS, expected $HID_HOST_ADDRESS. Override HID_HOST_ADDRESS only if the physical test host intentionally changed."

HOST_POWERED="$(bluetoothctl show | awk -F': ' '/^[[:space:]]*Powered:/ {print tolower($2); exit}')"
[[ "$HOST_POWERED" == "yes" ]] || fail "BlueZ controller is not powered"

bluetoothctl devices | tee "$EVIDENCE_DIR/bluetoothctl-devices.txt"
bluetoothctl devices | awk '{print toupper($2)}' | grep -Fxq "$HID_PHONE_ADDRESS" \
    || fail "Phone $HID_PHONE_ADDRESS is not present in bluetoothctl devices"

bluetoothctl info "$HID_PHONE_ADDRESS" | tee "$EVIDENCE_DIR/bluetoothctl-phone-info-before.txt"
PAIRED="$(bluetoothctl info "$HID_PHONE_ADDRESS" | awk -F': ' '/^[[:space:]]*Paired:/ {print tolower($2); exit}')"
[[ "$PAIRED" == "yes" ]] || fail "Phone $HID_PHONE_ADDRESS is not paired to this BlueZ host"

# Prevent the normal BlueDeck service from holding the one available HID app registration.
"${ADB[@]}" shell am force-stop "$APP_ID" || true
"${ADB[@]}" logcat -c

PHONE_OBJECT="${HID_PHONE_ADDRESS//:/_}"
BLUEZ_OBJECT_PATH="/org/bluez/${BLUEZ_ADAPTER}/dev_${PHONE_OBJECT}"

# Start the host-side connector before Gradle. Logcat was just cleared, so the
# readiness marker can only belong to this run. The Android test itself enforces
# its 90-second post-registration host-connect timeout.
(
    set -euo pipefail
    deadline=$((SECONDS + HOST_CONNECT_WATCH_TIMEOUT_SECONDS))
    echo "Waiting for READY_FOR_HOST_CONNECT marker (up to ${HOST_CONNECT_WATCH_TIMEOUT_SECONDS}s)..." | tee "$EVIDENCE_DIR/host-connect.log"
    while (( SECONDS < deadline )); do
        if "${ADB[@]}" logcat -d BtHidTest:W '*:S' 2>/dev/null | grep -q 'READY_FOR_HOST_CONNECT'; then
            echo "READY_FOR_HOST_CONNECT observed; opening HID profile with ConnectProfile(HID)." | tee -a "$EVIDENCE_DIR/host-connect.log"
            dbus-send --system --print-reply \
                --dest=org.bluez \
                "$BLUEZ_OBJECT_PATH" \
                org.bluez.Device1.ConnectProfile \
                "string:${HID_UUID}" 2>&1 | tee -a "$EVIDENCE_DIR/host-connect.log"
            echo "ConnectProfile(HID) returned success." | tee -a "$EVIDENCE_DIR/host-connect.log"
            exit 0
        fi
        sleep 1
    done
    echo "Timed out waiting for READY_FOR_HOST_CONNECT." | tee -a "$EVIDENCE_DIR/host-connect.log" >&2
    exit 124
) &
WATCH_PID=$!

set +e
(
    cd "$WORKTREE_DIR"
    ./gradlew :app:connectedDebugAndroidTest \
        -Pandroid.testInstrumentationRunnerArguments.class="$PHYSICAL_TEST_CLASS" \
        -Pandroid.testInstrumentationRunnerArguments.runPhysicalHidTests=true \
        -Pandroid.testInstrumentationRunnerArguments.hidHostAddress="$HID_HOST_ADDRESS" \
        -Pandroid.testInstrumentationRunnerArguments.hidPhoneAddress="$HID_PHONE_ADDRESS" \
        --stacktrace
) 2>&1 | tee "$EVIDENCE_DIR/gradle-physical-hid.log"
GRADLE_RC=${PIPESTATUS[0]}
set -e

if kill -0 "$WATCH_PID" 2>/dev/null; then
    # If Gradle terminated before the readiness marker, do not leave the watcher alive.
    kill "$WATCH_PID" 2>/dev/null || true
fi
set +e
wait "$WATCH_PID"
WATCH_RC=$?
set -e
WATCH_PID=""

"${ADB[@]}" logcat -d BtHidTest:W '*:S' > "$EVIDENCE_DIR/bthidtest-logcat.txt" 2>&1 || true
bluetoothctl info "$HID_PHONE_ADDRESS" > "$EVIDENCE_DIR/bluetoothctl-phone-info-after.txt" 2>&1 || true

[[ $GRADLE_RC -eq 0 ]] || fail "Gradle physical HID test failed with exit code $GRADLE_RC; see $EVIDENCE_DIR/gradle-physical-hid.log"
[[ $WATCH_RC -eq 0 ]] || fail "Host ConnectProfile watcher failed with exit code $WATCH_RC; see $EVIDENCE_DIR/host-connect.log"

grep -q 'READY_FOR_HOST_CONNECT' "$EVIDENCE_DIR/bthidtest-logcat.txt" \
    || fail "Physical test never emitted READY_FOR_HOST_CONNECT"
grep -Eq 'onConnectionStateChanged state=2' "$EVIDENCE_DIR/bthidtest-logcat.txt" \
    || fail "Android test logcat does not show HID STATE_CONNECTED"

# The Gradle task can technically succeed when JUnit assumptions skip physical tests.
# Parse Android test XML and require the entire 13-test physical class to execute with
# zero skips/failures/errors.
RESULT_SUMMARY="$EVIDENCE_DIR/test-summary.txt"
python3 - "$WORKTREE_DIR" "$PHYSICAL_TEST_CLASS" "$EXPECTED_TESTS" > "$RESULT_SUMMARY" <<'PY'
import pathlib
import sys
import xml.etree.ElementTree as ET

root = pathlib.Path(sys.argv[1])
class_name = sys.argv[2]
expected = int(sys.argv[3])
xml_roots = [
    root / "app" / "build" / "outputs" / "androidTest-results",
    root / "app" / "build" / "test-results",
]
files = []
for candidate in xml_roots:
    if candidate.exists():
        files.extend(candidate.rglob("*.xml"))

matched_cases = []
failures = 0
errors = 0
skipped = 0
matched_files = []
for path in files:
    try:
        tree = ET.parse(path)
    except ET.ParseError:
        continue
    file_matched = False
    for case in tree.getroot().iter("testcase"):
        classname = case.attrib.get("classname", "")
        if classname != class_name:
            continue
        file_matched = True
        matched_cases.append(case.attrib.get("name", "<unnamed>"))
        failures += len(case.findall("failure"))
        errors += len(case.findall("error"))
        skipped += len(case.findall("skipped"))
    if file_matched:
        matched_files.append(str(path.relative_to(root)))

print(f"class={class_name}")
print(f"tests={len(matched_cases)}")
print(f"failures={failures}")
print(f"errors={errors}")
print(f"skipped={skipped}")
print("xml_files=" + ",".join(matched_files))
for name in sorted(matched_cases):
    print(f"test={name}")

if len(matched_cases) != expected or failures != 0 or errors != 0 or skipped != 0:
    sys.exit(1)
PY
SUMMARY_RC=$?
cat "$RESULT_SUMMARY"
[[ $SUMMARY_RC -eq 0 ]] || fail "Expected exactly $EXPECTED_TESTS executed physical HID tests with failures=0/errors=0/skipped=0"

cat > "$EVIDENCE_DIR/identity.txt" <<EOF
TASK=22.6
TARGET_SHA=$TARGET_SHA
HARNESS_CHECKOUT=$(git -C "$REPO_ROOT" rev-parse HEAD)
HOSTNAME=$(hostname)
HID_HOST_ADDRESS=$HID_HOST_ADDRESS
HID_PHONE_ADDRESS=$HID_PHONE_ADDRESS
ADB_SERIAL=$ADB_SERIAL
PHONE_MODEL=$PHONE_MODEL
PHONE_ANDROID=$PHONE_ANDROID
PHONE_API=$PHONE_API
BLUEZ_ADAPTER=$BLUEZ_ADAPTER
EOF

cat <<EOF

TASK 22.6 PASS
- Exact implementation SHA: $TARGET_SHA
- Physical host: $HID_HOST_ADDRESS
- Android phone: $HID_PHONE_ADDRESS ($PHONE_MODEL, API $PHONE_API)
- ConnectProfile(HID): PASS
- BluetoothHidSendReportTest: $EXPECTED_TESTS/$EXPECTED_TESTS executed, 0 failed, 0 skipped
- Evidence: $EVIDENCE_DIR
EOF
