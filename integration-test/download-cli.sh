#!/usr/bin/env bash

set -e

if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    CLI_URL="https://github.com/getstrm/cli/releases/latest/download/pace_linux_amd64.tar.gz"
elif [[ "$OSTYPE" == "darwin"* ]]; then
    # macos
    CLI_URL="https://github.com/getstrm/cli/releases/latest/download/pace_darwin_arm64.tar.gz"
else
    echo "Unsupported OS: $OSTYPE"
    exit 1
fi

curl --silent -L -o - ${CLI_URL} | tar xz > pace && chmod +x pace
