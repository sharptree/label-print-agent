#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

if ! [ -x "$(command -v realpath)" ]; then
  echo 'The realpath command is not available, please install before running the label-print-agent.' >&2
  exit 1
fi

if ! [ -x "$(command -v jsvc)" ]; then
  echo 'The jsvc command is not available, please install before running the label-print-agent.' >&2
  exit 1
fi

# Attempt to find the JAVA_HOME environment variable
if [ -z "$JAVA_HOME" ]; then
  if type -p java; then
    JAVA_HOME="$(dirname "$(dirname "$(realpath "$(type -p java)")")")"
  else
    echo java was not found on the system.
    exit 1
  fi
fi

mkdir -p logs

# Setup variables
EXEC="$(realpath "$(type -p jsvc)")"
CLASS_PATH=$SCRIPT_DIR/label-print-agent.jar
CLASS=io.sharptree.maximo.app.label.LabelPrintAgent
USER=jason
PID=$SCRIPT_DIR/logs/label-print-agent.pid
LOG_OUT=$SCRIPT_DIR/logs/label-print-agent.log
LOG_ERR=$SCRIPT_DIR/logs/label-print-agent.err
CONFIG=$SCRIPT_DIR/label-print-agent.yaml

if ! id -u "$USER" >/dev/null 2>&1; then
    echo "The label print agent service user $USER does not exist."
    exit 1
fi

do_exec()
{
   ./j $EXEC -home $JAVA_HOME -cp $CLASS_PATH -user $USER -outfile $LOG_OUT -errfile $LOG_ERR -pidfile $PID $1 $CLASS -c $CONFIG
}

case "$1" in
    start)
        do_exec
            ;;
    stop)
        do_exec "-stop"
            ;;
    restart)
        if [ -f "$PID" ]; then
            do_exec "-stop"
            do_exec
        else
            echo "service not running, will do nothing"
            exit 1
        fi
            ;;
    *)
            echo "usage: daemon {start|stop|restart}" >&2
            exit 3
            ;;
esac