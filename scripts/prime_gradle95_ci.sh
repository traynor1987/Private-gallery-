#!/usr/bin/env bash
set -euo pipefail

prime_kotlin_compiler() (
    # Gradle can reuse a local Maven artifact after checking Maven Central's checksum.
    local_repo="$HOME/.m2/repository/org/jetbrains/kotlin/kotlin-compiler-embeddable/2.3.10"
    jar=kotlin-compiler-embeddable-2.3.10.jar
    digest=ac6a182785345380b5db5085dbeea3f5f0e925e2e100e54ab059b5787d4f6e40
    mkdir -p "$local_repo"
    if printf '%s  %s\n' "$digest" "$local_repo/$jar" | sha256sum --check --status 2>/dev/null; then
        exit 0
    fi
    download=$(mktemp "$local_repo/kotlin-download.XXXXXXXX")
    trap 'rm -f -- "$download"' EXIT
    asset='https://api.github.com/repos/JetBrains/kotlin/releases/assets/598692328'
    curl --fail --location --silent --show-error --connect-timeout 12 --max-time 120 \
        -H 'Accept: application/octet-stream' -o "$download" "$asset?download=$(date +%s)"
    printf '%s  %s\n' "$digest" "$download" | sha256sum --check
    mv "$download" "$local_repo/$jar"
    echo 'Verified pinned Kotlin 2.3.10 compiler JAR'
)

prime_kotlin_compiler

# Seed the wrapper cache from the pinned public Gradle release asset. The wrapper
# also verifies the same digest before unpacking the archive.
properties=gradle/wrapper/gradle-wrapper.properties
archive=gradle-9.5.0-bin.zip
digest=553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746
grep -Fxq "distributionSha256Sum=$digest" "$properties"
url=$(sed -n 's/^distributionUrl=//p' "$properties" | tr -d '\\')
[[ "$url" == "https://downloads.gradle.org/distributions/$archive" ]]
key=$(python3 - "$url" <<'PY'
import hashlib,sys
value=int(hashlib.md5(sys.argv[1].encode()).hexdigest(),16)
key=''
while value:
    value,digit=divmod(value,36)
    key='0123456789abcdefghijklmnopqrstuvwxyz'[digit]+key
print(key)
PY
)
cache="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-9.5.0-bin/$key"
mkdir -p "$cache"
if [[ -d "$cache/gradle-9.5.0" && -f "$cache/$archive.ok" ]]; then exit 0; fi
download=$(mktemp "$cache/gradle-download.XXXXXXXX")
trap 'rm -f -- "$download"' EXIT
asset='https://api.github.com/repos/gradle/gradle-distributions/releases/assets/407375534'
for uri in "$asset?download=$(date +%s)" "$url?download=$(date +%s)" 'https://repo.huaweicloud.com/gradle/gradle-9.5.0-bin.zip'; do
    if curl --fail --location --silent --show-error --connect-timeout 12 --max-time 120 \
        -H 'Accept: application/octet-stream' -o "$download" "$uri" &&
        printf '%s  %s\n' "$digest" "$download" | sha256sum --check --status; then
        mv "$download" "$cache/$archive"
        echo 'Verified pinned Gradle 9.5.0 archive'
        exit 0
    fi
done
echo 'Could not download the pinned Gradle 9.5.0 archive' >&2
exit 1
