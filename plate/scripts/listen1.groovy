import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def repo = null
    switch (platform) {
        case 'windows':
        case 'linux':
        case 'mac':
            repo = 'listen1_desktop'
            break
        case 'android':
            repo = 'listen1_mobile'
            break
        case 'extensions':
            repo = 'listen1_chrome_extension'
            break
        default:
            return null
    }
    def response = "https://api.github.com/repos/listen1/${repo}/releases/latest".toURL().text
    def result = new JsonSlurper().parseText(response)
    def tagName = result.tag_name as String
    version = tagName.replaceFirst('[vV]', '')

    def url = [:]
    if (platform == 'extensions') {
        url['Install on Chrome'] = 'https://chrome.google.com/webstore/detail/listen-1/indecfegkejajpaipjipfkkbedgaodbp'
        url['Install on Microsoft Edge'] = 'https://microsoftedge.microsoft.com/addons/detail/hneiglcmpeedblkmbndhfbeahcpjojjg'
        url['Install on FireFox'] = 'https://addons.mozilla.org/zh-CN/firefox/addon/listen1/'
    } else {
        for (asset in result.assets) {
            def name = asset.name as String
            if (name.endsWithIgnoreCase('.yml') ||
                    name.endsWithIgnoreCase('.blockmap') ||
                    !name.containsIgnoreCase(platform == 'windows' ? '_win' :
                            (platform == 'linux' ? '_linux' :
                                    (platform == 'mac' ? '_mac' : '.apk')))) {
                continue
            }
            url[name] = asset.browser_download_url
        }
    }
    return ['version': version, 'url': url]
}

