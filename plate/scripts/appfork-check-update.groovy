import com.jayway.jsonpath.JsonPath
import org.dom4j.DocumentHelper

static def checkUpdate(version, platform, args) {
    if (args == null) {
        return null
    }

    def url = args.url as String
    def regex = args.regex as String
    def jsonpath = args.jsonpath as String
    def xpath = args.xpath as String
    def updateUrl = args[platform as String]

    if (!url || !updateUrl) {
        return null
    }

    def response = url.toURL().text
    // 开始用对应方式查找版本号
    if (regex) { // 正则
        def matcher = response =~ regex
        if (!matcher.find()) {
            return null
        }
        version = matcher[0][1] as String
    } else if (jsonpath) { // json
        def read = JsonPath.read(response, jsonpath)
        if (read instanceof List) {
            version = read[0] as String
        } else {
            version = read as String
        }
    } else if (xpath) { // xml
        def document = DocumentHelper.parseText(response)
        def node = document.selectSingleNode(xpath)
        if (node) {
            return null
        }
        version = node.getText()
    } else {
        return null
    }

    def versionReplaceRegex = '\\$\\{?version}?'
    def retUrl = null
    if (updateUrl instanceof String) {
        retUrl = updateUrl.replaceAll(versionReplaceRegex, version)
    } else if (updateUrl instanceof Map) {
        retUrl = [:]
        updateUrl.forEach((String k, String v) -> {
            retUrl[k.replaceAll(versionReplaceRegex, version)] = v.replaceAll(versionReplaceRegex, version)
        })
    }

    return [
            version: version,
            url    : retUrl
    ]
}
