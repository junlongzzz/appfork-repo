import com.alibaba.fastjson2.JSONObject
import groovy.json.JsonSlurper
import plus.junlong.appfork.script.ScriptUpdater

class UpdateScript implements ScriptUpdater {

    @Override
    Object checkUpdate(JSONObject manifest, JSONObject args) {
        // codes是JB产品的唯一编码，多个使用英文逗号分隔
        def codes = args ? args.codes as String : null
        if (codes == null || codes == '') {
            return null
        }
        def response = "https://data.services.jetbrains.com/products/releases?code=${codes}&latest=true&type=release".toURL().text
        def responseJson = new JsonSlurper().parseText(response)

        def codeArr = codes.split(',')
        def version = null
        def url = []
        for (final String code in codeArr) {
            def productInfo = responseJson[code][0]
            def productVersion = productInfo['version'] as String
            def productBuild = productInfo['build'] as String
            def downloads = productInfo['downloads'] as Map

            def codeVersion = productBuild.startsWith(productVersion) ? productBuild :
                    "${productVersion}(${productBuild})" as String
            if (version == null) {
                version = codeVersion
            } else if (!version.contains(codeVersion)) {
                // 多个版本号最后以逗号分隔
                version = version + ',' + codeVersion
            }

            for (final Map.Entry<String, Object> entry in downloads.entrySet()) {
                // 以 windows,linux,mac 开头的就是对应平台的下载地址
                if (entry.getKey().startsWith(manifest.platform as String)) {
                    def link = entry.getValue()['link'] as String
                    url << link
                }
            }
        }

        return [
                'version': version,
                'url'    : url
        ]
    }

}