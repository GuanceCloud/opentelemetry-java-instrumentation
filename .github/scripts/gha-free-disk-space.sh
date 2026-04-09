#!/bin/bash -e

# GitHub Actions runners have only provide 14 GB of disk space which we have been exceeding regularly
# https://docs.github.com/en/actions/using-github-hosted-runners/about-github-hosted-runners#supported-runners-and-hardware-resources

df -h

# Self-hosted runners may not have passwordless sudo, and often have ample disk space already.
if ! sudo -n true 2>/dev/null; then
  echo "Skipping disk cleanup: passwordless sudo is unavailable on this runner."
  exit 0
fi

sudo -n rm -rf /usr/local/lib/android
sudo -n rm -rf /usr/share/dotnet
sudo -n rm -rf /usr/local/julia*
sudo -n rm -rf /usr/share/swift
sudo -n rm -rf /usr/local/.ghcup
df -h
