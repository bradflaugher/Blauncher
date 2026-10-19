#!/usr/bin/env python3
"""Check the static site in site/ before it is published to GitHub Pages.

Every page must be well formed (elements closed and properly nested) and
have a title; every local link, asset and anchor must exist inside the
published site; and nothing may load from another origin, from the HTML or
from a stylesheet (the site, like the app, makes no third-party requests).
Links to other sites are fine; loading their scripts, styles, images or
fonts is not. Every page also carries a Content-Security-Policy that stops
scripts from connecting anywhere: it must list the hash of each inline
script (the check prints the right one), and since it allows no inline
styles, pages may not use style attributes or <style> elements. A <base>
element is not allowed at all (it would re-point every relative URL).

Each stylesheet link carries the stylesheet's content hash (`style.css?v=…`),
so a page never pairs with a stale cached copy: Pages lets browsers cache
for ten minutes, and a new page on an old stylesheet renders broken. Run
with --fix to write the current hashes into the pages after a CSS change.

Usage: python3 .github/scripts/check_site.py [site-dir] [--fix]
"""

import base64
import hashlib
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
        self.csp = None
        self.scripts = []  # inline script bodies
        self.styles = []  # stylesheet hrefs

    def _where(self):
        return f"line {self.getpos()[0]}"

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if "id" in a:
            self.ids.add(a["id"])
        if tag not in VOID:
            self._stack.append((tag, self._where()))
        self._in_style = tag == "style"
        if tag == "base":
            self.problems.append(f"{self._where()}: <base> is not allowed")
        if tag == "style" or "style" in a:
            self.problems.append(f"{self._where()}: inline styles are blocked by the CSP; use style.css")
        if tag == "script" and "src" not in a:
            self.scripts.append("")
        if tag == "meta" and (a.get("http-equiv") or "").lower() == "content-security-policy":
            self.csp = a.get("content") or ""
        for name, value in attrs:
            if value is None:
                continue
            if (tag, name) in LOADS:
                for part in value.split(","):
                    self.refs.append((f"<{tag} {name}>", part.strip().split(" ")[0], True))
            elif tag == "link" and name == "href":
                rels = set((a.get("rel") or "").lower().split())
                self.refs.append((f"<{tag} {name}>", value, bool(rels & LOADING_RELS)))
                if "stylesheet" in rels:
                    self.styles.append(value)
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
        if self._stack and self._stack[-1][0] == "script" and self.scripts:
            self.scripts[-1] += data
        if self._stack and self._stack[-1][0] == "title" and data.strip():
            self.title = True
        if self._in_style:
            self.refs.extend(("<style>", u, True) for u in css_urls(data))

    def close(self):
        super().close()
        for tag, where in self._stack:
            self.problems.append(f"<{tag}> from {where} is never closed")


# What every page's Content-Security-Policy must say, besides script-src.
CSP_REQUIRED = {"default-src": "'self'", "connect-src": "'none'", "object-src": "'none'",
                "base-uri": "'none'", "form-action": "'none'", "style-src": "'self'"}


def csp_problems(page):
    if page.csp is None:
        return ["missing <meta http-equiv=\"Content-Security-Policy\">"]
    policy = {}
    for directive in page.csp.split(";"):
        name, *values = directive.split()
        if name:
            policy[name.lower()] = values
    problems = [f"CSP needs {name} {value}" for name, value in CSP_REQUIRED.items()
                if policy.get(name) != [value]]
    hashes = ["'sha256-" + base64.b64encode(hashlib.sha256(body.encode()).digest()).decode() + "'"
              for body in page.scripts]
    script_src = hashes or ["'none'"]
    if policy.get("script-src") != script_src:
        problems.append("CSP script-src must be exactly " + " ".join(script_src))
    return problems


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


def version(css):
    # LF-normalized, so a CRLF checkout stamps the hash CI computes.
    return hashlib.sha256(css.read_bytes().replace(b"\r\n", b"\n")).hexdigest()[:10]


def local_sheet(site, page, href):
    """The file a same-site stylesheet href names, or None."""
    url = urlsplit(href)
    if (url.scheme or url.netloc) and not href.startswith(SITE_URL) or not url.path:
        return None
    target = resolve(site, page.parent, url.path)
    return target if target is not None and target.is_file() else None


def fix_versions(site):
    """Rewrites each local stylesheet href to carry its file's current hash."""
    for page in site.rglob("*.html"):
        html = new = page.read_text(encoding="utf-8")
        parser = Page()
        parser.feed(html)
        for href in parser.styles:
            target = local_sheet(site, page, href)
            if target is not None:
                stamped = urlsplit(href)._replace(query=f"v={version(target)}").geturl()
                new = re.sub(r'(href=["\'])' + re.escape(href) + r'(["\'])',
                             lambda m: f"{m.group(1)}{stamped}{m.group(2)}", new)
        if new != html:
            page.write_text(new, encoding="utf-8")


def main():
    args = [a for a in sys.argv[1:] if a != "--fix"]
    site = Path(args[0] if args else "site").resolve()
    if "--fix" in sys.argv[1:]:
        fix_versions(site)
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
        errors.extend(f"{rel}: {p}" for p in csp_problems(parser))
        for href in parser.styles:
            target = local_sheet(site, page, href)
            if target is not None and urlsplit(href).query != f"v={version(target)}":
                errors.append(f"{rel}: {href} should end ?v={version(target)} "
                              "(run python3 .github/scripts/check_site.py site --fix)")
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
