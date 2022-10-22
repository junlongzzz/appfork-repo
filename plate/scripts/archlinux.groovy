static def checkUpdate(version, platform, args) {
    def response = 'https://archlinux.org/releng/releases/'.toURL().text
    def matcher = response =~ '>([\\d.]+)</a>'
    if (!matcher.find()) {
        return null
    }
    version = matcher[0][1]
    return [
            'version': version,
            'url'    : "https://mirrors.tuna.tsinghua.edu.cn/archlinux/iso/latest/archlinux-${version}-x86_64.iso".toString()
    ]
}
