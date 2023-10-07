static def checkUpdate(version, platform, args) {
    def response = 'https://maven.apache.org/docs/history.html'.toURL().text
    def matcher = response =~ '<b>([\\d.]+)</b>'
    if (!matcher.find()) {
        return null
    }
    version = matcher.group(1)

    return [
            'version': version,
            'url'    : "https://repo.huaweicloud.com/apache/maven/maven-3/${version}/binaries/apache-maven-${version}-bin.zip".toString()
    ]
}
