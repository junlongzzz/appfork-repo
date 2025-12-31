import com.alibaba.fastjson2.JSONObject
import groovy.json.JsonSlurper
import plus.junlong.appfork.script.ScriptUpdater

class UpdateScript implements ScriptUpdater {

    @Override
    Object checkUpdate(JSONObject manifest, JSONObject args) {
        def platform = manifest.platform as String

        def repo = switch (platform) {
            case 'windows', 'linux', 'mac' -> 'listen1_desktop'
            case 'android' -> 'listen1_mobile'
            case 'extensions' -> 'listen1_chrome_extension'
            default -> null
        }
        if (!repo) {
            return null
        }
        def response = "https://api.github.com/repos/listen1/${repo}/releases/latest".toURL().text
        def result = new JsonSlurper().parseText(response)
        def tagName = result.tag_name as String
        def version = tagName.replaceFirst('[vV]', '')

        def url = [:]
        if (platform == 'extensions') {
            url['Install on Chrome'] = 'https://chrome.google.com/webstore/detail/listen-1/indecfegkejajpaipjipfkkbedgaodbp'
            url['Install on Microsoft Edge'] = 'https://microsoftedge.microsoft.com/addons/detail/bcneiehcbgahghfmgigmblcgkhihehad'
            url['Install on FireFox'] = 'https://addons.mozilla.org/zh-CN/firefox/addon/listen1/'
        } else {
            for (asset in result.assets) {
                def name = asset.name as String
                if (name.endsWithIgnoreCase('.yml') ||
                        name.endsWithIgnoreCase('.blockmap') ||
                        !name.containsIgnoreCase(platform == 'windows' ? '_win' : (platform == 'linux' ? '_linux' : (platform == 'mac' ? '_mac' : '.apk')))
                ) {
                    continue
                }
                url[name] = asset.browser_download_url
            }
        }
        return [version: version, url: url]
    }

}