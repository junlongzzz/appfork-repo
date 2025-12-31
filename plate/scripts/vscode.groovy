import com.alibaba.fastjson2.JSONObject
import com.jayway.jsonpath.JsonPath
import plus.junlong.appfork.script.ScriptUpdater

import java.util.regex.Pattern

class UpdateScript implements ScriptUpdater {

    @Override
    Object checkUpdate(JSONObject manifest, JSONObject args) {
        def jsonpath = switch (manifest.platform) {
            case 'windows' -> '$.products[?(@.platform.os =~ /win32(.*)/i)]'
            case 'linux' -> '$.products[?(@.platform.os =~ /linux(.*)/i)]'
            case 'mac' -> '$.products[?(@.platform.os =~ /darwin(.*)/i)]'
            default -> null
        }

        if (jsonpath == null) {
            return null
        }

        def response = 'https://code.visualstudio.com/sha?build=stable'.toURL().text
        def read = JsonPath.read(response, jsonpath)
        def version = null
        def url = [:]
        if (read instanceof List) {
            def versionPattern = Pattern.compile('^[\\d.]+$')
            read.forEach(product -> {
                def productVersion = product.productVersion
                // 正则判断需要符合 x.y.z 格式
                if (productVersion.matches(versionPattern)) {
                    if (version == null || version < productVersion) {
                        version = productVersion
                    }
                    url[product.platform.prettyname as String] = "https://update.code.visualstudio.com/${version}/${product.platform.os}/stable" as String
                }
            })
        } else {
            return null
        }

        return [
                'version': version,
                'url'    : url
        ]
    }

}