#!/usr/bin/env bash

set -e

if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    ln -s $(which awk) awk
    ln -s $(which cut) cut
elif [[ "$OSTYPE" == "darwin"* ]]; then
    # macos
    ln -s $(which gawk) awk
    ln -s $(which gcut) cut
else
    echo "Unsupported OS: $OSTYPE"
    exit 1
fi
