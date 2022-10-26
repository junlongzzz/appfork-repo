static def checkUpdate(version, platform, args) {
    def pattern = '>Ubuntu ([\\d.]+)<'

    if (args != null && 'lts'.equalsIgnoreCase(args.type as String)) {
        pattern = '>Ubuntu ([\\d.]+) LTS<'
    }

    def html = 'https://ubuntu.com/download/desktop'.toURL().text
    def matcher = html =~ pattern
    if (!matcher.find()) {
        return null
    }
    version = matcher[0][1] as String

    return [
            'version': version,
            'url'    : "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-releases/${version}/ubuntu-${version}-desktop-amd64.iso".toString()
    ]
}
