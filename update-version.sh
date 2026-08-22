#!/bin/bash

# Update Kalo version
# Usage: ./update-version.sh <version> [status]
# Example: ./update-version.sh 0.2.0 "Now Available"
# Example: ./update-version.sh 0.3.0-beta "Beta Release"

VERSION=${1:-"0.1.0"}
STATUS=${2:-"Now Available"}
DATE=$(date +%Y-%m-%d)

cat > docs-site/version.json << EOF
{
  "version": "$VERSION",
  "name": "v$VERSION",
  "status": "$STATUS",
  "releaseDate": "$DATE",
  "features": {
    "abilities": 57,
    "skills": 41,
    "stats": 26,
    "conditions": 9,
    "migrationPlugins": 8,
    "contentTypes": 10
  }
}
