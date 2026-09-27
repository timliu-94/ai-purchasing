# FieldLedger 农产品收购管理 V1

按 `docs` 产品与研发设计实现的可运行 MVP，包含原生 Android App、REST API 和 Web 管理后台。

## 本地启动

```bash
npm run dev
```

浏览器打开 <http://localhost:8787>，登录账号：`owner` / `demo123`。服务数据保存在 `server/data/database.json`，图片与签名保存在 `web/uploads/`。Android 模拟器通过 `10.0.2.2:8787` 访问同一服务，因此运行 App 前需要保持服务启动。

```bash
./scripts/android.sh build
./scripts/android.sh run
npm test
```

本地实现使用文件持久化代替生产环境的 RDS MySQL，并用本地私有目录模拟 OSS。API、数据关系与状态规则按文档设计，部署时可替换存储适配层。

## MySQL / 阿里云

服务端现已支持 MySQL 8。设置 `DATABASE_URL` 时使用 MySQL，不设置时继续使用 JSON 文件：

```bash
export DATABASE_URL='mysql://fieldledger:password@127.0.0.1:3306/fieldledger'
npm run db:migrate
npm start
```

建表文件位于 `server/db/schema.sql`，采用单公司模式，不包含租户表或 `organization_id`。迁移工具会导入现有 `server/data/database.json`。阿里云 RDS、ECS 内网连接和首次 Workbench CLI 登录步骤见 [阿里云数据库迁移与 Workbench 连接](docs/阿里云数据库迁移与Workbench连接.md)。图片和签名目前仍是磁盘文件，尚未迁移到 OSS。

## Android 开发环境

本目录是可直接用 Android Studio 打开的 Kotlin 原生安卓项目，附带计数器示例、JUnit 单元测试和 Espresso 界面测试。

## 开始开发

```bash
cd /Users/liuchen/ai-purchase
./scripts/android.sh studio
```

等待 Gradle Sync 完成，选择 `app` 和 `Medium Phone API 35`，点击 Run ▶ 运行；点击 Debug 调试断点。源码入口：`app/src/main/java/com/aipurchase/app/MainActivity.kt`。

## 命令行

```bash
./scripts/android.sh doctor       # 检查 Java、ADB、模拟器和 Gradle
./scripts/android.sh build        # 构建调试 APK
./scripts/android.sh test         # 单元测试 + Android Lint
./scripts/android.sh emulator     # 启动模拟器，保持这个终端运行
# 在另一个终端执行，等模拟器进入桌面后：
./scripts/android.sh run          # 编译、安装并启动应用
./scripts/android.sh ui-test      # 在已启动的设备上运行界面测试
./scripts/android.sh devices      # 查看设备
./scripts/android.sh logs         # 查看 Logcat，Ctrl+C 退出
```

也可以 `source scripts/env.sh` 后运行 `./gradlew`、`adb` 或 `emulator`。脚本为当前终端设置 JDK 17，不修改全局 Java 配置。直接执行 Gradle 前请加载环境，因为本机系统 Java 是 23。

连接真机时，开启开发者选项和 USB 调试，并在手机上允许调试授权。多设备连接时使用 `export ANDROID_SERIAL=设备序列号` 指定安装、启动目标；`ui-test` 默认测试所有连接设备。

## 版本与路径

- Android Studio：复用 `/Applications/Android Studio.app` 中的现有版本。
- JDK：Android Studio 内置 17；Kotlin：1.9.24。
- Android Gradle Plugin：8.6.1；Gradle Wrapper：8.7，启用发行包 SHA-256 校验。
- SDK：`/Users/liuchen/Library/Android/sdk`；compile/target SDK 35；Build Tools 35.0.0；最低 Android 8.0（API 26）。
- 模拟器：现有 `Medium_Phone_API_35`，Android 15 / ARM64，使用 macOS 硬件加速。

版本选择匹配本机现有工具，兼容要求见 [Android 官方文档](https://developer.android.com/build/releases/agp-8-6-0-release-notes)。这是开发起点，上架前需另外核对当时的商店要求并配置发布签名。

项目配置和源码保存在本目录；复用系统 SDK、JDK、模拟器，下载的构建依赖缓存位于 `~/.gradle`。换电脑后需要调整 `local.properties`、`scripts/env.sh`，并在 Android Studio 的 Gradle 设置中选择 JDK 17。

## 构建与测试产物

- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 单元测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`
- Lint 报告：`app/build/reports/lint-results-debug.html`
- 界面测试报告：`app/build/reports/androidTests/connected/debug/index.html`

单元测试验证计数递增和恢复初始值；界面测试验证点击按钮更新文本，以及 Activity 重建后计数保留。
