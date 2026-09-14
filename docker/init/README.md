# 数据库初始化脚本目录

把建表 SQL 放在本目录（如 `01_schema.sql`、`02_seed.sql`），
MySQL 容器**首次启动**时会按文件名顺序自动执行。

注意：脚本修改后需删除数据卷重建才会重新执行：

```bash
docker compose down -v && docker compose up -d
```

> 规范要求：任何涉及数据库的改动，必须同步更新本目录的初始化 SQL（见工程规范 Definition of Done）。
