import org.jsoup.Jsoup

static def checkUpdate(version, platform, args) {
    println('test script...')

    println(checkUpdateTest(version, platform, args))

    return [
            version: 'beta',
            url    : 'https://junlong.plus/ztool/appfork'
    ]
}

static def checkUpdateTest(version, platform, args) {
    // test script here
    def appUrl = 'tsc://0/26980'

    def regex = '>版本：([\\d.]+)<'

    def urlMatcher = appUrl =~ 'tsc://(\\d+)/(\\d+)'
    if (!urlMatcher.find()) {
        return null
    }

    def categoryId = urlMatcher.group(1)
    def appId = urlMatcher.group(2)
    def document = Jsoup.connect("https://pc.qq.com/detail/${categoryId}/detail_${appId}.html").timeout(30000)
            .headers(['User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36 Edg/117.0.2045.41'])
            .get()
    def matcher = document.html() =~ regex
    if (!matcher.find()) {
        return null
    }
    version = matcher.group(1)
    def url = document.selectFirst("a[data-id='${appId}']").attr('href')

    return [
            version: version,
            url    : url
    ]
}