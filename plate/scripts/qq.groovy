import org.jsoup.Jsoup

static def checkUpdate(version, platform, args) {
    def document = Jsoup.parse('https://im.qq.com/download'.toURL(), 30000)
    if (!document) {
        return null
    }

    def params = switch (platform) {
        case 'windows' -> ['#imedit_wordandurl_pctabdownurl', '/QQ([\\d.]+).exe']
        case 'android' -> ['#mb_and', '/Android_([\\d.]+)_']
        default -> null
    }
    if (params == null) {
        return null
    }
    def url = null
    def element = document.selectFirst(params[0])
    if (element) {
        url = element.attr('href')
        def matcher = url =~ params[1]
        if (!matcher.find()) {
            return null
        }
        version = matcher.group(1)
    }

    return [
            version: version,
            url    : url
    ]
}
