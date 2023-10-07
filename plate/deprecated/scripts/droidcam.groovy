import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def repo = null
    if ('windows' == platform) {
        repo = 'windows-releases'
    } else if ('linux' == platform) {
        repo = 'droidcam'
    } else {
        return null
    }
    def response = "https://api.github.com/repos/dev47apps/${repo}/releases/latest".toURL().text
    def tagName = new JsonSlurper().parseText(response).tag_name as String
    version = tagName.replaceFirst('win-|v|V', '')

    def url = [:]
    if ('windows' == platform) {
        def name = "DroidCam.Setup.${version}.exe"
        url[name] = "https://files.dev47apps.net/win/${name}".toString()
    } else if ('linux' == platform) {
        def name = "droidcam_${version}.zip"
        url[name] = "https://files.dev47apps.net/linux/${name}".toString()
    } else {
        return null
    }
    url['Android App on Google Play'] = 'https://play.google.com/store/apps/developer?id=Dev47Apps'
    url['iOS App on App Store'] = 'https://apps.apple.com/us/app/droidcam-wireless-webcam/id1510258102'

    return [
            'version': version,
            'url'    : url
    ]
}
