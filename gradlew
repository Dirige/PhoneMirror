#!/bin/sh

##############################################################################
# Gradle start up script for POSIX
##############################################################################

APP_HOME=$( cd "${APP_HOME:-./}" > /dev/null && pwd -P ) || exit
APP_BASE_NAME=$(basename "$0")

MAX_FD=maximum
warn () { echo "$*"; } >&2
die () { echo; echo "$*"; echo; exit 1; } >&2

cygwin=false; msys=false; darwin=false; nonstop=false
case "$( uname )" in
  CYGWIN* ) cygwin=true ;; Darwin* ) darwin=true ;;
  MSYS* | MINGW* ) msys=true ;; NonStop* ) nonstop=true ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then JAVACMD=$JAVA_HOME/jre/sh/java
    else JAVACMD=$JAVA_HOME/bin/java; fi
    if [ ! -x "$JAVACMD" ] ; then die "ERROR: JAVA_HOME is invalid: $JAVA_HOME"; fi
else
    JAVACMD=java
    if ! command -v java >/dev/null 2>&1 ; then die "ERROR: JAVA_HOME not set and no java found."; fi
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS -Dorg.gradle.appname="$APP_BASE_NAME" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
