#!/bin/bash
# Push wiki content to GitHub Wiki
# Run this from the project root

set -e

WIKI_REPO="https://github.com/CyoriaSMP-Team/kalo.wiki.git"
WIKI_DIR=".wiki-temp"

echo "📦 Pushing Kalo Wiki to GitHub..."

# Clean up any existing temp dir
rm -rf "$WIKI_DIR"

# Clone the wiki repo
echo "📥 Cloning wiki repository..."
git clone "$WIKI_REPO" "$WIKI_DIR"

# Copy wiki files
echo "📋 Copying wiki pages..."
cp wiki/*.md "$WIKI_DIR/"

# Push changes
cd "$WIKI_DIR"
git add -A
git commit -m "Update wiki documentation" || echo "No changes to commit"
git push origin main

# Clean up
cd ..
rm -rf "$WIKI_DIR"

echo "✅ Wiki updated successfully!"
echo "🔗 View at: https://github.com/CyoriaSMP-Team/kalo/wiki"
