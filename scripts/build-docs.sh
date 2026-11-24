#!/usr/bin/env bash
# Builds the documentation site (MkDocs Material) into target/site, with llms.txt for agents.
# Stages README.md, GLOSSARY.md and docs/ into target/docs-src so that
# the repository's relative links keep working on the site.
set -euo pipefail
cd "$(dirname "$0")/.."

repo_url="https://github.com/parttio/unified-testing-tool/blob/main"
src=target/docs-src

# every page names the same release version, the latest from docs/releases/
python3 scripts/doc_version.py

rm -rf "$src"
mkdir -p "$src"
cp -r docs/spec docs/adr docs/guide docs/releases "$src"/
# the Unreleased page, assembled from one file per change (docs/releases/unreleased/)
rm -rf "$src"/releases/unreleased
python3 scripts/release_notes.py unreleased "$src"/releases/unreleased.md
cp docs/comparison.md "$src"/comparison.md
cp docs/migrating-from-testbench.md "$src"/migrating-from-testbench.md
cp docs/migrating-from-browserless.md "$src"/migrating-from-browserless.md
cp docs/migrating-from-dramafinder.md "$src"/migrating-from-dramafinder.md
cp GLOSSARY.md "$src"/GLOSSARY.md
# the API reference shipped in the unified-test-api jar, generated from the code (ApiReferenceTest)
cp unified-test-api/src/main/resources/META-INF/unified/api-reference.md "$src"/api-reference.md
cp README.md "$src"/index.md

# README.md sits at the site root: docs/x -> x; the API reference is a page of the site
sed -i -E -e 's#\]\(docs/#](#g' \
  -e 's#\]\(unified-test-api/src/main/resources/META-INF/unified/api-reference\.md#](api-reference.md#g' "$src"/index.md
# Pages under spec/, adr/ and guide/: ../../README.md -> ../index.md, ../../GLOSSARY.md -> ../GLOSSARY.md,
# other ../../ links (source code) -> GitHub
find "$src"/spec "$src"/adr "$src"/guide -name '*.md' -exec sed -i -E \
  -e 's#\]\(\.\./\.\./unified-test-api/src/main/resources/META-INF/unified/api-reference\.md#](../api-reference.md#g' \
  -e 's#\]\(\.\./\.\./README\.md#](../index.md#g' \
  -e 's#\]\(\.\./\.\./GLOSSARY\.md#](../GLOSSARY.md#g' \
  -e "s#\]\(\.\./\.\./#](${repo_url}/#g" \
  -e 's#\]\(\.\./adr\)#](../adr/index.md)#g' {} +
sed -i -E 's#\]\(adr\)#](adr/index.md)#g' "$src"/index.md
# CONTRIBUTING.md is for this repository, not the site: link it on GitHub
sed -i -E "s#\]\(CONTRIBUTING\.md#](${repo_url}/CONTRIBUTING.md#g" "$src"/index.md

# Index page for the ADRs
{
  echo "# Architecture decision records"
  echo
  for f in docs/adr/[0-9]*.md; do
    title=$(grep -m1 '^# ' "$f" | sed 's/^# //')
    echo "- [$(basename "$f" .md | cut -c1-4): $title]($(basename "$f"))"
  done
} > "$src"/adr/index.md

mkdocs build --strict "$@"

# llms.txt, llms-full.txt and each page as Markdown, for agents
python3 scripts/llms_txt.py
