#!/bin/sh
set -e

GRADLE_VERSION="9.3.1"
DIST_DIR="${HOME}/.gradle/dj-wrapper"
GRADLE_HOME="${DIST_DIR}/gradle-${GRADLE_VERSION}"

if [ ! -x "${GRADLE_HOME}/bin/gradle" ]; then
  mkdir -p "${DIST_DIR}"
  ARCHIVE="${DIST_DIR}/gradle-${GRADLE_VERSION}-bin.zip"
  URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 -o "${ARCHIVE}" "${URL}"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "${ARCHIVE}" "${URL}"
  else
    echo "ERROR: curl or wget is required to bootstrap Gradle ${GRADLE_VERSION}." >&2
    exit 1
  fi

  rm -rf "${GRADLE_HOME}"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "${ARCHIVE}" -d "${DIST_DIR}"
  else
    echo "ERROR: unzip is required to bootstrap Gradle ${GRADLE_VERSION}." >&2
    exit 1
  fi
fi

exec "${GRADLE_HOME}/bin/gradle" "$@"
