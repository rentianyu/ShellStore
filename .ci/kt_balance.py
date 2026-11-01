"""Kotlin 括号/引号平衡粗检（不替代编译，只用来抓改动引入的结构性错误）。

要点：必须正确跳过注释、普通字符串、raw string、字符字面量，
以及字符串模板 ${...}（模板里还能再嵌字符串，这是最容易误报的地方）。
用法：python3 .ci/kt_balance.py <file.kt>...
"""
import io
import sys


def skip_string(s, i, line):
    """i 指向起始双引号，返回结束后的位置"""
    i += 1
    while i < len(s):
        c = s[i]
        if c == "\\":
            i += 2
            continue
        if c == "$" and i + 1 < len(s) and s[i + 1] == "{":
            i, line = skip_template(s, i + 2, line)
            continue
        if c == '"':
            return i + 1, line
        if c == "\n":
            line += 1
        i += 1
    return i, line


def skip_char(s, i, line):
    i += 1
    while i < len(s):
        c = s[i]
        if c == "\\":
            i += 2
            continue
        if c == "'":
            return i + 1, line
        if c == "\n":
            line += 1
        i += 1
    return i, line


def skip_template(s, i, line):
    """i 指向 ${ 之后，返回配对 } 之后的位置"""
    depth = 1
    while i < len(s):
        c = s[i]
        if c == '"':
            i, line = skip_string(s, i, line)
            continue
        if c == "'":
            i, line = skip_char(s, i, line)
            continue
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return i + 1, line
        elif c == "\n":
            line += 1
        i += 1
    return i, line


def check(path):
    s = io.open(path, encoding="utf-8").read()
    i, n, line = 0, len(s), 1
    stack = []
    pairs = {")": "(", "]": "[", "}": "{"}
    while i < n:
        c = s[i]
        if c == "\n":
            line += 1
            i += 1
            continue
        if c == "/" and s.startswith("//", i):
            while i < n and s[i] != "\n":
                i += 1
            continue
        if c == "/" and s.startswith("/*", i):
            i += 2
            while i + 1 < n and not s.startswith("*/", i):
                if s[i] == "\n":
                    line += 1
                i += 1
            i += 2
            continue
        if c == '"':
            if s.startswith('"""', i):
                i += 3
                while i < n and not s.startswith('"""', i):
                    if s[i] == "\n":
                        line += 1
                    i += 1
                i += 3
            else:
                i, line = skip_string(s, i, line)
            continue
        if c == "'":
            i, line = skip_char(s, i, line)
            continue
        if c in "([{":
            stack.append((c, line))
        elif c in ")]}":
            if not stack:
                return "EXTRA %s at line %d" % (c, line)
            top, ln = stack.pop()
            if top != pairs[c]:
                return "MISMATCH %s at line %d (opened %s at line %d)" % (c, line, top, ln)
        i += 1
    if stack:
        return "UNCLOSED %s at line %d" % (stack[-1][0], stack[-1][1])
    return None


def main(paths):
    bad = 0
    for p in paths:
        r = check(p)
        if r:
            print("%-72s %s" % (p, r))
            bad += 1
    print("checked %d files, BAD %d" % (len(paths), bad))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
