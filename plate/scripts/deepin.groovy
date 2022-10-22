static def checkUpdate(version, platform, args) {
    def response = 'https://www.deepin.org/zh/download/'.toURL().text
    def matcher = response =~ '/releases/([\\d.]+)/deepin-desktop-community-([\\d.]+)-amd64.iso'
    if (!matcher.find()) {
        return null
    }
    version = matcher[0][1]
    return [
            'version': version,
            'url'    : "https://mirrors.tuna.tsinghua.edu.cn/deepin-cd/${version}/deepin-desktop-community-${version}-amd64.iso".toString()
    ]
}
