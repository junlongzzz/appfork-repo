static def checkUpdate(version, platform, args) {
    //    def response = "https://raw.fastgit.org/ScoopInstaller/Main/master/bucket/go.json".toURL().text
//    def response = "https://cdn.jsdelivr.net/gh/chawyehsu/dorado@master/bucket/neteasemusic.json".toURL().text
//    def response = "https://ghproxy.com/https://raw.githubusercontent.com/ScoopInstaller/Main/master/bucket/go.json".toURL().text
//    return new JsonSlurper().parseText(response).version
//    def response = 'https://golang.google.cn/dl'.toURL().text
    def response = 'https://go.dev/dl/'.toURL().text
    def matcher = response =~ 'go([\\d.]+)\\.windows-'
    if (!matcher.find()) {
        return null
    }
    version = matcher[0][1]

    return [
            'version': version,
            'url'    : [
                    'amd64_msi': "https://dl.google.com/go/go${version}.windows-amd64.msi".toString(),
                    'amd64_zip': "https://dl.google.com/go/go${version}.windows-amd64.zip".toString(),
                    'arm64_msi': "https://dl.google.com/go/go${version}.windows-arm64.msi".toString(),
                    'arm64_zip': "https://dl.google.com/go/go${version}.windows-arm64.zip".toString(),
            ]
    ]
}
