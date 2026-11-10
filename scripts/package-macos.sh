#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/java-env.sh
./mvnw -B -ntp package -DskipTests
if [[ -e target/dist/SteamStone.app ]]; then
  echo 'target/dist/SteamStone.app already exists. Move it aside before packaging again.' >&2
  exit 1
fi
mkdir -p target/package-input/lib target/dist
cp target/steamstone.jar target/package-input/
cp target/lib/*.jar target/package-input/lib/
cp -R licenses target/package-input/
cat > target/worker-launcher.properties <<'EOF'
main-class=steamstone.steam.nativeapi.NativeWorker
java-options=--enable-native-access=ALL-UNNAMED
EOF
jpackage --add-launcher SteamStoneWorker=target/worker-launcher.properties --type app-image --name SteamStone --app-version 1.0.0 \
  --vendor SteamStone --input target/package-input --main-jar steamstone.jar \
  --main-class steamstone.Main --dest target/dist \
  --java-options '--module-path=$APPDIR/lib' \
  --java-options '--add-modules=javafx.controls' \
  --java-options '--enable-native-access=javafx.graphics' \
  --add-modules java.se,jdk.unsupported,jdk.crypto.ec \
  --mac-package-identifier app.steamstone.desktop
printf '\nCreated %s/target/dist/SteamStone.app\n' "$PWD"
