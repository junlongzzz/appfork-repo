import plus.junlong.appfork.ScriptVars

import cn.hutool.crypto.Mode
import cn.hutool.crypto.Padding
import cn.hutool.crypto.symmetric.AES
import cn.hutool.http.HttpUtil
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

static def checkUpdate(manifest, args) {
    println('=====================================')
    println('>> This is test script')
    println(">> manifest: \n${JsonOutput.prettyPrint(JsonOutput.toJson(manifest as Map))}")
    println(">> script args: \n${JsonOutput.prettyPrint(JsonOutput.toJson(args as Map))}")
    println('=====================================')

    checkUpdateTest(manifest, args)

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

static def checkUpdateTest(manifest, args) {
    // test script here
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
            .header('user-agent', ScriptVars.USER_AGENT)
            .header('origin', baseUrl)
            .header('referer', "${baseUrl}/detail/${softId}")
            .body(JsonOutput.toJson(
                    [
                            data: aes.encryptBase64(JsonOutput.toJson(body as Map))
                    ]
            )).execute()
    return new JsonSlurper().parseText(resp.body())
}