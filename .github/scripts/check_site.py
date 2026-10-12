#!/usr/bin/env python3
"""Check the static site in site/ before it is published to GitHub Pages.

Every page must parse, every local link and asset must exist, and nothing
may load from another origin (the site, like the app, makes no third-party
requests). Links to other sites are fine; loading their scripts, styles,
images or fonts is not.

Usage: python3 .github/scripts/check_site.py [site-dir]
"""

import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlsplit, unquote

SITE_URL = "https://bradflaugher.github.io/Blauncher/"
BASE_PATH = urlsplit(SITE_URL).path  # /Blauncher/

# Attributes that make the browser fetch something when the page loads.
LOADS = {("img", "src"), ("script", "src"), ("source", "src"), ("source", "srcset"),
         ("img", "srcset"), ("iframe", "src"), ("video", "src"), ("audio", "src")}
LOADING_RELS = {"stylesheet", "icon", "apple-touch-icon", "preload", "modulepreload",
                "manifest", "prefetch"}


class Page(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.refs = []  # (tag, attr, value, loads)
        self.ids = set()
        self.title = False
        self._in_title = False

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if "id" in a:
            self.ids.add(a["id"])
        if tag == "title":
            self._in_title = True
        for name, value in attrs:
            if value is None:
                continue
            if (tag, name) in LOADS:
                for part in value.split(","):
                    self.refs.append((tag, name, part.strip().split(" ")[0], True))
            elif tag == "link" and name == "href":
                rels = set((a.get("rel") or "").lower().split())
                self.refs.append((tag, name, value, bool(rels & LOADING_RELS)))
            elif name == "href":
                self.refs.append((tag, name, value, False))
            elif tag == "meta" and name == "content" and a.get("property") == "og:image":
                self.refs.append((tag, "og:image", value, False))

    def handle_endtag(self, tag):
        if tag == "title":
            self._in_title = False

    def handle_data(self, data):
        if self._in_title and data.strip():
            self.title = True


def resolve(site, page, path):
    """Map a same-site URL path to a file under site/, or None."""
    if path.startswith(BASE_PATH):
        target = site / path[len(BASE_PATH):]
    elif path.startswith("/"):
        return None  # outside this project's Pages path
    else:
        target = page.parent / path
    target = Path(unquote(str(target))).resolve()
    if target.is_dir() or str(path).endswith("/"):
        target = target / "index.html"
    return target


def main():
    site = Path(sys.argv[1] if len(sys.argv) > 1 else "site").resolve()
    errors = []
    pages = sorted(site.rglob("*.html"))
    if not (site / "index.html").is_file() or not (site / "privacy" / "index.html").is_file():
        errors.append("site/index.html and site/privacy/index.html must both exist")
    ids = {}
    parsed = {}
    for page in pages:
        parser = Page()
        parser.feed(page.read_text(encoding="utf-8"))
        parser.close()
        parsed[page] = parser
        ids[page] = parser.ids
        if not parser.title:
            errors.append(f"{page.relative_to(site)}: missing <title>")

    for page, parser in parsed.items():
        rel = page.relative_to(site)
        for tag, attr, value, loads in parser.refs:
            if not value or value.startswith(("mailto:", "tel:", "data:")):
                continue
            url = urlsplit(value)
            external = bool(url.scheme or url.netloc)
            if external and not value.startswith(SITE_URL):
                if loads:
                    errors.append(f"{rel}: <{tag} {attr}> loads from another origin: {value}")
                continue
            path = url.path  # for an absolute link to this site, /Blauncher/...
            if not path:
                # A bare fragment: it must name an element on this page.
                if url.fragment and url.fragment not in parser.ids:
                    errors.append(f"{rel}: #{url.fragment} does not exist on this page")
                continue
            target = resolve(site, page, path)
            if target is None:
                errors.append(f"{rel}: {value} points outside {BASE_PATH}")
                continue
            if not target.is_file():
                errors.append(f"{rel}: {value} is missing")
                continue
            if url.fragment and target.suffix == ".html" and url.fragment not in ids.get(target, set()):
                errors.append(f"{rel}: {value} names a missing anchor")

    for error in errors:
        print(f"::error::{error}")
    if errors:
        return 1
    print(f"site OK: {len(pages)} pages checked")
    return 0


if __name__ == "__main__":
    sys.exit(main())
