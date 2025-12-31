import com.alibaba.fastjson2.JSONObject
import groovy.xml.XmlSlurper
import plus.junlong.appfork.script.ScriptUpdater

class UpdateScript implements ScriptUpdater {

    @Override
    Map<String, Object> checkUpdate(JSONObject manifest, JSONObject args) {
        def response = 'https://scoopinstaller.github.io/UpdateTracker/googlechrome/chrome.min.xml'.toURL().text
        def chromechecker = new XmlSlurper().parseText(response)
        def version = chromechecker.stable64.version.text()
        def url = null
        chromechecker.stable64.download.url.each {
            url = it.text()
            if (url.contains('dl.google.com')) {
                return [
                        'version': version,
                        'url'    : url
                ]
            }
        }

        return [
                'version': version,
                'url'    : url
        ] as Map<String, Object>
    }

}