import groovy.json.JsonSlurper
import plus.junlong.appfork.ScriptVars

import java.net.http.HttpRequest
import java.net.http.HttpResponse

static def checkUpdate(manifest, args) {
    def response = ScriptVars.HTTP_CLIENT.send(
            HttpRequest.newBuilder('https://lmstudio.ai/download'.toURI())
                    .header('User-Agent', ScriptVars.USER_AGENT)
                    .build(),
            HttpResponse.BodyHandlers.ofString()
    ).body()
    response = response.replace('\\"', '"')
    // 定位 "versionsData" 的起始位置
    def startKey = '"versionsData":'
    def startIndex = response.indexOf(startKey)
    if (startIndex == -1) {
        return null
    }

    // 从起始位置开始，提取剩余的字符串
    def remainingText = response.substring(startIndex + startKey.length())
    // 逐字符遍历，找到 JSON 的结束位置，模拟stack栈逻辑
    def stackDepth = 0
    def endIndex = 0
    for (int i = 0; i < remainingText.length(); i++) {
        char c = remainingText.charAt(i)
        if (c == (char) '{') {
            stackDepth++
        } else if (c == (char) '}') {
            stackDepth--
            if (stackDepth == 0) {
                endIndex = i
                break
            }
        }
    }
    if (endIndex == 0) {
        println("未找到完整的 JSON 结构")
        return null
    }
    // 提取完整的 JSON 字符串
    def jsonStr = remainingText.substring(0, endIndex + 1)
    try {
        // 使用 JsonSlurper 解析 JSON
        def jsonData = new JsonSlurper().parseText(jsonStr)
        def baseUrl = 'https://installers.lmstudio.ai'
        switch (manifest.platform) {
            case 'windows': {
                def version = "${jsonData.win32.x64.version}-${jsonData.win32.x64.build}" as String
                return [
                        'version': version,
                        'url'    : [
                                "${baseUrl}/win32/x64/${version}/LM-Studio-${version}-x64.exe" as String,
                                "${baseUrl}/win32/arm64/${version}/LM-Studio-${version}-arm64.exe" as String
                        ]
                ]
            }
            case 'mac': {
                def version = "${jsonData.darwin.arm64.version}-${jsonData.darwin.arm64.build}" as String
                return [
                        'version': version,
                        'url'    : [
                                "${baseUrl}/darwin/arm64/${version}/LM-Studio-${version}-arm64.dmg" as String
                        ]
                ]
            }
            case 'linux': {
                def version = "${jsonData.linux.x64.version}-${jsonData.linux.x64.build}" as String
                return [
                        'version': version,
                        'url'    : [
                                "${baseUrl}/linux/x64/${version}/LM-Studio-${version}-x64.AppImage" as String
                        ]
                ]
            }
            default:
                return null
        }
    } catch (Exception e) {
        println("提取的 JSON 无效: ${e.message}")
        return null
    }
}