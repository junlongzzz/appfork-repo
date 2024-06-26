import java.util.regex.Matcher

static def checkUpdate(manifest, args) {
    def response = 'https://nginx.org/en/download.html'.toURL().text
    Matcher matcher = response =~ 'nginx/Windows-(?<version>[\\d.]+)'
    def url = [:]
    def versions = []
    for (int i = 0; i < 2; i++) {
        if (!matcher.find()) {
            return null
        }
        def version = matcher.group('version')
        def channel = switch (i) {
            case 0 -> 'Mainline'
            case 1 -> 'Stable'
        }
        // 添加元素到List
        versions << version
        url["nginx-${version}.zip (${channel})".toString()] = "https://nginx.org/download/nginx-${version}.zip".toString()
    }

    return [
            version: String.join('/', versions),
            url    : url
    ]
}
