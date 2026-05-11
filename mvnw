#!/usr/bin/env sh
# ----------------------------------------------------------------------
# Minimal Maven Wrapper for macOS / Linux.
# On first run, downloads Maven into .mvn/wrapper/ and reuses it after.
# ----------------------------------------------------------------------
set -e

MAVEN_VERSION="3.9.9"
BASEDIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_DIR="$BASEDIR/.mvn/wrapper"
MAVEN_HOME="$WRAPPER_DIR/apache-maven-$MAVEN_VERSION"

if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  echo "[mvnw] Maven $MAVEN_VERSION not found locally. Downloading..."
  mkdir -p "$WRAPPER_DIR"
  ZIP="$WRAPPER_DIR/maven.zip"
  URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.zip"

  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$URL" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$URL" -O "$ZIP"
  else
    echo "[mvnw] ERROR: need curl or wget to download Maven" >&2
    exit 1
  fi

  unzip -q "$ZIP" -d "$WRAPPER_DIR"
  rm -f "$ZIP"
  chmod +x "$MAVEN_HOME/bin/mvn"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
