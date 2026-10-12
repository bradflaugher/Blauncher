#!/usr/bin/env python3
"""Check the static site in site/ before it is published to GitHub Pages.

Every page must be well formed (elements closed and properly nested) and
have a title; every local link, asset and anchor must exist inside the
published site; and nothing may load from another origin, from the HTML or
from a stylesheet (the site, like the app, makes no third-party requests).
Links to other sites are fine; loading their scripts, styles, images or
fonts is not.

Usage: python3 .github/scripts/check_site.py [site-dir]
"""

import re
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlsplit, unquote

SITE_URL = "https://bradflaugher.github.io/Blauncher/"
BASE_PATH = urlsplit(SITE_URL).path  # /Blauncher/

# Attributes that make the browser fetch something when the page loads.
LOADS = {("img", "src"), ("script", "src"), ("source", "src"), ("source", "srcset"),
         ("img", "srcset"), ("iframe", "src"), ("video", "src"), ("audio", "src"),
         ("embed", "src"), ("object", "data"), ("video", "poster"), ("image", "href")}
LOADING_RELS = {"stylesheet", "icon", "apple-touch-icon", "preload", "modulepreload",
                "manifest", "prefetch"}
# Elements with no end tag. Every other element must be closed explicitly:
# the site is hand-written, so an optional end tag left out is a mistake.
VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta",
        "source", "track", "wbr"}
# url(...) and @import in CSS, inline or in a stylesheet.
CSS_URL = re.compile(r"""url\(\s*(['"]?)(.*?)\1\s*\)|@import\s+(['"])(.*?)\3""", re.I)


def css_urls(css):
    for m in CSS_URL.finditer(css):
        yield m.group(2) if m.group(2) is not None else m.group(4)


class Page(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.refs = []  # (what, value, loads)
        self.ids = set()
        self.title = False
        self.problems = []
        self._stack = []
        self._in_style = False

    def _where(self):
        return f"line {self.getpos()[0]}"

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if "id" in a:
            self.ids.add(a["id"])
        if tag not in VOID:
            self._stack.append((tag, self._where()))
        self._in_style = tag == "style"
        for name, value in attrs:
            if value is None:
                continue
            if (tag, name) in LOADS:
                for part in value.split(","):
                    self.refs.append((f"<{tag} {name}>", part.strip().split(" ")[0], True))
            elif tag == "link" and name == "href":
                rels = set((a.get("rel") or "").lower().split())
                self.refs.append((f"<{tag} {name}>", value, bool(rels & LOADING_RELS)))
            elif name == "href":
                self.refs.append((f"<{tag} {name}>", value, False))
            elif name == "style":
                self.refs.extend((f"<{tag} style>", u, True) for u in css_urls(value))
            elif tag == "meta" and name == "content" and a.get("property") == "og:image":
                self.refs.append(("og:image", value, False))
            elif tag == "meta" and name == "content" and (a.get("http-equiv") or "").lower() == "refresh":
                # "5; url=..." navigates by itself, so its target counts as a load.
                target = re.search(r"url\s*=\s*['\"]?([^'\";]*)", value, re.I)
                if target:
                    self.refs.append(("<meta refresh>", target.group(1).strip(), True))

    def handle_startendtag(self, tag, attrs):
        # <x/>: an element that is opened and closed at once.
        self.handle_starttag(tag, attrs)
        if tag not in VOID:
            self._stack.pop()

    def handle_endtag(self, tag):
        if tag in VOID:
            self.problems.append(f"{self._where()}: </{tag}> closes a void element")
            return
        if not self._stack or self._stack[-1][0] != tag:
            open_tag = f"<{self._stack[-1][0]}> from {self._stack[-1][1]}" if self._stack else "nothing"
            self.problems.append(f"{self._where()}: </{tag}> does not match {open_tag}")
            # Recover at the matching element, if there is one, so one slip
            # is reported once.
            for i in range(len(self._stack) - 1, -1, -1):
                if self._stack[i][0] == tag:
                    del self._stack[i:]
                    break
            return
        self._stack.pop()
        if tag == "style":
            self._in_style = False

    def handle_data(self, data):
        if self._stack and self._stack[-1][0] == "title" and data.strip():
            self.title = True
        if self._in_style:
            self.refs.extend(("<style>", u, True) for u in css_urls(data))

    def close(self):
        super().close()
        for tag, where in self._stack:
            self.problems.append(f"<{tag}> from {where} is never closed")


def resolve(site, base, path):
    """Map a same-site URL path to a file in site/; None if it leaves the site.

    `base` is the directory the reference is relative to."""
    if path.startswith("/"):
        if not path.startswith(BASE_PATH):
            return None  # outside this project's Pages path
        target = site / path[len(BASE_PATH):]
    else:
        target = base / path
    target = Path(unquote(str(target))).resolve()
    if target != site and site not in target.parents:
        return None  # ../ climbed out of the published directory
    if target.is_dir() or path.endswith("/"):
        target = target / "index.html"
    return target


def check_refs(site, source, base, refs, ids, errors):
    rel = source.relative_to(site)
    for what, value, loads in refs:
        if not value or value.startswith(("mailto:", "tel:", "data:")):
            continue
        url = urlsplit(value)
        if (url.scheme or url.netloc) and not value.startswith(SITE_URL):
            if loads:
                errors.append(f"{rel}: {what} loads from another origin: {value}")
            continue
        if not url.path:
            # A bare fragment names an element on this page (CSS has none).
            if url.fragment and source.suffix == ".html" and url.fragment not in ids.get(source, set()):
                errors.append(f"{rel}: #{url.fragment} does not exist on this page")
            continue
        target = resolve(site, base, url.path)
        if target is None:
            errors.append(f"{rel}: {value} points outside {BASE_PATH}")
        elif not target.is_file():
            errors.append(f"{rel}: {value} is missing")
        elif url.fragment and target.suffix == ".html" and url.fragment not in ids.get(target, set()):
            errors.append(f"{rel}: {value} names a missing anchor")


def main():
    site = Path(sys.argv[1] if len(sys.argv) > 1 else "site").resolve()
    errors = []
    if not (site / "index.html").is_file() or not (site / "privacy" / "index.html").is_file():
        errors.append("site/index.html and site/privacy/index.html must both exist")

    parsed = {}
    for page in sorted(site.rglob("*.html")):
        parser = Page()
        parser.feed(page.read_text(encoding="utf-8"))
        parser.close()
        parsed[page] = parser
        rel = page.relative_to(site)
        errors.extend(f"{rel}: {p}" for p in parser.problems)
        if not parser.title:
            errors.append(f"{rel}: missing <title>")
    ids = {page: parser.ids for page, parser in parsed.items()}

    for page, parser in parsed.items():
        check_refs(site, page, page.parent, parser.refs, ids, errors)
    # Stylesheets: url() and @import resolve against the stylesheet itself.
    stylesheets = sorted(site.rglob("*.css"))
    for sheet in stylesheets:
        refs = [("url()", u, True) for u in css_urls(sheet.read_text(encoding="utf-8"))]
        check_refs(site, sheet, sheet.parent, refs, ids, errors)

    for error in errors:
        print(f"::error::{error}")
    if errors:
        return 1
    print(f"site OK: {len(parsed)} pages and {len(stylesheets)} stylesheets checked")
    return 0


if __name__ == "__main__":
    sys.exit(main())
