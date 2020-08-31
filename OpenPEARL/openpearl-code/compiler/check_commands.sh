#!/bin/bash

NEEDED_COMMANDS="jlex byaccj ant javac"

for cmd in ${NEEDED_COMMANDS} ; do
    if ! command -v ${cmd} &> /dev/null ; then
        echo Please install ${cmd}!
        exit -1
    fi
done




# check_for_antlr

touch .cmd_ok
