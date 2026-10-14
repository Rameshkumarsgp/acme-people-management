#!/bin/sh
# Source this file to put the Node version from frontend/.nvmrc on PATH.
# Git hooks and agent tools run without your interactive shell, so fnm is not loaded there.
# Does nothing when fnm is absent: the Node already on PATH is used.
if command -v fnm >/dev/null 2>&1; then
    NODE_VERSION=$(cat "$(git rev-parse --show-toplevel)/frontend/.nvmrc")
    NODE_BIN_DIR=$(fnm exec --using="$NODE_VERSION" -- sh -c 'dirname "$(command -v node)"' 2>/dev/null)
    [ -n "$NODE_BIN_DIR" ] && export PATH="$NODE_BIN_DIR:$PATH"
fi
