static def checkUpdate(version, platform, args) {
    def response = 'https://pc.weixin.qq.com'.toURL().text
    def matcher = response =~ '<span class=\"download-version\">([\\d.]+)</span>'
    if (!matcher.find()) {
        return null
    }

    return [
            'version': matcher[0][1],
            'url'    : 'https://dldir1.qq.com/weixin/Windows/WeChatSetup.exe'
    ]
}
