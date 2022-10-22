import com.jayway.jsonpath.JsonPath

static def checkUpdate(version, platform, args) {
    if (args == null) {
        return null
    }

    def url = args.url as String
    def regex = args.regex as String
    def jsonpath = args.jsonpath as String
    def updateUrl = args[platform as String]

    if (!url || !updateUrl) {
        return null
    }

    def response = url.toURL().text
    // 正则和jsonpath只能二选一来查找版本号
    if (regex) {
        def matcher = response =~ regex
        if (!matcher.find()) {
            return null
        }
        version = matcher[0][1] as String
    } else if (jsonpath) {
        def read = JsonPath.read(response, jsonpath)
        if (read instanceof List) {
            version = read[0] as String
        } else {
            version = read as String
        }
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
