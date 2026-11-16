#!/usr/bin/env bash
# Root delegation wrapper for Gradle
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$DIR/legacy/android/gradlew" ]; then
  cd "$DIR/legacy/android" && exec ./gradlew "$@"
elif [ -f "$DIR/android/gradlew" ]; then
  cd "$DIR/android" && exec ./gradlew "$@"
else
  echo "Error: gradlew not found in legacy/android or android subdirectories"
  exit 1
fi
