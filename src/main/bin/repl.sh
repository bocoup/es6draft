#!/bin/bash
#
# Copyright (c) 2012-2013 André Bargull
# Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
#
# <https://github.com/anba/es6draft>
#

#
# Description:
# Helper script to start the simple REPL
#

REL_PATH="$( dirname "$0" )"
BUILD_DIR="${REL_PATH}/../../../target"
CLASSES="${BUILD_DIR}/classes"
DEP_DIR="${BUILD_DIR}/dependencies"
DEPENDENCIES=`ls -1 "${DEP_DIR}" | sed 's,^,'"${DEP_DIR}"'/&,' | sed ':a;{N; s/\n/:/; ta}'`
CLASSPATH="${CLASSES}:${DEPENDENCIES}"
case "`uname`" in
  "CYGWIN"*) CLASSPATH=`cygpath -wp "${CLASSPATH}"` ;;
esac
MAINCLASS="com.github.anba.es6draft.repl.Repl"

if [[ -z "$JAVA_HOME" ]] ; then
  JAVA_CMD="java"
else
  case "`uname`" in
    "CYGWIN"*) JAVA_HOME=`cygpath -u "${JAVA_HOME}"` ;;
  esac
  JAVA_CMD="${JAVA_HOME}/bin/java"
fi
JAVA_OPTS="${JAVA_OPTS:-""}"
JAVA_OPTS="${JAVA_OPTS} -ea -server -XX:+TieredCompilation"
JAVA_VERSION=`${JAVA_CMD} -version 2>&1 | sed 's/java version "\([0-9._]*\).*"/\1/; 1q'`

if [[ "$JAVA_VERSION" < "1.7.0_45" ]] ; then
  JAVA_OPTS="${JAVA_OPTS} -esa"
  JAVA_CLASSPATH="-Xbootclasspath/a:${CLASSPATH}"
else
  JAVA_CLASSPATH="-cp ${CLASSPATH}"
fi

${JAVA_CMD} ${JAVA_OPTS} ${JAVA_CLASSPATH} "${MAINCLASS}" "$@"
