#!/usr/bin/env bash
# Source from project scripts. Choose JDK 25 without changing the user's shell configuration.
steamstone_java_version() {
  "$1/bin/java" -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p'
}

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]] && [[ "$(steamstone_java_version "$JAVA_HOME")" == 25 ]]; then
  :
elif [[ "$(uname -s)" == Darwin ]]; then
  steamstone_jdk="$(/usr/libexec/java_home -F -v 25 2>/dev/null || true)"
  if [[ -z "$steamstone_jdk" || "$(steamstone_java_version "$steamstone_jdk")" != 25 ]]; then
    steamstone_jdk=""
    for candidate in /opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home /usr/local/opt/openjdk/libexec/openjdk.jdk/Contents/Home; do
      if [[ -x "$candidate/bin/java" && "$(steamstone_java_version "$candidate")" == 25 ]]; then
        steamstone_jdk="$candidate"
        break
      fi
    done
  fi
  export JAVA_HOME="$steamstone_jdk"
fi

if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]] || [[ "$(steamstone_java_version "$JAVA_HOME")" != 25 ]]; then
  echo 'SteamStone requires JDK 25. Set JAVA_HOME to its installation directory; see README.md.' >&2
  exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"
