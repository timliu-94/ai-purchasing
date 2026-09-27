# 阿里云数据库迁移与 Workbench 连接

## 推荐拓扑

生产环境推荐 `ECS（Node.js 服务） -> RDS MySQL 8.0（内网地址）`。Android 和 Web 只访问 ECS 上的 HTTP API，不直接开放 MySQL。若暂时把 MySQL 安装在 ECS，本项目也能使用，`DATABASE_URL` 指向 `127.0.0.1` 即可，但备份、高可用和升级需要自行维护。

图片和签名当前仍保存在 `FIELDLEDGER_UPLOADS` 指定的磁盘目录；它们尚未迁移到 OSS，不能只迁移数据库后就删除原目录。

## 第一次配置 Workbench CLI

本机已安装 Workbench CLI。当前没有 `~/.workbench/config.json`，因此还不能查询或登录实例。

1. 在阿里云 RAM 创建专用用户，不要使用主账号 AccessKey。
2. 至少授予 `AliyunECSReadOnlyAccess` 和 `AliyunECSWorkbenchFullAccess`。如采用更严格的自定义策略，应包含实例查询和 `ecs-workbench:*`，并限制到目标资源组。
3. 在本机终端运行 `workbench config`，按提示输入凭证和地域。密钥只输入 CLI，不写入本项目或聊天记录。
4. 验证 profile 和实例：

   ```bash
   workbench config get
   workbench list ecs --region cn-hangzhou --output json
   ```

5. 验证远程命令（替换实例 ID、地域和系统用户）：

   ```bash
   workbench exec -i i-xxxxxxxx --region cn-hangzhou -u root -c "uname -a && node --version" --output json
   workbench connect -i i-xxxxxxxx --region cn-hangzhou -u root
   ```

Workbench CLI 目前只支持 Linux ECS，目标实例需运行云助手 Agent。首次连接可能自动添加来自 `100.104.0.0/16` 到 TCP 22 的内网安全组规则。

## 创建 RDS 数据库

在与 ECS 相同地域、同 VPC 创建 RDS MySQL 8.0，并完成：

- 创建数据库 `fieldledger`，字符集 `utf8mb4`。
- 创建仅拥有该库权限的应用账号 `fieldledger`。
- 把 ECS 所在安全组或私网地址加入 RDS 白名单。
- 应用只使用 RDS 内网连接地址，不向互联网开放 3306。
- 开启自动备份，并妥善保存账号密码到 ECS 的环境变量或密钥管理服务。

## 首次数据导入

先在 ECS 上部署项目并安装生产依赖，然后设置连接串。不要把密码写进 shell 历史；以下仅展示变量名：

```bash
cd /opt/fieldledger
npm ci --omit=dev
export DATABASE_URL='mysql://fieldledger:URL编码后的密码@RDS内网地址:3306/fieldledger'
npm run db:migrate
```

迁移命令会自动创建单公司模式的 9 张业务表，并把 `server/data/database.json` 导入 MySQL。为避免误覆盖，只要目标库已有用户数据，命令就会停止；明确需要全量替换时才临时设置 `ALLOW_DATABASE_REPLACE=yes`。

数据校验：

```bash
mysql --host=RDS内网地址 --user=fieldledger --password --database=fieldledger \
  --execute="SELECT COUNT(*) farmers FROM farmers; SELECT COUNT(*) purchases FROM purchases;"
```

## 启动服务与回退

设置 `DATABASE_URL` 后，`npm start` 使用 MySQL；不设置时继续使用原 JSON 文件。因此切换建议为：停止写入、备份 JSON 和上传目录、执行迁移、校验计数、设置服务环境变量、重启并完成一笔测试交易。

若校验失败，移除 `DATABASE_URL` 并重启即可回到 JSON。迁移成功后仍应保留迁移前备份，直到业务验收完成。
