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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * 平台级更新必须写回 YAML 清单结构，否则落盘内容不变，Actions 检测不到变更
     */
    @Test
    @SuppressWarnings("unchecked")
    public void platformUpdatePersistsToYamlManifest() {
        File file = new File("plate/manifests/f/fl-clash.yaml");
        JSONObject manifestJson = new Yaml().loadAs(FileUtil.readUtf8String(file), JSONObject.class);

        Updater updater = new Updater();
        Map<String, JSONObject> platforms = updater.parsePlatforms(manifestJson.get("platforms"));
        assertNotNull(platforms, "platforms 解析失败");

        JSONObject block = platforms.get("windows");
        String newVersion = block.getString("version") + ".1";
        assertTrue(updater.applyUpdate(file, manifestJson, block, "windows", Map.of("version", newVersion)),
                "版本变更应返回 true");

        // 修改必须体现在清单原始结构中，dump 后才会真正写入文件
        Map<String, Object> rawPlatforms = (Map<String, Object>) manifestJson.get("platforms");
        assertEquals(newVersion, ((Map<String, Object>) rawPlatforms.get("windows")).get("version"),
                "平台版本未写回清单结构");
    }

    /**
     * 验证指定清单文件：逐平台执行脚本检查更新 + HEAD 请求验证下载链接可用性
     * <p>
     * 使用方法：修改 manifestPaths 数组为需要验证的清单文件路径，然后运行此测试
     */
    @Test
    public void verifyManifests() throws Exception {
        // ====== 在这里指定需要验证的清单文件路径 ======
        String[] manifestPaths = {
                "plate/manifests/g/git-for-windows.yaml",
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
        String code = FileUtil.mainName(file).toLowerCase();
        log.info("清单: name={}, code={}", name, code);

        // 3. 验证必填字段
        assertFalse(StrUtil.isBlank(name), "缺少必填字段: name");
        assertFalse(StrUtil.isBlank(manifestJson.getString("homepage")), "缺少必填字段: homepage");
        assertFalse(StrUtil.isBlank(manifestJson.getString("author")), "缺少必填字段: author");
        assertFalse(StrUtil.isBlank(manifestJson.getString("description")), "缺少必填字段: description");

        // 3.1 校验 categories
        assertInstanceOf(List.class, manifestJson.get("categories"), "categories 必须是数组");
        List<?> categories = (List<?>) manifestJson.get("categories");
        assertFalse(categories.isEmpty(), "categories 不能为空");
        for (Object category : categories) {
            assertInstanceOf(String.class, category, "categories 元素必须是字符串");
            assertTrue(Updater.CATEGORIES.containsKey(((String) category).toLowerCase()),
                    "分类非法: " + category);
        }

        // 3.2 校验 platforms
        assertInstanceOf(Map.class, manifestJson.get("platforms"), "platforms 必须是映射");
        Map<?, ?> platformsJson = (Map<?, ?>) manifestJson.get("platforms");
        assertFalse(platformsJson.isEmpty(), "platforms 不能为空");
        Map<String, JSONObject> platforms = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : platformsJson.entrySet()) {
            assertInstanceOf(String.class, entry.getKey(), "platforms 的键必须是字符串");
            String platform = (String) entry.getKey();
            assertTrue(Updater.PLATFORMS.containsKey(platform), "未知平台: " + platform);
            assertInstanceOf(Map.class, entry.getValue(), "平台配置必须是映射: " + platform);
            JSONObject block = new JSONObject((Map<String, Object>) entry.getValue());
            assertFalse(StrUtil.isBlank(block.getString("version")), "缺少平台版本号: " + platform);
            assertNotNull(block.get("url"), "缺少平台下载地址: " + platform);
            platforms.put(platform, block);
        }
        log.info("平台: {}", platforms.keySet());

        // 3.3 校验 logo（如果存在）
        String logo = manifestJson.getString("logo");
        if (StrUtil.isNotBlank(logo)) {
            if (logo.startsWith("//")) {
                logo = "https:" + logo;
            }
            assertTrue(ReUtil.isMatch(URL_PATTERN, logo), "logo URL 格式不合法: " + logo);
        }

        // 4. 逐平台执行更新脚本
        ScriptConfig defaultScript = parseScript(manifestJson, code);
        List<String> urlsToVerify = new ArrayList<>();
        if (StrUtil.isNotBlank(logo)) {
            urlsToVerify.add(logo);
        }
        for (Map.Entry<String, JSONObject> entry : platforms.entrySet()) {
            String platform = entry.getKey();
            JSONObject block = entry.getValue();
            ScriptConfig scriptConfig = block.containsKey("script") ? parseScript(block, code) : defaultScript;
            log.info("========== 平台 [{}] version={} ==========", platform, block.getString("version"));

            // 构造单平台视图，与 Updater 执行脚本时保持一致
            JSONObject view = manifestJson.clone();
            view.remove("script");
            view.remove("categories");
            view.remove("platforms");
            view.put("platform", platform);
            view.put("version", block.get("version"));
            view.put("url", block.get("url"));

            Map<String, Object> result = executeScript(scriptConfig, view);
            assertFalse(result.containsKey("error"), "脚本返回错误: " + result.get("error"));

            Object resultVersion = result.get("version");
            assertInstanceOf(String.class, resultVersion, "脚本返回的 version 不是 String 类型");
            assertFalse(((String) resultVersion).isBlank(), "脚本返回的 version 为空字符串");
            log.info("检查更新结果: version={}", resultVersion);

            // 收集脚本返回的下载链接
            collectUrls(result.get("url"), platform + " 返回的 url", urlsToVerify);
        }

        // 5. HEAD 请求验证下载链接可用性
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

    /**
     * 解析脚本配置，缺省时脚本名为清单文件名
     */
    private ScriptConfig parseScript(JSONObject json, String defaultName) {
        Object scriptValue = json.get("script");
        String scriptName = defaultName;
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
        } else if (scriptValue != null) {
            fail("script 配置类型不合法: " + scriptValue.getClass().getName());
        }
        return new ScriptConfig(scriptName, scriptArgs);
    }

    /**
     * 加载并执行 Groovy 更新脚本
     */
    private Map<String, Object> executeScript(ScriptConfig scriptConfig, JSONObject manifestView) throws Exception {
        File scriptFile = new File(SCRIPTS_DIR, scriptConfig.name().toLowerCase() + ".groovy");
        assertTrue(scriptFile.exists(), "脚本文件不存在: " + scriptFile.getPath());
        log.info("执行脚本: {}, args={}", scriptFile.getName(), scriptConfig.args());

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
            result = updater.checkUpdate(manifestView, scriptConfig.args());
        }

        assertNotNull(result, "脚本返回 null，未检测到更新信息");
        assertInstanceOf(Map.class, result, "脚本返回类型不是 Map");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        return resultMap;
    }

    /**
     * 收集并校验 URL（字符串、列表或键值映射）
     */
    private void collectUrls(Object urlValue, String source, List<String> urlsToVerify) {
        assertNotNull(urlValue, source + " 为 null");
        switch (urlValue) {
            case String s -> {
                assertTrue(ReUtil.isMatch(URL_PATTERN, s), source + " 格式不合法: " + s);
                urlsToVerify.add(s);
            }
            case Map<?, ?> m -> {
                assertFalse(m.isEmpty(), source + " Map 为空");
                for (Map.Entry<?, ?> entry : m.entrySet()) {
                    assertInstanceOf(String.class, entry.getValue(),
                            source + " 中 key=[" + entry.getKey() + "] 的值不是 String 类型");
                    String url = (String) entry.getValue();
                    assertTrue(ReUtil.isMatch(URL_PATTERN, url),
                            source + " 格式不合法: " + entry.getKey() + " -> " + url);
                    urlsToVerify.add(url);
                }
                log.info("{} Map 包含 {} 个下载链接", source, m.size());
            }
            case List<?> l -> {
                assertFalse(l.isEmpty(), source + " List 为空");
                for (Object item : l) {
                    assertInstanceOf(String.class, item, source + " 中的元素不是 String 类型");
                    String url = (String) item;
                    assertTrue(ReUtil.isMatch(URL_PATTERN, url), source + " 格式不合法: " + url);
                    urlsToVerify.add(url);
                }
                log.info("{} List 包含 {} 个下载链接", source, l.size());
            }
            default -> fail(source + " 类型不合法，应为 String/Map/List，实际为: " + urlValue.getClass().getName());
        }
    }

    private record ScriptConfig(String name, JSONObject args) {
    }

    @Test
    public void convertJsonOrYaml() {
        Path path = Path.of("plate/manifests/b/bing-wallpaper.yaml");
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
