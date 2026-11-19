#!/usr/bin/env python3
"""Writes llms.txt, llms-full.txt and a Markdown copy of every page into the built documentation site.

Run by scripts/build-docs.sh after `mkdocs build`. The pages are those of mkdocs.yml's nav, read from the staged
sources (target/docs-src) with their code snippets (--8<--) included, so an agent reads the same text as a person.

  <site>/llms.txt        the index (https://llmstxt.org): one link per page, to its Markdown copy
  <site>/llms-full.txt   the pages an agent needs to write a Unified UI Test, in one file
  <site>/<page>.md       each page as Markdown, next to its HTML
"""
import re
import sys
import textwrap
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
SNIPPET = re.compile(r'^(\s*)--8<-- "([^":]+)(?::([\w-]+))?"\s*$')
MARKER = re.compile(r'--8<-- \[(start|end):([\w-]+)\]')
# the nav groups an agent can skip when it is short of context: llms.txt's "Optional" section
OPTIONAL = {"Specification", "Decisions (ADRs)", "Release notes"}
# what llms-full.txt holds: enough to write and read a Unified UI Test
FULL = {"Home", "API reference", "Developer guide", "Glossary"}


class Loader(yaml.SafeLoader):
    """mkdocs.yml names Python objects (!!python/name:…); the nav doesn't need them."""


Loader.add_multi_constructor("tag:yaml.org,2002:python/name:", lambda loader, suffix, node: None)


def snippet(path, section):
    """The file, or its lines between `--8<-- [start:section]` and `--8<-- [end:section]`, dedented."""
    lines = (ROOT / path).read_text(encoding="utf-8").splitlines()
    if section:
        inside, taken = False, []
        for line in lines:
            marker = MARKER.search(line)
            if marker and marker.group(2) == section:
                inside = marker.group(1) == "start"
            elif inside:
                taken.append(line)
        if not taken:
            raise SystemExit(f"llms_txt.py: no section {section} in {path}")
        lines = taken
    lines = [line for line in lines if not MARKER.search(line)]
    return textwrap.dedent("\n".join(lines)).split("\n")


def resolved(markdown):
    """The page with every snippet line replaced by the code it includes."""
    out = []
    for line in markdown.splitlines():
        include = SNIPPET.match(line)
        if include:
            indent, path, section = include.groups()
            out.extend(indent + code if code else code for code in snippet(path, section))
        else:
            out.append(line)
    return "\n".join(out) + "\n"


def description(markdown):
    """The page's first paragraph of prose, as one line: its first sentence, or its first 200 characters."""
    paragraph, fenced = [], False
    markdown = re.sub(r"\A---\n.*?\n---\n", "", markdown, flags=re.DOTALL)   # an ADR's front matter
    for line in markdown.splitlines()[1:]:
        stripped = line.strip()
        if stripped.startswith("```"):
            fenced = not fenced
            continue
        if fenced:
            continue
        if not stripped:
            if paragraph:
                break
            continue
        if paragraph or not stripped.startswith(("#", "|", "-", "*", ">", "!", "<", "=", "1.", "[")):
            paragraph.append(stripped)
    text = " ".join(paragraph)
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)   # links: their text
    text = re.sub(r"\*\*([^*]+)\*\*", r"\1", text)         # bold: its text
    sentence = re.match(r"(.+?[.:])(\s|$)", text)
    text = sentence.group(1).rstrip(":") if sentence and len(sentence.group(1)) > 40 else text
    return text if len(text) <= 200 else text[:199].rstrip() + "…"


def pages(nav, group=None):
    """(group, title, path) for every page of the nav; a page without a title is named by its first heading."""
    for entry in nav:
        if isinstance(entry, str):
            yield group, None, entry
        else:
            (title, target), = entry.items()
            if isinstance(target, list):
                yield from pages(target, title)
            else:
                yield group, title, target


def main():
    config = yaml.load((ROOT / "mkdocs.yml").read_text(encoding="utf-8"), Loader=Loader)
    source, site = ROOT / config["docs_dir"], ROOT / config["site_dir"]
    base = config["site_url"].rstrip("/") + "/"
    sections, full = {}, []
    for group, title, path in pages(config["nav"]):
        markdown = resolved((source / path).read_text(encoding="utf-8"))
        heading = re.search(r"^# (.+)$", markdown, re.MULTILINE)
        if title in (None, "Overview") and heading:
            title = heading.group(1)   # "Overview" says nothing outside its nav group
        title = title or path
        (site / path).parent.mkdir(parents=True, exist_ok=True)
        (site / path).write_text(markdown, encoding="utf-8")
        summary = description(markdown)
        section = "Optional" if group in OPTIONAL else group or "Docs"
        sections.setdefault(section, []).append(f"- [{title}]({base}{path})" + (f": {summary}" if summary else ""))
        if (group or title) in FULL:
            full.append(markdown)

    index = (source / "index.md").read_text(encoding="utf-8")
    lines = [f"# {config['site_name']}", "", f"> {description(index)}", "",
             "Each link is the page as Markdown, with its code examples included. "
             f"[llms-full.txt]({base}llms-full.txt) holds the home page, the API reference, the developer guide "
             "and the glossary in one file. The same API reference is in the `unified-test-api` jar, at "
             "`META-INF/unified/api-reference.md`.", ""]
    for section in ["Docs"] + [s for s in sections if s not in ("Docs", "Optional")] + ["Optional"]:
        if section in sections:
            lines += [f"## {section}", "", *sections[section], ""]
    (site / "llms.txt").write_text("\n".join(lines), encoding="utf-8")
    (site / "llms-full.txt").write_text("\n\n".join(page.rstrip() for page in full) + "\n", encoding="utf-8")
    print(f"llms.txt: {sum(len(v) for v in sections.values())} pages, llms-full.txt: {len(full)} pages", file=sys.stderr)


if __name__ == "__main__":
    main()
