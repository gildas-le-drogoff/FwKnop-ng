#!/usr/bin/env python3
"""
Supprime les commentaires des fichiers sources d'un répertoire (ou d'un fichier).

Un analyseur lexical par famille de langage :
  - C-like (Kotlin, Java, Gradle, JS/TS...) : // et /* */, en respectant les
    chaînes, les caractères, les raw strings Kotlin \"\"\" et les templates ${...}.
  - XML/HTML/SVG : uniquement <!-- -->, CDATA et prologue <?xml ?> intacts.
    Les URLs (xmlns="http://...") ne sont jamais touchées.
  - # (shell, Python, YAML, TOML, properties, ProGuard...) : # en début de mot
    seulement, hors chaînes, heredocs et blocs YAML | / >.

Toujours conservés :
  - les en-têtes de licence en début de fichier (SPDX, Copyright, License...) ;
  - le shebang, la déclaration d'encodage Python et les directives d'outils
    (shellcheck, noqa, ktlint-disable, noinspection, REUSE-IgnoreStart...).
"""

import argparse
import difflib
import os
import re
import sys
from pathlib import Path

LICENSE_RE = re.compile(r"SPDX-|copyright|licen[cs]e|\(c\)|©", re.IGNORECASE)
DIRECTIVE_RE = re.compile(
    r"^(#!|#.*coding[:=])"
    r"|shellcheck\s|noqa|type:\s*ignore|pragma|fmt:\s*(off|on)|ktlint-disable"
    r"|noinspection|NOSONAR|@formatter:|eslint-|prettier-ignore|yamllint\s"
    r"|REUSE-Ignore",
)

# --------------------------------------------------------------------------
# Lexers : renvoient une liste de (début, fin, type) avec type 'line'|'block'
# --------------------------------------------------------------------------


class CLexer:
    def __init__(
        self,
        text,
        nested=False,
        templates=False,
        triple=None,
        backtick=False,
        line_comments=True,
    ):
        self.t = text
        self.n = len(text)
        self.nested = nested  # /* /* */ */ (Kotlin, Rust, Swift, Dart)
        self.templates = templates  # "${ ... }" (Kotlin, Groovy, Dart)
        self.triple = triple  # None | 'raw' (Kotlin) | 'escaped' (Java)
        self.backtick = backtick  # `...${}...` (JS/TS)
        self.line_comments = line_comments
        self.spans = []

    def run(self):
        self.code(0, False)
        return self.spans

    def code(self, i, stop_on_brace):
        t, n = self.t, self.n
        depth = 0
        while i < n:
            c = t[i]
            if c == "/" and i + 1 < n:
                nxt = t[i + 1]
                if nxt == "/" and self.line_comments:
                    j = t.find("\n", i)
                    j = n if j < 0 else j
                    self.spans.append((i, j, "line"))
                    i = j
                    continue
                if nxt == "*":
                    j = self.block(i)
                    self.spans.append((i, j, "block"))
                    i = j
                    continue
            if c == '"':
                if self.triple and t.startswith('"""', i):
                    raw = self.triple == "raw"
                    i = self.string(
                        i + 3, '"""', escapes=not raw, templates=self.templates and raw
                    )
                else:
                    i = self.string(i + 1, '"', True, self.templates)
                continue
            if c == "`" and self.backtick:
                i = self.string(i + 1, "`", True, True)
                continue
            if c == "'":
                i = self.char(i)
                continue
            if stop_on_brace:
                if c == "{":
                    depth += 1
                elif c == "}":
                    if depth == 0:
                        return i + 1
                    depth -= 1
            i += 1
        return i

    def block(self, i):
        t, n = self.t, self.n
        level = 0
        while i < n:
            if t.startswith("/*", i):
                level += 1 if (self.nested or level == 0) else 0
                i += 2
            elif t.startswith("*/", i):
                level -= 1
                i += 2
                if level == 0:
                    return i
            else:
                i += 1
        return n

    def string(self, i, delim, escapes, templates):
        t, n = self.t, self.n
        while i < n:
            c = t[i]
            if escapes and c == "\\":
                i += 2
                continue
            if t.startswith(delim, i):
                if delim == '"""':
                    while t.startswith('""""', i):  # """abc"""" -> 'abc"'
                        i += 1
                return i + len(delim)
            if templates and c == "$" and t.startswith("${", i):
                i = self.code(i + 2, True)
                continue
            if c == "\n" and delim in ('"', "'"):
                return i  # chaîne non terminée : on ne s'emballe pas
            i += 1
        return n

    def char(self, i):
        """'x', '\\n', ou chaîne simple quote (Groovy/JS). Sinon ' isolé (lifetime Rust)."""
        t, n = self.t, self.n
        j = i + 1
        while j < n and t[j] != "\n":
            if t[j] == "\\":
                j += 2
                continue
            if t[j] == "'":
                return j + 1
            j += 1
        return i + 1


def xml_lexer(text):
    spans = []
    i, n = 0, len(text)
    while i < n:
        if text.startswith("<![CDATA[", i):
            j = text.find("]]>", i)
            i = n if j < 0 else j + 3
        elif text.startswith("<?", i):
            j = text.find("?>", i)
            i = n if j < 0 else j + 2
        elif text.startswith("<!--", i):
            j = text.find("-->", i + 4)
            j = n if j < 0 else j + 3
            spans.append((i, j, "block"))
            i = j
        elif text[i] == "<":
            # Balise : on saute les valeurs d'attributs entre quotes
            j = i + 1
            while j < n and text[j] != ">":
                if text[j] in "\"'":
                    k = text.find(text[j], j + 1)
                    j = n if k < 0 else k
                j += 1
            i = j + 1
        else:
            i += 1
    return spans


def hash_lexer(text, mode):
    """
    mode : 'sh' | 'py' | 'yaml' | 'toml' | 'props' | 'generic'
    """
    spans = []
    t, n = text, len(text)
    i = 0
    line_start = True
    heredocs = []  # délimiteurs en attente (sh)
    yaml_block_indent = None

    def word_start(k):
        return k == 0 or t[k - 1] in " \t\n;&|()"

    def quote_opens(k):
        if mode not in ("yaml",):
            return True
        p = k - 1
        while p >= 0 and t[p] in " \t":
            p -= 1
        return p < 0 or t[p] in "\n:-[{,?"

    while i < n:
        c = t[i]

        if line_start:
            line_start = False
            eol = t.find("\n", i)
            eol = n if eol < 0 else eol
            line = t[i:eol]
            # Contenu d'un heredoc shell : intact
            if heredocs:
                delim, strip_tabs = heredocs[0]
                if (line.lstrip("\t") if strip_tabs else line) == delim:
                    heredocs.pop(0)
                i = eol + 1
                line_start = True
                continue
            # Contenu d'un bloc scalaire YAML (| ou >) : intact
            if yaml_block_indent is not None:
                indent = len(line) - len(line.lstrip(" "))
                if line.strip() == "" or indent > yaml_block_indent:
                    i = eol + 1
                    line_start = True
                    continue
                yaml_block_indent = None
            if mode == "props":
                s = line.lstrip()
                if s[:1] in ("#", "!"):
                    k = i + len(line) - len(s)
                    spans.append((k, eol, "line"))
                i = eol + 1
                line_start = True
                continue

        if c == "\n":
            if mode == "yaml":
                ls = t.rfind("\n", 0, i) + 1
                prev = t[ls:i]
                # retirer un éventuel commentaire déjà repéré sur cette ligne
                if spans and spans[-1][0] >= ls:
                    prev = t[ls : spans[-1][0]]
                if re.search(r"(^|[:\-]\s*)[|>][+-]?\d*[+-]?\s*$", prev):
                    yaml_block_indent = len(prev) - len(prev.lstrip(" "))
            i += 1
            line_start = True
            continue

        if c == "#" and (mode in ("py", "toml") or word_start(i)):
            j = t.find("\n", i)
            j = n if j < 0 else j
            spans.append((i, j, "line"))
            i = j
            continue

        if c == "\\" and mode in ("sh", "generic", "py"):
            i += 2
            continue

        if mode == "sh" and t.startswith("<<", i) and not t.startswith("<<<", i):
            m = re.match(r"<<(-?)\s*(['\"]?)([A-Za-z_][\w]*)\2", t[i:])
            if m:
                heredocs.append((m.group(3), m.group(1) == "-"))
                i += m.end()
                continue

        if c in "\"'" or (c == "`" and mode == "sh"):
            if not quote_opens(i):
                i += 1
                continue
            if mode in ("py", "toml") and t.startswith(c * 3, i):
                end = t.find(c * 3, i + 3)
                if mode == "py" and c == '"':
                    # docstring/chaîne triple avec échappements
                    k = i + 3
                    while k < n and not t.startswith(c * 3, k):
                        k += 2 if t[k] == "\\" else 1
                    end = k if k < n else -1
                i = n if end < 0 else end + 3
                continue
            escapes = not (c == "'" and mode in ("sh", "toml"))
            if mode == "yaml" and c == "'":
                escapes = False
            k = i + 1
            while k < n:
                if escapes and t[k] == "\\":
                    k += 2
                    continue
                if t[k] == c:
                    if mode == "yaml" and c == "'" and t.startswith("''", k):
                        k += 2
                        continue
                    break
                if t[k] == "\n" and mode in ("py", "toml", "yaml", "props"):
                    break
                k += 1
            i = k + 1
            continue

        i += 1

    return spans


# --------------------------------------------------------------------------
# Langages
# --------------------------------------------------------------------------


def lexer_for(path):
    name = path.name
    ext = path.suffix.lower()
    if ext in (".kt", ".kts"):
        return lambda s: CLexer(s, nested=True, templates=True, triple="raw").run()
    if ext in (".gradle", ".groovy"):
        return lambda s: CLexer(s, templates=True, triple="raw").run()
    if ext == ".java":
        return lambda s: CLexer(s, triple="escaped").run()
    if ext in (".c", ".h", ".cpp", ".hpp", ".cc", ".go", ".aidl"):
        return lambda s: CLexer(s).run()
    if ext in (".rs", ".swift"):
        return lambda s: CLexer(s, nested=True).run()
    if ext == ".dart":
        return lambda s: CLexer(s, nested=True, templates=True).run()
    if ext in (".js", ".ts", ".mjs", ".cjs"):
        return lambda s: CLexer(s, backtick=True).run()
    if ext in (".css", ".scss"):
        # pas de // en CSS : url(http://...) doit survivre
        return lambda s: CLexer(s, line_comments=(ext == ".scss")).run()
    if ext in (".xml", ".svg", ".html", ".htm", ".xsd", ".plist"):
        return xml_lexer
    if ext in (".sh", ".bash", ".zsh") or name in ("gradlew",):
        return lambda s: hash_lexer(s, "sh")
    if ext == ".py":
        return lambda s: hash_lexer(s, "py")
    if ext in (".yml", ".yaml"):
        return lambda s: hash_lexer(s, "yaml")
    if ext == ".toml":
        return lambda s: hash_lexer(s, "toml")
    if ext == ".properties":
        return lambda s: hash_lexer(s, "props")
    if ext in (".pro", ".cfg", ".conf", ".rb") or name in (
        "Makefile",
        "Dockerfile",
        "CMakeLists.txt",
    ):
        return lambda s: hash_lexer(s, "generic")
    return None


# --------------------------------------------------------------------------
# Sélection des spans à conserver (licence, directives)
# --------------------------------------------------------------------------


def group_spans(text, spans):
    """Regroupe les commentaires ligne consécutifs (sans ligne vide entre eux)."""
    groups = []
    for s in spans:
        if (
            groups
            and s[2] == "line"
            and groups[-1][-1][2] == "line"
            and re.fullmatch(r"[ \t]*\r?\n[ \t]*", text[groups[-1][-1][1] : s[0]])
        ):
            groups[-1].append(s)
        else:
            groups.append([s])
    return groups


def prolog_end(text):
    m = re.match(r"\s*<\?xml.*?\?>", text, re.DOTALL)
    return m.end() if m else 0


def spans_to_remove(text, spans):
    groups = group_spans(text, spans)
    start = prolog_end(text)
    remove = []
    leading = True
    pos = start
    for g in groups:
        g_text = text[g[0][0] : g[-1][1]]
        if leading and text[pos : g[0][0]].strip():
            leading = False
        pos = g[-1][1]
        if leading and LICENSE_RE.search(g_text):
            continue
        for s in g:
            body = text[s[0] : s[1]]
            if DIRECTIVE_RE.search(body):
                continue
            remove.append(s)
    return remove


# --------------------------------------------------------------------------
# Suppression avec nettoyage des lignes
# --------------------------------------------------------------------------


def apply_removals(text, spans):
    for s, e, _ in sorted(spans, reverse=True):
        ls = text.rfind("\n", 0, s) + 1
        le = text.find("\n", e)
        le = len(text) if le < 0 else le
        before, after = text[ls:s], text[e:le]
        if not before.strip() and not after.strip():
            # ligne(s) entièrement commentaire : on supprime la ligne
            if le < len(text):
                text = text[:ls] + text[le + 1 :]
            else:
                text = text[: max(ls - 1, 0)] + text[le:]
        elif not after.strip():
            # commentaire en fin de ligne
            text = text[:ls] + before.rstrip() + text[le:]
        elif not before.strip():
            text = text[:s] + after.lstrip() + text[le:]
        else:
            sep = "" if before[-1].isspace() or after[0].isspace() else " "
            if before[-1].isspace() and after[0].isspace():
                after = after.lstrip()
            text = text[:s] + sep + after + text[le:]
    return text


def remove_comments(text, lexer):
    spans = lexer(text)
    return apply_removals(text, spans_to_remove(text, spans))


# --------------------------------------------------------------------------
# Fichiers
# --------------------------------------------------------------------------

SKIP_DIRS = {"node_modules", "__pycache__", "venv", "env", "dist", "build"}


def process_file(path, dry_run=False, verbose=False, show_diff=False):
    """Renvoie 'modified', 'unchanged', 'skipped' ou 'error'."""
    lexer = lexer_for(path)
    if lexer is None:
        return "skipped"
    try:
        raw = path.read_bytes()
        content = raw.decode("utf-8")
    except UnicodeDecodeError:
        if verbose:
            print(f"⚠️  {path} : pas en UTF-8, ignoré")
        return "skipped"
    except OSError as e:
        print(f"⚠️  Impossible de lire {path}: {e}")
        return "error"

    new_content = remove_comments(content, lexer)
    if new_content == content:
        if verbose:
            print(f"ℹ️  Aucun changement dans {path}")
        return "unchanged"

    if show_diff:
        sys.stdout.writelines(
            difflib.unified_diff(
                content.splitlines(True),
                new_content.splitlines(True),
                f"a/{path}",
                f"b/{path}",
            )
        )
    if dry_run:
        print(f"📝 {path} - des commentaires seraient supprimés")
        return "modified"
    try:
        with open(path, "w", encoding="utf-8", newline="") as f:
            f.write(new_content)
    except OSError as e:
        print(f"❌ Erreur lors de l'écriture de {path}: {e}")
        return "error"
    if verbose:
        print(f"✅ {path} - commentaires supprimés")
    return "modified"


def iter_files(root):
    if root.is_file():
        yield root
        return
    for dirpath, dirs, files in os.walk(root):
        dirs[:] = sorted(
            d for d in dirs if not d.startswith(".") and d not in SKIP_DIRS
        )
        for f in sorted(files):
            if not f.startswith("."):
                yield Path(dirpath) / f


def main():
    parser = argparse.ArgumentParser(
        description="Supprime les commentaires (hors en-têtes de licence et directives)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Exemples:
  %(prog)s app/src
  %(prog)s app/src --dry-run --diff
  %(prog)s app/src/main/AndroidManifest.xml -v
        """,
    )
    parser.add_argument("path", help="Répertoire ou fichier à traiter")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="N'écrit rien, liste les fichiers qui changeraient",
    )
    parser.add_argument(
        "--diff", action="store_true", help="Affiche le diff unifié des modifications"
    )
    parser.add_argument("--verbose", "-v", action="store_true")
    args = parser.parse_args()

    root = Path(args.path)
    if not root.exists():
        print(f"❌ Le chemin '{root}' n'existe pas")
        sys.exit(1)

    stats = {"modified": 0, "unchanged": 0, "skipped": 0, "error": 0}
    for path in iter_files(root):
        stats[process_file(path, args.dry_run, args.verbose, args.diff)] += 1

    print("\n📊 Résumé:")
    print(
        f"   Fichiers analysés : {stats['modified'] + stats['unchanged'] + stats['error']}"
    )
    print(f"   {'À modifier' if args.dry_run else 'Modifiés'} : {stats['modified']}")
    print(f"   Erreurs : {stats['error']}")
    sys.exit(1 if stats["error"] else 0)


if __name__ == "__main__":
    main()
