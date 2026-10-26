#!/bin/sh
set -eu
cd "$(dirname "$0")/../../../../.."
export JAVA_HOME="$PWD/.tooling/jdk21/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
fixture_dir="$PWD/verification/local/widget-host-build"
sdk_tools="$PWD/.tooling/sdk/build-tools/36.0.0"
android_jar="$PWD/.tooling/sdk/platforms/android-36/android.jar"
mkdir -p "$fixture_dir/classes" "$fixture_dir/dex"
javac -source 17 -target 17 -classpath "$android_jar" -d "$fixture_dir/classes" app/src/androidTest/fixtures/widget-host/HostActivity.java
"$sdk_tools/d8" --lib "$android_jar" --min-api 31 --output "$fixture_dir/dex" "$fixture_dir"/classes/io/github/dpan538/widgethost/*.class
"$sdk_tools/aapt2" link -I "$android_jar" --manifest app/src/androidTest/fixtures/widget-host/AndroidManifest.xml -o "$fixture_dir/unsigned.apk"
(cd "$fixture_dir/dex" && zip -q "$fixture_dir/unsigned.apk" classes.dex)
"$sdk_tools/zipalign" -f 4 "$fixture_dir/unsigned.apk" "$fixture_dir/aligned.apk"
# Disposable fixture identity. Never uses the product's publishing key.
if [ ! -f "$fixture_dir/fixture.keystore" ]; then keytool -genkeypair -keystore "$fixture_dir/fixture.keystore" -storepass android -keypass android -alias fixture -dname 'CN=Local widget verification' -keyalg RSA -validity 3650 >/dev/null 2>&1; fi
"$sdk_tools/apksigner" sign --ks "$fixture_dir/fixture.keystore" --ks-pass pass:android --key-pass pass:android --out "$fixture_dir/widget-host.apk" "$fixture_dir/aligned.apk"
