# AppFork Repository

本仓库为 [AppFork](https://junlong.plus/ztool/appfork) 远程软件仓库，每天通过 [Github Actions](https://github.com/actions) 进行自动同步更新仓库内的软件清单文件

### 仓库目录说明

- `plate-test`: 软件源测试文件存放目录，无其他任何实际意义
- `plate`: 仓库软件源所有相关文件存放目录，又名`盘子`
    - `plate/manifests`: 存放软件仓库清单文件，一个清单文件对应一个软件，由`json`格式编写的文件
    - `plate/scripts`: 存放软件仓库每个软件对应的更新同步脚本代码文件，通过 [`Groovy`](https://github.com/apache/groovy) 语言进行编写和执行
    - `plate/deprecated`: 存放废弃的软件`清单`、`脚本`文件，不会进行自动同步更新和显示在前端页面内
- `src`: 执行仓库软件清单同步更新的`Java`源码，本仓库本身就是一个`Maven`项目

> 项目运行环境要求：`Java 17`、`Maven 3+`

### 工作原理说明

`AppFork`内的每个软件对应`plate/manifests`下的一个清单文件，每个清单文件在`plate/scripts`下都有一个对应或指定的更新脚本，没有对应的脚本文件即代表无需更新，仓库同步代码通过执行脚本内容获取软件最新的版本和下载链接，如有更新就写入至软件清单文件内即可完成软件的更新和同步

[WIP] 清单文件和更新脚本编写格式及规则

### 其他

**Inspired by [Scoop](https://scoop.sh)**

欢迎PR新软件或维护现有软件~
