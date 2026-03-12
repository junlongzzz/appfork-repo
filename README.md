# AppFork Repository

本仓库为 [AppFork](https://junlong.plus/ztool/appfork) 远程软件仓库，通过 [Github Actions](https://github.com/actions) 进行自动同步更新仓库内的软件清单文件

[![Automatic check repository update](https://github.com/junlongzzz/appfork-repo/actions/workflows/check-update.yml/badge.svg?branch=main)](https://github.com/junlongzzz/appfork-repo/actions/workflows/check-update.yml)
[![GitHub commit activity (branch)](https://img.shields.io/github/commit-activity/m/junlongzzz/appfork-repo/main)](https://github.com/junlongzzz/appfork-repo/commits/main)

### 仓库目录说明

- `plate` : 仓库软件源所有相关文件存放目录（重要），又名 `盘子`
    - `plate/deprecated` : 存放废弃的软件 `清单`、`脚本` 文件，不会进行自动同步更新和显示在前端页面内
    - `plate/manifests` : 存放软件仓库清单文件，一个清单文件对应一个软件，由 `json`/`yaml` 格式编写的文件
    - `plate/manifests-test` : 存放软件仓库清单文件的测试文件，用于测试清单文件的格式和内容
    - `plate/scripts` : 存放软件仓库每个软件对应的更新同步脚本代码文件，通过 [`Groovy`](https://github.com/apache/groovy) 语言进行编写和执行
- `src` : 执行仓库软件清单同步更新的 `Java` 源码，本仓库本身就是一个 `Maven` 项目

> 项目运行环境要求：`Java 21`、`Maven 3.9.0 or above`

### 工作原理说明

`AppFork` 内的每个软件对应 `plate/manifests` 下的一个清单文件，每个清单文件在 `plate/scripts` 下都有一个对应或指定的更新脚本，没有对应的脚本文件即代表无需更新，仓库同步代码通过执行脚本内容获取软件最新的版本和下载链接，如有更新就写入更新至软件清单文件内对应的属性即可完成同步更新

### 清单文件和更新脚本编写格式及规则

本节用于帮助你快速、正确地新增或维护软件条目，尽量做到「可读、可维护、可自动更新」。

> Tips: 可以借助 AI 工具自动生成清单文件，在对应工具中打开本项目，然后通过白话告诉 AI 生成软件清单/脚本等，例如：`添加这个应用：<应用主页或详细描述>，然后使用 AppForkTests 内的 verifyManifests 方法（方法内的 manifestPaths 参数只能包含当前清单文件）对生成的清单文件进行验证测试`

#### 1) 清单文件（`plate/manifests`）

- 一个软件对应一个清单文件，推荐使用 `yaml`（也支持 `json`）
- 文件路径建议：`plate/manifests/<首字母或#>/<软件名>.yaml`
- 文件名应与软件标识一致，尽量全小写，单词用 `-` 连接（如 `git-for-windows.yaml`）

必填字段：

- `name`：软件名称
- `homepage`：官网或项目主页
- `author`：作者/组织
- `description`：简短描述（1 句话，支持 `markdown` 语法）
- `category`：软件分类
- `platform`：软件平台
- `version`：当前版本号（不要加多余文本）
- `url`：下载地址（字符串、列表或键值映射）

固定值字段的可用取值（必填且区分拼写，建议全小写）：

- `category`（软件分类）：
  - `network` 网络应用
  - `chat` 社交沟通
  - `music` 音乐欣赏
  - `video` 视频播放
  - `graphics` 图形图像
  - `games` 游戏娱乐
  - `office` 办公学习
  - `reading` 阅读翻译
  - `development` 编程开发
  - `tools` 系统工具
  - `beautify` 主题美化
  - `others` 其他应用
  - `image` 系统镜像
- `platform`（运行平台）：
  - `windows` Windows
  - `linux` Linux
  - `mac` macOS
  - `android` Android
  - `extensions` 浏览器扩展
  - `other` 其他平台

可选字段：

- `logo`：图标 URL
- `script`：更新脚本配置，支持 3 种写法：
  - 不指定：默认使用与清单文件同名的脚本（如 `git-for-windows.yaml` 对应 `plate/scripts/git-for-windows.groovy`）
  - 字符串：直接指定脚本名（如 `script: appfork-check-update`，对应 `plate/scripts/appfork-check-update.groovy`）
  - 对象（Map）：`name` 指定脚本名，`args` 传递脚本参数（可选）

`script` 对象（Map）示例：

```yaml
script:
  name: appfork-check-update
  args:
    url: https://example.com/api/v1/check_update?app=demoapp
    autoupdate:
      - https://example.com/downloads/demoapp_${version}_windows_amd64.zip
      - https://example.com/downloads/demoapp_${version}_windows_arm64.zip
```

推荐示例（YAML）：

```yaml
name: DemoApp
author: Demo
homepage: https://example.com/logo
description: This is a demo app.
category: development
platform: windows
version: 1.0.0
url:
  - https://example.com/downloads/demoapp_1.0.0_windows_amd64.zip
  - https://example.com/downloads/demoapp_1.0.0_windows_arm64.zip
script:
  name: appfork-check-update
  args:
    url: https://example.com/api/v1/check_update?app=demoapp
    autoupdate:
      - https://example.com/downloads/demoapp_${version}_windows_amd64.zip
      - https://example.com/downloads/demoapp_${version}_windows_arm64.zip
```

#### 2) 更新脚本（`plate/scripts`）

- 脚本语言：`Groovy`
- 文件位置：`plate/scripts/<脚本名>.groovy`
- 类名固定：`UpdateScript`
- 必须实现接口：`ScriptUpdater`
- 方法签名固定：`Object checkUpdate(JSONObject manifest, JSONObject args)`
  - 方法参数：manifest（软件清单文件），args（脚本参数）

返回约定：

- `null`：无更新
- `[version: 'x.y.z', url: ...]`：有更新（`version` 为新版本号，根据实际软件版本格式返回，`url` 支持字符串、列表、映射）
- `[error: '原因']`：更新执行错误

#### 3) 推荐优先使用通用脚本 `appfork-check-update`

大多数软件无需新建脚本，直接在清单里配置：

- `script.name: appfork-check-update`
- `script.args.url`: 检测地址（常用 `github://owner/repo`）
- `script.args.autoupdate`: 用 `${version}` 模板生成下载地址

常见检测方式：

- GitHub 最新正式版：`url: github://owner/repo`
- 自定义提取规则：可补充 `regex` / `jsonpath` / `xpath`
- 多平台多架构：`autoupdate` 使用列表或映射

#### 4) 编写与维护建议

- 描述保持简短准确，不写营销文案
- 版本号保持“可比对”，避免混入前缀文本（如仅保留 `1.2.3`）
- 下载链接优先官方源（GitHub Releases、官网直链等）
- 仅在必要时新增专用 Groovy 脚本，优先复用通用脚本
- 新增清单后建议同步补充 `plate/manifests-test` 测试样例（如项目已有对应流程）

#### 5) 提交前自检清单

- 清单字段完整、缩进正确、无语法错误
- `version` 与 `url` 当前可用
- `autoupdate` 模板可正确替换 `${version}`
- 脚本（如有）返回值符合约定
- 本地执行测试通过（至少验证相关测试项）

### 其他说明

**Inspired by [Scoop](https://scoop.sh)**

欢迎PR新软件或维护现有软件~
