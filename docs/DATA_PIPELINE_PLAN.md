# GeoNames 地理 Catalog 数据管道计划

## 目标

实现一条可重复、可恢复、可审计的数据处理链路：

```text
GeoNames 官方文件
    ↓
下载到本地 raw 目录
    ↓
校验文件、记录版本和来源
    ↓
解析制表符文本
    ↓
规范化国家和一级行政区
    ↓
质量检查和去重
    ↓
staging 表
    ↓
事务性 upsert 到 catalog_entities
    ↓
报告导入结果
```

第一阶段只处理：

- `countryInfo.txt`：国家基本信息
- `admin1CodesASCII.txt`：一级行政区英文名称和 GeoNames ID

暂不下载 `allCountries.zip`。它适合后续城市、景点和自然地理数据，但文件更大，应该等 catalog 结构和导入流程稳定后再接入。

## 为什么之前会断开

当前工作区没有生成半成品，Git 状态保持干净，因此不是下载文件写坏或代码部分写入导致的。更可能的原因是一次操作同时包含较大的补丁、网络等待和多步命令，工具会话在输出或执行尚未完成时被关闭。

后续执行规则：

1. 设计、代码、下载、导入、验证分成独立步骤。
2. 每个步骤只输出摘要，不打印整份 GeoNames 文件。
3. 下载使用临时文件，完成后原子重命名。
4. 记录 HTTP URL、下载时间、文件大小和 SHA-256。
5. 原始文件和生成文件放在 Git ignored 目录，不提交到仓库。
6. 每个完整阶段单独 commit 和 push。

## 目录设计

```text
data/
  raw/geonames/              # 原始下载文件，不提交
  normalized/geonames/      # 可选的规范化中间文件，不提交
  reports/                   # 导入报告，可按需要保留

backend/
  app/catalog.py             # 数据结构、解析、规范化
  app/db.py                  # catalog_entities SQLAlchemy model
  scripts/download_geonames.py
  scripts/import_geonames.py
  tests/test_catalog_parser.py
```

如果将来需要保留一个可审查的小型 seed，可以只提交测试 fixture 和生成元数据，不提交完整原始下载包。

## 数据源和授权

使用 GeoNames 官方 dump：

- `https://download.geonames.org/export/dump/countryInfo.txt`
- `https://download.geonames.org/export/dump/admin1CodesASCII.txt`
- `https://download.geonames.org/export/dump/readme.txt`

GeoNames dump 是 UTF-8 制表符文本；一级行政区文件包含 `code`、`name`、`name ascii`、`geonameid`。GeoNames 数据采用 CC BY 4.0，产品需要在 About/数据来源页面保留 GeoNames attribution 和链接。

下载器需要保存：

```text
source: geonames
source_url
source_file
downloaded_at
source_version
sha256
byte_size
```

`source_version` 第一阶段可以使用下载日期；如果官方提供明确版本日期，则优先使用官方日期。

## 解析和规范化规则

### 国家

从 `countryInfo.txt` 读取：

- ISO alpha-2 作为标准 code，例如 `US`
- GeoNames country ID 作为 `source_id`
- 国家名称作为默认显示名
- ISO alpha-3、ISO numeric、continent 等放入 metadata

生成：

```text
id: country:US
kind: country
code: US
parent_id: null
source: geonames
source_id: 6252001
```

### 一级行政区

GeoNames code 形如：

```text
US.CA
CN.31
```

规范化为：

```text
id: admin1:US-CA
kind: admin1
code: US.CA
parent_id: country:US
source: geonames
source_id: 5332921
```

不使用显示名称作为 ID。这样可以支持中文名、英文名和其他语言名共存，也避免改名后产生重复成就。

### 必须拒绝的记录

- 缺少 ISO alpha-2 或 GeoNames ID
- code 不是预期格式
- admin1 的父国家不在本次国家 catalog 中
- 空名称
- 同一个 `(source, source_id)` 映射到多个实体
- 同一个 `(kind, code)` 映射到多个实体

所有拒绝记录写入报告，不静默丢弃。

## PostgreSQL 表设计

### catalog_entities

```sql
CREATE TABLE catalog_entities (
    id VARCHAR(160) PRIMARY KEY,
    kind VARCHAR(32) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(300) NOT NULL,
    name_ascii VARCHAR(300),
    parent_id VARCHAR(160),
    source VARCHAR(64) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    source_version VARCHAR(64) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (kind, code),
    UNIQUE (source, source_id)
);
```

`parent_id` 使用逻辑 ID 而不是数据库外键，第一阶段可以避免导入顺序和跨类型层级的耦合；应用层在导入质量检查阶段验证父级存在。

### staging_catalog_entities

正式导入前可以使用 staging 表：

```sql
CREATE TABLE staging_catalog_entities (
    import_run_id UUID NOT NULL,
    row_number INTEGER NOT NULL,
    payload JSONB NOT NULL,
    validation_status VARCHAR(32) NOT NULL,
    validation_error TEXT,
    PRIMARY KEY (import_run_id, row_number)
);
```

第一版可以先在 Python 内存中完成解析和校验；数据量扩大后再落 staging 表。正式表不应直接接收未经校验的原始行。

## 脚本接口

### 下载

```bash
python -m scripts.download_geonames \
  --output-dir ../data/raw/geonames \
  --source-version 2026-02-09

# Include coordinates for countries and first-level administrative regions.
# This downloads the large GeoNames allCountries.zip archive.
python -m scripts.download_geonames \
  --output-dir ../data/raw/geonames \
  --source-version 2026-02-09 \
  --include-coordinates
```

下载脚本行为：

- 默认下载三个官方文件
- `.part` 临时文件下载完成后再 rename
- 已存在且 SHA-256 相同则跳过
- `--force` 才重新下载
- 输出简短摘要，不输出原始数据内容

### 导入

```bash
DATABASE_URL=postgresql+psycopg://... \
python -m scripts.import_geonames \
  --input-dir ../data/raw/geonames \
  --source-version 2026-02-09 \
  --report ../data/reports/geonames-2026-02-09.json

# If allCountries.zip is not in the input directory, pass it explicitly:
python -m scripts.import_geonames \
  --input-dir ../data/raw/geonames \
  --coordinates-file ../data/raw/geonames/allCountries.zip \
  --source-version 2026-02-09 \
  --report ../data/reports/geonames-with-coordinates.json
```

开发环境可以使用 SQLite 验证同一套解析和 upsert 逻辑；生产环境只允许 PostgreSQL/Cloud SQL。

导入脚本必须支持：

- `--dry-run`：只解析和校验，不写数据库
- `--strict`：存在任何错误时退出非零
- 重复执行不产生重复记录
- 同一 `source_version` 重跑结果一致
- 导入前后打印 inserted/updated/skipped/rejected 数量
- 事务失败时不留下半个导入结果

## 导入事务

推荐顺序：

1. 建立 `import_run` 信息和 source metadata。
2. 解析国家。
3. 解析一级行政区。
4. 校验所有 parent、ID 和唯一约束。
5. 写入 staging 或内存中的 validated records。
6. 开启数据库事务。
7. 按稳定 `id` upsert `catalog_entities`。
8. 更新 `source_version`、`updated_at` 和 metadata。
9. 提交事务。
10. 写入导入报告。

任何异常都 rollback，报告标记为 failed；下一次运行可以安全重试。

## 验收标准

- 能下载并保存三个 GeoNames 官方文件的 metadata。
- 能解析全部国家和一级行政区，不因非 ASCII 名称失败。
- `admin1` 的 parent country 全部可解析。
- 重复执行两次，数据库行数不增加。
- 修改一个源记录后重新导入，只更新对应实体。
- `--dry-run` 不写数据库。
- `--strict` 对坏数据返回非零状态码。
- import report 能列出每个阶段的数量和错误。
- GeoNames attribution 被保存到数据来源文档和未来产品 About 页面。

## 实施顺序

1. 先提交本计划。
2. 增加 `catalog_entities` SQLAlchemy model。
3. 实现纯函数解析器和测试 fixture。
4. 实现下载器和下载 metadata。
5. 实现 SQLite dry-run/upsert 验证。
6. 接入 PostgreSQL 驱动和迁移。
7. 用官方小文件跑一次完整导入。
8. 生成报告并 push 独立 commit。
