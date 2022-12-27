static def checkUpdate(version, platform, args) {
    // 在 2.0.0.1740 版本的时候，返回的json内的 details 的值是数组但是多了一个逗号，导致无法进行json解析
//    def matcher = response =~ 'jsonpCallback\\(([\\s\\S]*)\\)'
//    if (!matcher.find()) {
//        return null
//    }
//    def jsonData = new JsonSlurper().parseText("{\"data\":${matcher[0][1]}}")
//    return jsonData.data[0].version

    def response = "https://weishi.360.cn/qudongdashi/updateData.json?callback=jsonpCallback&_=${System.currentTimeMillis()}".toURL().text
    // 正则匹配任意字符
    def matcher = response =~ '"version": "([\\d.]+)"'
    if (!matcher.find()) {
        return null
    }
    return [
            'version': matcher[0][1] as String,
            'url'    : [
                    '轻巧版': 'https://dl.360safe.com/drvmgr/guanwang__360DrvMgrInstaller_beta.exe',
                    '网卡版': 'https://dl.360safe.com/drvmgr/gwwk__360DrvMgrInstaller_net.exe'
            ]
    ]
}