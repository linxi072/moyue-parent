#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从 moyue-common-migration 的真实 Flyway 迁移脚本生成 H2(MySQL 兼容模式) 专用建表脚本。

背景
----
项目硬性约束禁用 Docker/Testcontainers，集成测试改用 H2。但真实迁移脚本存在
MySQL 与 H2 的语义差异，无法在 H2 上原样执行，主要有两类：

1. **索引名作用域**：MySQL 索引名按「表」作用域（不同表可重名，本项目有 6 组
   重名索引，如 idx_status 出现在 10 处），H2 索引名按「schema」全局唯一 → 冲突。
   直接改名要动 10 个已应用迁移，会破坏既有库的 Flyway 校验，故不改名。
2. **MySQL 专有语法**：SET NAMES / ENGINE=InnoDB / DEFAULT CHARSET / ADD COLUMN ... AFTER /
   MEDIUMTEXT 等，H2 不识别。

因此本脚本做「测试期等价变换」，生成仅供集成测试使用的 H2 schema：
  - 索引名统一加表名前缀并去重（保证 schema 内全局唯一）
  - 剥离/降级 MySQL 专有语法
  - 其它 DDL 语义与生产库保持一致

用法
----
    python tools/gen-h2-schema.py

输出：各业务模块的 src/test/resources/db/h2/V1__h2_schema.sql
（测试用 spring.flyway.locations=classpath:db/h2 挂载，与生产的 classpath:db/migration 隔离）

schema 变更后需重新生成本文件。
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MIGRATION_DIR = ROOT / "moyue-common" / "moyue-common-migration" / "src" / "main" / "resources" / "db" / "migration"

# 输出目标：需要跑 DB 集成测试的模块
OUTPUT_MODULES = [
    "moyue-modules/moyue-content",
    "moyue-modules/moyue-social",
    "moyue-modules/moyue-commerce",
    "moyue-modules/moyue-system",
]
OUTPUT_REL = Path("src") / "test" / "resources" / "db" / "h2" / "V1__h2_schema.sql"

HEADER = """-- 本文件由 tools/gen-h2-schema.py 从 db/migration 自动生成，请勿手工编辑。
-- 用途：集成测试（H2 MySQL 兼容模式）建表，与生产迁移脚本 classpath:db/migration 隔离。
-- 变换：索引名加表名前缀去重（H2 索引名 schema 全局唯一）；剥离 SET NAMES / ENGINE / CHARSET /
--       ADD COLUMN ... AFTER / MEDIUMTEXT 等 MySQL 专有语法。
-- schema 变更后请重新执行：python tools/gen-h2-schema.py
"""


def migration_key(path: Path):
    m = re.match(r"V(\d+)__", path.name)
    return int(m.group(1)) if m else 0


# MySQL 会话级语句，H2 无对应实现
SKIP_STMT = re.compile(
    r"(?i)^(SET\s+(NAMES|SESSION\s+storage_engine|storage_engine|FOREIGN_KEY_CHECKS|SQL_MODE|character_set\w*|NAMES\s+\w+)"
    r"|USE\s|LOCK\s+TABLES|UNLOCK\s+TABLES|CREATE\s+DATABASE|DROP\s+DATABASE)\b"
)


def strip_mysql_only(stmt: str) -> str:
    """剥离单条语句中 H2 不支持的 MySQL 专有语法。"""
    s = " ".join(stmt.split())
    # ALTER TABLE ... ADD COLUMN xxx ... AFTER yyy  → 去掉 AFTER 子句
    s = re.sub(r"(?i)\s+AFTER\s+`?[A-Za-z0-9_]+`?(?=\s*(,|;|$))", "", s)
    # 大字段类型降级（H2 无 MEDIUMTEXT/LONGTEXT/TINYTEXT，统一 TEXT）
    s = re.sub(r"(?i)\b(MEDIUMTEXT|LONGTEXT|TINYTEXT)\b", "TEXT", s)
    # 表选项：仅保留 COMMENT
    s = re.sub(r"(?i)\bENGINE\s*=\s*\w+\s*", "", s)
    s = re.sub(r"(?i)\bDEFAULT\s+CHARSET\s*=\s*\w+\s*", "", s)
    s = re.sub(r"(?i)\bCHARSET\s*=\s*\w+\s*", "", s)
    s = re.sub(r"(?i)\bCOLLATE\s*=\s*[\w]+\s*", "", s)
    s = re.sub(r"(?i)\bROW_FORMAT\s*=\s*\w+\s*", "", s)
    s = re.sub(r"(?i)\bAUTO_INCREMENT\s*=\s*\d+\s*", "", s)
    # 表选项剥离后残留的空逗号
    s = re.sub(r"\(\s*,", "(", s)
    s = re.sub(r",\s*,", ",", s)
    s = re.sub(r",\s*\)", ")", s)
    return s.strip()


def split_top_level(s: str):
    """按「括号深度 0 且不在字符串内」的逗号切分。"""
    parts, cur, depth, inq, i = [], "", 0, False, 0
    while i < len(s):
        ch = s[i]
        if inq:
            cur += ch
            if ch == "'":
                inq = False
            i += 1
            continue
        if ch == "'":
            inq = True
            cur += ch
            i += 1
            continue
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append(cur)
            cur = ""
            i += 1
            continue
        cur += ch
        i += 1
    parts.append(cur)
    return [p.strip() for p in parts if p.strip()]


def to_statements(sql: str):
    """把迁移脚本切成语句列表（去掉 -- 行注释，按 ; 切分）。"""
    lines = []
    for line in sql.splitlines():
        idx = line.find("--")
        if idx >= 0:
            line = line[:idx]
        lines.append(line)
    body = "\n".join(lines)
    return [s.strip() for s in body.split(";") if s.strip()]


class IndexRenamer:
    """给索引名加表名前缀，保证 H2 schema 内全局唯一。

    处理三种形态：
      - CREATE TABLE 内联：KEY `idx_x` (...)  / UNIQUE KEY `uk_x` (...)
      - ALTER TABLE 追加：ADD INDEX/KEY `idx_x` (...) / ADD UNIQUE ...
      - 独立语句：CREATE [UNIQUE] INDEX `idx_x` ON tbl (...)
    """

    def __init__(self):
        self.used = set()
        self.table = None
        self.renames = []

    def uniq(self, name: str) -> str:
        base = f"{self.table}_{name}" if self.table else name
        cand, i = base, 2
        while cand.lower() in self.used:
            cand = f"{base}_{i}"
            i += 1
        self.used.add(cand.lower())
        return cand

    def apply(self, stmt: str) -> str:
        m = re.match(r"(?i)^CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([A-Za-z0-9_]+)`?", stmt)
        if m:
            self.table = m.group(1)
        else:
            m = re.match(r"(?i)^ALTER\s+TABLE\s+`?([A-Za-z0-9_]+)`?", stmt)
            self.table = m.group(1) if m else None

        def repl(mt):
            prefix, name = mt.group(1), mt.group(2)
            new = self.uniq(name)
            if new != name:
                self.renames.append((name, new))
            return f"{prefix} `{new}`"

        stmt = re.sub(r"(?i)\b((?:UNIQUE\s+)?KEY)\s+`([A-Za-z0-9_]+)`", repl, stmt)
        stmt = re.sub(r"(?i)\b(ADD\s+(?:UNIQUE\s+)?(?:INDEX|KEY))\s+`([A-Za-z0-9_]+)`", repl, stmt)
        stmt = re.sub(r"(?i)\b(CREATE\s+(?:UNIQUE\s+)?INDEX)\s+`?([A-Za-z0-9_]+)`?", repl, stmt)
        return stmt


def main():
    if not MIGRATION_DIR.is_dir():
        print(f"[ERROR] 迁移目录不存在: {MIGRATION_DIR}", file=sys.stderr)
        return 1

    files = sorted(MIGRATION_DIR.glob("V*__*.sql"), key=migration_key)
    if not files:
        print("[ERROR] 未找到迁移脚本", file=sys.stderr)
        return 1

    renamer = IndexRenamer()
    parts, skipped, split_cnt = [], 0, 0
    for f in files:
        stmts = []
        for raw in to_statements(f.read_text(encoding="utf-8")):
            if SKIP_STMT.match(raw):
                skipped += 1
                continue
            stmt = strip_mysql_only(raw)
            if not stmt:
                continue
            # H2 的 ALTER TABLE 一次只能做一个动作：多动作语句拆成多条
            m = re.match(r"(?i)^ALTER\s+TABLE\s+(`?[A-Za-z0-9_]+`?)\s+(.*)$", stmt, re.S)
            if m and re.search(r"(?i)\b(ADD|DROP|MODIFY|CHANGE|ALTER|RENAME)\b", m.group(2)):
                actions = split_top_level(m.group(2))
                if len(actions) > 1:
                    split_cnt += len(actions) - 1
                    for a in actions:
                        stmts.append(renamer.apply(f"ALTER TABLE {m.group(1)} {a}"))
                    continue
            stmts.append(renamer.apply(stmt))
        if stmts:
            parts.append(f"-- ============ {f.name} ============\n" + ";\n".join(stmts) + ";")

    content = HEADER + "\n" + "\n\n".join(parts) + "\n"

    written = []
    for mod in OUTPUT_MODULES:
        target = ROOT / mod / OUTPUT_REL
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8", newline="\n")
        written.append(target)

    print(f"[OK] 源迁移 {len(files)} 个：{files[0].name} .. {files[-1].name}")
    print(f"[OK] 索引改名 {len(renamer.renames)} 处（H2 索引名 schema 全局唯一，MySQL 按表作用域不冲突）")
    print(f"[OK] 拆分多动作 ALTER {split_cnt} 条；跳过 MySQL 会话语句 {skipped} 条")
    for t in written:
        print(f"[OK] 生成 {t.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
