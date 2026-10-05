#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

for variable in MWALAB_RELEASE_STORE_FILE MWALAB_RELEASE_STORE_PASSWORD \
    MWALAB_RELEASE_KEY_ALIAS MWALAB_RELEASE_KEY_PASSWORD; do
    if [[ -z "${!variable:-}" ]]; then
        printf 'Missing operator signing input: %s\n' "$variable" >&2
        exit 2
    fi
done

if [[ "${MWALAB_RELEASE_STORE_FILE}" != /* || ! -f "${MWALAB_RELEASE_STORE_FILE}" ]]; then
    printf 'Release keystore must be an existing absolute file outside the repository.\n' >&2
    exit 2
fi

./gradlew :app:assembleRelease --rerun-tasks --console=plain

apk=app/build/outputs/apk/release/app-release.apk
build_tools="${ANDROID_HOME:?ANDROID_HOME is required}/build-tools/36.0.0"
apksigner="$build_tools/apksigner"
aapt="$build_tools/aapt"
[[ -f "$apk" && -x "$apksigner" && -x "$aapt" ]]
verification="$($apksigner verify --verbose --print-certs "$apk")"
expected_cert=a3745b48d28baac2a230360f32dcf9671d5abeb9a6ca81f458eb4b8667ecc4e4
if [[ "$verification" != *"Signer #1 certificate SHA-256 digest: $expected_cert"* ]]; then
    printf 'Signer certificate differs from the frozen Phase 10 identity; refusing RC2.\n' >&2
    exit 1
fi
badging="$($aapt dump badging "$apk")"
if [[ "$badging" != *"package: name='dev.mwalab' versionCode='1' versionName='0.1.0-clockin'"* ]]; then
    printf 'Release package/version mismatch; refusing RC2.\n' >&2
    exit 1
fi
permissions="$($aapt dump permissions "$apk")"
if [[ "$permissions" != *"android.permission.INTERNET"* ||
      "$permissions" == *"android.permission.CAMERA"* ]]; then
    printf 'Unexpected release permissions; refusing RC2.\n' >&2
    exit 1
fi

output_dir="$HOME/Downloads/MWA_LAB_RC2_$(date +%F)"
output="$output_dir/MWA-Lab-v0.1.0-clockin-rc2.apk"
mkdir -p "$output_dir"
if [[ -e "$output" ]]; then
    if ! cmp -s "$apk" "$output"; then
        printf 'Existing RC2 path has different bytes; refusing overwrite.\n' >&2
        exit 1
    fi
else
    install -m 0644 "$apk" "$output"
fi
sha256sum "$output" > "$output.sha256"
printf 'Signed RC2: %s\n' "$output"
sha256sum -c "$output.sha256"
printf 'Signer certificate SHA-256: %s\n' "$expected_cert"
