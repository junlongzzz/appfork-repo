package plus.junlong.appfork;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import groovy.lang.GroovyClassLoader;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import plus.junlong.appfork.script.ScriptUpdater;
import plus.junlong.appfork.script.ScriptVars;

import java.io.File;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class AppForkTests {

    /**
     * 脚本目录路径
     */
    private static final String SCRIPTS_DIR = "plate/scripts";

    /**
     * URL 合法性正则
     */
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^((https?|ftp):)?//[\\w.-]+(?:\\.[\\w.-]+)+[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");

    @Test
    public void test() {
    }

    /**
     * 验证指定清单文件：执行脚本检查更新 + HEAD 请求验证下载链接可用性
     * <p>
     * 使用方法：修改 manifestPaths 数组为需要验证的清单文件路径，然后运行此测试
     */
    @Test
    public void verifyManifests() throws Exception {
        // ====== 在这里指定需要验证的清单文件路径 ======
        String[] manifestPaths = {
                "plate/manifests/e/eclipse-temurin-jdk-8.json",
                "plate/manifests/e/eclipse-temurin-jdk-8-linux.json",
                "plate/manifests/e/eclipse-temurin-jdk-11.json",
                "plate/manifests/e/eclipse-temurin-jdk-11-linux.json",
                "plate/manifests/e/eclipse-temurin-jdk-17.json",
                "plate/manifests/e/eclipse-temurin-jdk-17-linux.json",
                "plate/manifests/e/eclipse-temurin-jdk-21.json",
                "plate/manifests/e/eclipse-temurin-jdk-21-linux.json",
                "plate/manifests/e/eclipse-temurin-jdk-25.json",
                "plate/manifests/e/eclipse-temurin-jdk-25-linux.json",
                "plate/manifests/o/oracle-jdk-21.json",
                "plate/manifests/o/oracle-jdk-21-linux.json",
                "plate/manifests/o/oracle-jdk-25.json",
                "plate/manifests/o/oracle-jdk-25-linux.json",
                "plate/manifests/g/graalvm-jdk.json",
                "plate/manifests/g/graalvm-jdk-linux.json",
        };
        // =============================================

        int successCount = 0;
        int failCount = 0;

        for (String manifestPath : manifestPaths) {
            log.info("========== 验证清单文件: {} ==========", manifestPath);
            try {
                verifyManifest(Path.of(manifestPath));
                successCount++;
                log.info("[PASS] {}", manifestPath);
            } catch (Exception e) {
                failCount++;
                log.error("[FAIL] {}: {}", manifestPath, e.getMessage(), e);
            }
            log.info("");
        }

        log.info("========== 验证结果: {} 通过, {} 失败 ==========", successCount, failCount);
        assertEquals(0, failCount, failCount + " 个清单文件验证失败");
    }

    /**
     * 验证单个清单文件
     */
    private void verifyManifest(Path manifestPath) throws Exception {
        // 1. 检查文件存在
        assertTrue(Files.exists(manifestPath) && Files.isRegularFile(manifestPath),
                "清单文件不存在: " + manifestPath);

        File file = manifestPath.toFile();
        String format = FileUtil.extName(file).toLowerCase();
        String content = FileUtil.readUtf8String(file);
        assertFalse(StrUtil.isBlank(content), "清单文件内容为空");

        // 2. 解析清单文件
        JSONObject manifestJson = switch (format) {
            case "json" -> JSON.parseObject(content);
            case "yaml" -> new Yaml().loadAs(content, JSONObject.class);
            default -> throw new IllegalArgumentException("不支持的文件格式: " + format);
        };
        assertNotNull(manifestJson, "清单文件解析结果为空");
        assertFalse(manifestJson.isEmpty(), "清单文件解析结果为空对象");

        String name = manifestJson.getString("name");
        String version = manifestJson.getString("version");
        log.info("清单: name={}, version={}, platform={}", name, version, manifestJson.getString("platform"));

        // 3. 验证必填字段
        assertFalse(StrUtil.isBlank(manifestJson.getString("name")), "缺少必填字段: name");
        assertFalse(StrUtil.isBlank(manifestJson.getString("homepage")), "缺少必填字段: homepage");
        assertFalse(StrUtil.isBlank(manifestJson.getString("author")), "缺少必填字段: author");
        assertFalse(StrUtil.isBlank(manifestJson.getString("description")), "缺少必填字段: description");
        assertFalse(StrUtil.isBlank(manifestJson.getString("category")), "缺少必填字段: category");
        assertFalse(StrUtil.isBlank(manifestJson.getString("platform")), "缺少必填字段: platform");

        // 3.1 校验 logo（如果存在）
        String logo = manifestJson.getString("logo");
        if (StrUtil.isNotBlank(logo)) {
            if (logo.startsWith("//")) {
                logo = "https:" + logo;
            }
            assertTrue(ReUtil.isMatch(URL_PATTERN, logo), "logo URL 格式不合法: " + logo);
        }

        // 4. 解析 script 配置
        String code = FileUtil.mainName(file).toLowerCase();
        Object scriptValue = manifestJson.get("script");
        String scriptName = code;
        JSONObject scriptArgs = null;

        if (scriptValue instanceof String s) {
            scriptName = s;
        } else if (scriptValue instanceof Map<?, ?> m) {
            if (m.get("name") instanceof String s) {
                scriptName = s;
            }
            if (m.get("args") instanceof Map<?, ?> argsMap) {
                scriptArgs = new JSONObject(argsMap);
            }
        }

        // 5. 加载并执行 Groovy 脚本
        File scriptFile = new File(SCRIPTS_DIR, scriptName.toLowerCase() + ".groovy");
        assertTrue(scriptFile.exists(), "脚本文件不存在: " + scriptFile.getPath());

        log.info("执行脚本: {}, args={}", scriptFile.getName(), scriptArgs);

        Object result;
        try (GroovyClassLoader groovyClassLoader = new GroovyClassLoader()) {
            String original = FileUtil.readUtf8String(scriptFile);
            if (!original.trim().startsWith("package ")) {
                original = "package scripts.auto.p" + DigestUtil.md5Hex(scriptFile.getAbsolutePath()) + ";" + original;
            }
            Class<?> clazz = groovyClassLoader.parseClass(original, scriptFile.getName());
            assertTrue(ScriptUpdater.class.isAssignableFrom(clazz),
                    "脚本类未实现 ScriptUpdater 接口");

            ScriptUpdater updater = (ScriptUpdater) clazz.getDeclaredConstructor().newInstance();
            JSONObject manifestClone = manifestJson.clone();
            manifestClone.remove("script");
            result = updater.checkUpdate(manifestClone, scriptArgs);
        }

        // 6. 验证脚本返回结果
        assertNotNull(result, "脚本返回 null，未检测到更新信息");
        assertInstanceOf(Map.class, result, "脚本返回类型不是 Map");

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;

        // 检查是否返回了错误
        assertFalse(resultMap.containsKey("error"),
                "脚本返回错误: " + resultMap.get("error"));

        // 验证 version
        Object resultVersion = resultMap.get("version");
        assertNotNull(resultVersion, "脚本返回的 version 为 null");
        assertInstanceOf(String.class, resultVersion, "脚本返回的 version 不是 String 类型");
        assertFalse(((String) resultVersion).isBlank(), "脚本返回的 version 为空字符串");
        log.info("检查更新结果: version={}", resultVersion);

        // 验证 url
        Object resultUrl = resultMap.get("url");
        assertNotNull(resultUrl, "脚本返回的 url 为 null");

        // 收集所有需要验证的 URL
        List<String> urlsToVerify = new ArrayList<>();
        if (StrUtil.isNotBlank(logo)) {
            urlsToVerify.add(logo);
        }
        switch (resultUrl) {
            case String s -> {
                assertTrue(ReUtil.isMatch(URL_PATTERN, s), "URL 格式不合法: " + s);
                urlsToVerify.add(s);
            }
            case Map<?, ?> m -> {
                assertFalse(m.isEmpty(), "URL Map 为空");
                for (Map.Entry<?, ?> entry : m.entrySet()) {
                    assertInstanceOf(String.class, entry.getValue(),
                            "URL Map 中 key=[" + entry.getKey() + "] 的值不是 String 类型");
                    String url = (String) entry.getValue();
                    assertTrue(ReUtil.isMatch(URL_PATTERN, url),
                            "URL 格式不合法: " + entry.getKey() + " -> " + url);
                    urlsToVerify.add(url);
                }
                log.info("URL Map 包含 {} 个下载链接", m.size());
            }
            case List<?> l -> {
                assertFalse(l.isEmpty(), "URL List 为空");
                for (Object item : l) {
                    assertInstanceOf(String.class, item, "URL List 中的元素不是 String 类型");
                    String url = (String) item;
                    assertTrue(ReUtil.isMatch(URL_PATTERN, url), "URL 格式不合法: " + url);
                    urlsToVerify.add(url);
                }
                log.info("URL List 包含 {} 个下载链接", l.size());
            }
            default -> fail("URL 类型不合法，应为 String/Map/List，实际为: " + resultUrl.getClass().getName());
        }

        // 7. HEAD 请求验证下载链接可用性
        log.info("开始验证 {} 个下载链接的可用性...", urlsToVerify.size());
        var httpClient = ScriptVars.HTTP_CLIENT;
        List<String> failedUrls = new ArrayList<>();
        List<String> networkErrorUrls = new ArrayList<>();

        for (String url : urlsToVerify) {
            try {
                if (url.startsWith("//")) {
                    url = "https:" + url;
                }
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .header("User-Agent", ScriptVars.USER_AGENT)
                        .build();
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                int statusCode = response.statusCode();

                if (statusCode >= 200 && statusCode < 400) {
                    log.info("  [OK] {} -> {}", statusCode, url);
                } else if (statusCode == 405) {
                    // 某些服务器不支持 HEAD 方法，改用 GET + Range
                    HttpRequest getRequest = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .GET()
                            .header("User-Agent", ScriptVars.USER_AGENT)
                            .header("Range", "bytes=0-0")
                            .build();
                    HttpResponse<Void> getResponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.discarding());
                    int getStatusCode = getResponse.statusCode();
                    if (getStatusCode >= 200 && getStatusCode < 400) {
                        log.info("  [OK] {} (GET fallback) -> {}", getStatusCode, url);
                    } else {
                        log.error("  [FAIL] {} (GET fallback) -> {}", getStatusCode, url);
                        failedUrls.add(url + " (HTTP " + getStatusCode + ")");
                    }
                } else {
                    log.error("  [FAIL] {} -> {}", statusCode, url);
                    failedUrls.add(url + " (HTTP " + statusCode + ")");
                }
            } catch (java.net.http.HttpTimeoutException e) {
                // 连接超时视为网络环境问题，不算测试失败
                log.warn("  [WARN] 连接超时(网络环境问题，不计入失败) -> {}", url);
                networkErrorUrls.add(url + " (timeout)");
            } catch (java.io.IOException e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("Connection reset") || msg.contains("timed out"))) {
                    // 连接重置/超时视为网络环境问题
                    log.warn("  [WARN] 网络异常(不计入失败): {} -> {}", msg, url);
                    networkErrorUrls.add(url + " (" + msg + ")");
                } else {
                    log.error("  [FAIL] IO 异常 -> {}: {}", url, msg);
                    failedUrls.add(url + " (" + msg + ")");
                }
            } catch (Exception e) {
                log.error("  [FAIL] 请求异常 -> {}: {}", url, e.getMessage());
                failedUrls.add(url + " (" + e.getMessage() + ")");
            }
        }

        if (!networkErrorUrls.isEmpty()) {
            log.warn("有 {} 个链接因网络环境问题无法验证(不计入失败):\n{}", networkErrorUrls.size(),
                    String.join("\n", networkErrorUrls));
        }
        assertTrue(failedUrls.isEmpty(),
                "以下 " + failedUrls.size() + " 个下载链接不可用:\n" + String.join("\n", failedUrls));
        log.info("全部下载链接验证完成: {} 个成功, {} 个网络异常(跳过)", urlsToVerify.size() - networkErrorUrls.size(), networkErrorUrls.size());
    }

    @Test
    public void convertJsonOrYaml() {
        Path path = Path.of("plate/manifests/b/bing-wallpaper.json");
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            log.error("文件不存在: {}", path);
            return;
        }
        File file = path.toFile();
        String format = FileUtil.extName(file).toLowerCase();
        String read = FileUtil.readUtf8String(file);
        if (StrUtil.isBlank(read)) {
            log.error("文件内容为空");
            return;
        }
        log.info("文件内容：\n{}", read);
        String write;
        if ("json".equals(format)) {
            // convert to yaml
            format = "yaml";
            JSONObject manifestJson = JSON.parseObject(read);
            DumperOptions options = new DumperOptions();
            options.setIndent(2);
            options.setIndicatorIndent(2);
            options.setIndentWithIndicator(true);
            options.setSplitLines(false);
            write = new Yaml(options).dumpAsMap(manifestJson);
        } else if ("yaml".equals(format)) {
            // convert to json
            format = "json";
            JSONObject manifestJson = new Yaml().loadAs(read, JSONObject.class);
            write = JSON.toJSONString(manifestJson, JSONWriter.Feature.PrettyFormat);
        } else {
            log.error("不支持的文件类型");
            return;
        }
        String fromFilePath = file.getAbsolutePath();
        String toFilePath = fromFilePath.substring(0, fromFilePath.lastIndexOf(".") + 1) + format;
        log.info("转换后文件：{}", toFilePath);
        FileUtil.writeUtf8String(write, toFilePath);
        log.info("转换后：\n{}", write);
    }

}
