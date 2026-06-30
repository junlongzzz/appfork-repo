import cn.hutool.http.HttpUtil
import com.alibaba.fastjson2.JSONObject
import groovy.json.JsonSlurper
import plus.junlong.appfork.script.ScriptUpdater
import plus.junlong.appfork.script.ScriptVars

class UpdateScript implements ScriptUpdater {

    // https://cdn-go.cn/qq-web/im.qq.com_new/latest/rainbow/pcConfig.json
    // https://cdn-go.cn/qq-web/im.qq.com_new/latest/rainbow/mobileConfig.json

    @Override
    Object checkUpdate(JSONObject manifest, JSONObject args) {
        def platform = manifest.platform as String

        def timeout = 60000
        def headers = [
                'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6',
                'User-Agent'     : ScriptVars.USER_AGENT
        ]

        def checkUrl = 'https://cdn-go.cn/qq-web/im.qq.com_new/latest/rainbow/pcConfig.json'

        def response = HttpUtil.createGet(checkUrl, true).setProxy(Proxy.NO_PROXY)
                .timeout(timeout).headerMap(headers, true).execute().body()
        if (response == null || response.isEmpty()) {
            return null
        }
        def object = new JsonSlurper().parseText(response)

        def version = null
        def url = null
        if (platform == 'linux') {
            version = object.Linux?.version
            url = object.Linux?.x64DownloadUrl
        } else if (platform == 'windows') {
            version = object.Windows?.version
            url = [
                    'x64'  : object.Windows?.ntDownloadX64Url,
                    'arm64': object.Windows?.ntDownloadARMUrl
            ]
        }

        return [
                version: version,
                url    : url
        ]
    }

}