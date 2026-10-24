#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
export JAVA_HOME="$PWD/.tooling/jdk21/Contents/Home"
export ANDROID_HOME="$PWD/.tooling/sdk"
export GRADLE_USER_HOME="$PWD/.tooling/gradle-home"
exec .tooling/gradle-8.13/bin/gradle "$@"
