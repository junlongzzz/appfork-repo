import cn.hutool.crypto.Mode
import cn.hutool.crypto.Padding
import cn.hutool.crypto.symmetric.AES
import cn.hutool.http.HttpUtil
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.jsoup.Jsoup

static def checkUpdate(version, platform, args) {
    println('[[this is test script]]')

    checkUpdateTest(version, platform, args)

//    println(tsc('tsc://0/26980'))

    // 获取应用详情
//    def respJson = lestore('/api/webstorecontents/app/details', [
//            softId: '13407'
//    ])
//    if (respJson.status != 0) {
//        println(respJson.message)
//    } else {
//        println(respJson.data)
//    }

    // 获取应用的下载链接地址
//    def respJson = lestore('/api/webstorecontents/download/getDownloadUrl', [
//            bizType: 1,
//            product: 3,
//            softId : '12489',
//            type   : 0
//    ])
//    if (respJson.status != 0) {
//        println(respJson.message)
//    } else {
//        println(respJson.data)
//    }

    return [
            version: 'beta',
            url    : 'https://junlong.plus/ztool/appfork'
    ]
}

static def checkUpdateTest(version, platform, args) {
    // test script here
}

static tsc(appUrl, regex = '>版本：([\\d.]+)<') {
    // 从腾讯软件中心获取版本号和下载链接
    def urlMatcher = appUrl =~ 'tsc://(\\d+)/(\\d+)'
    if (!urlMatcher.find()) {
        return null
    }

    def categoryId = urlMatcher.group(1)
    def appId = urlMatcher.group(2)
    def document = Jsoup.connect("https://pc.qq.com/detail/${categoryId}/detail_${appId}.html").timeout(30000)
            .headers(['User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36 Edg/117.0.2045.41'])
            .get()
    def matcher = document.html() =~ regex
    if (!matcher.find()) {
        return null
    }
    def version = matcher.group(1)
    def url = document.selectFirst("a[data-id='${appId}']").attr('href')

    return [
            version: version,
            url    : url
    ]
}

static def lestore(url, body) {
    // 联想软件商店的请求参数AES加密解密 https://lestore.lenovo.com/
    // AES加密密钥，必须与加密时使用的密钥相同
    String key = '65023EC4BA7420BB' // 16字节的密钥
    // cbc pkcs7padding(与pkcs5大体一致) iv向量加密解密
    AES aes = new AES(Mode.CBC, Padding.PKCS5Padding, key.getBytes(), key.getBytes())
    def softId = body.softId

    def baseUrl = 'https://lestore.lenovo.com'
    def resp = HttpUtil.createPost("${baseUrl}${url}")
            .timeout(30000)
            .contentType('application/json')
            .header('user-agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36 Edg/117.0.2045.41')
            .header('origin', baseUrl)
            .header('referer', "${baseUrl}/detail/${softId}")
            .body(JsonOutput.toJson(
                    [
                            data: aes.encryptBase64(JsonOutput.toJson(body as Map))
                    ]
            )).execute()
    return new JsonSlurper().parseText(resp.body())
}