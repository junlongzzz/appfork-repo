# AppFork Repository

本仓库为 [AppFork](https://junlong.plus/ztool/appfork) 远程软件仓库，通过 [Github Actions](https://github.com/actions) 进行自动同步更新仓库内的软件清单文件

[![Automatic check repository update](https://github.com/junlongzzz/appfork-repo/actions/workflows/check-update.yml/badge.svg?branch=main)](https://github.com/junlongzzz/appfork-repo/actions/workflows/check-update.yml)
[![GitHub commit activity (branch)](https://img.shields.io/github/commit-activity/m/junlongzzz/appfork-repo/main)](https://github.com/junlongzzz/appfork-repo/commits/main)

### 仓库目录说明

- `plate` : 仓库软件源所有相关文件存放目录（重要），又名 `盘子`
    - `plate/deprecated` : 存放废弃的软件 `清单`、`脚本` 文件，不会进行自动同步更新和显示在前端页面内
    - `plate/manifests` : 存放软件仓库清单文件，一个清单文件对应一个软件，由 `json` 格式编写的文件
    - `plate/manifests-test` : 存放软件仓库清单文件的测试文件，用于测试清单文件的格式和内容
    - `plate/scripts` : 存放软件仓库每个软件对应的更新同步脚本代码文件，通过 [`Groovy`](https://github.com/apache/groovy) 语言进行编写和执行
- `src` : 执行仓库软件清单同步更新的 `Java` 源码，本仓库本身就是一个 `Maven` 项目

> 项目运行环境要求：`Java 21`、`Maven 3.9.0 or above`

### 工作原理说明

`AppFork` 内的每个软件对应 `plate/manifests` 下的一个清单文件，每个清单文件在 `plate/scripts` 下都有一个对应或指定的更新脚本，没有对应的脚本文件即代表无需更新，仓库同步代码通过执行脚本内容获取软件最新的版本和下载链接，如有更新就写入更新至软件清单文件内对应的属性即可完成同步更新

[WIP] 清单文件和更新脚本编写格式及规则

### 其他说明

**Inspired by [Scoop](https://scoop.sh)**

欢迎PR新软件或维护现有软件~
