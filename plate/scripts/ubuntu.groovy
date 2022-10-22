static def checkUpdate(version, platform, args) {
    def html = 'https://cn.ubuntu.com/download/desktop'.toURL().text
    def matcher = html =~ '>Ubuntu ([\\d.]+)<'
    if (!matcher.find()) {
        return null
    }
    version = matcher[0][1]

    return [
            'version': version,
            'url'    : "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-releases/${version}/ubuntu-${version}-desktop-amd64.iso".toString()
    ]
}
