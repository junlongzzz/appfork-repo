import java.util.regex.Matcher

static def checkUpdate(version, platform, args) {
    def response = 'https://nginx.org/en/download.html'.toURL().text
    Matcher matcher = response =~ 'nginx/Windows-([\\d.]+)'
    if (!matcher.find()) {
        return null
    }
    def mainlineVersion = matcher.group(1)

    // 继续往下查找匹配
    if (!matcher.find()) {
        return null
    }
    def stableVersion = matcher.group(1)

    def url = [:]
    url["Mainline:${mainlineVersion}".toString()] = "https://nginx.org/download/nginx-${mainlineVersion}.zip".toString()
    url["Stable:${stableVersion}".toString()] = "https://nginx.org/download/nginx-${stableVersion}.zip".toString()

    return [
            version: "${mainlineVersion}/${stableVersion}".toString(),
            url    : url
    ]
}
