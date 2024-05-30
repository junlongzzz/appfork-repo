package plus.junlong.appfork;

import cn.hutool.core.date.BetweenFormatter;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Junlong
 */
@Component
@Slf4j
public final class Updater {

    private static final Map<String, String> platforms = new LinkedHashMap<>();
    private static final Map<String, String> categories = new LinkedHashMap<>();

    static {
        // 平台列表
        platforms.put("windows", "Windows");
        platforms.put("linux", "Linux");
        platforms.put("mac", "macOS");
        platforms.put("android", "Android");
        platforms.put("extensions", "浏览器扩展");
        platforms.put("other", "Other");
        // 分类列表
        categories.put("network", "网络应用");
        categories.put("chat", "社交沟通");
        categories.put("music", "音乐欣赏");
        categories.put("video", "视频播放");
        categories.put("graphics", "图形图像");
        categories.put("games", "游戏娱乐");
        categories.put("office", "办公学习");
        categories.put("reading", "阅读翻译");
        categories.put("development", "编程开发");
        categories.put("tools", "系统工具");
        categories.put("beautify", "主题美化");
        categories.put("others", "其他应用");
        categories.put("image", "系统镜像");
    }

    private static final GroovyShell GROOVY_SHELL = new GroovyShell();
    private static final Map<String, Script> SCRIPT_CACHE = new LinkedHashMap<>();

    // 正常完成同步
    private static final int SYNC_NONE = 0;
    // 正常完成同步并更新了清单文件信息
    private static final int SYNC_UPDATE = 1;
    // 未完成同步，失败
    private static final int SYNC_ERROR = 2;

    @Value("${config.repo-path}")
    private String repoPath;

    /**
     * 运行Updater
     */
    public void run(String... args) {
        log.info("同步软件库开始[{}]...", repoPath);

        // 清单文件目录 test环境下使用manifests-test目录
        Path manifestsDir = Path.of(repoPath, "test".equals(SpringUtil.getActiveProfile()) ? "manifests-test" : "manifests");
        if (!Files.exists(manifestsDir) || !Files.isDirectory(manifestsDir)) {
            log.error("该目录不存在: {}", manifestsDir);
            return;
        }
        // 获取清单文件列表，目录下所有层级文件夹内的文件
        List<File> manifests = FileUtil.loopFiles(manifestsDir, pathname ->
                pathname.getName().toLowerCase().endsWith(".json") &&
                        pathname.isFile() &&
                        pathname.canRead() &&
                        pathname.canWrite());
        if (manifests.isEmpty()) {
            log.error("目录内无清单文件: {}", repoPath);
            return;
        }
        log.info("共扫描到清单文件 {} 个", manifests.size());

        long startTime = System.currentTimeMillis();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<int[]> future = executor.submit(() -> {
                int[] count = {0, 0, 0};
                for (File manifestFile : manifests) {
                    try {
                        count[sync(manifestFile)]++;
                    } catch (Exception e) {
                        count[SYNC_ERROR]++;
                        log.error("sync [{}] error[{}]: {}", manifestFile.getName(), e.getClass().getSimpleName(), e.getMessage());
                    }
                }
                return count;
            });
            // 等待结果超时
            int[] count = future.get(4, TimeUnit.HOURS);
            log.info("同步完成, 本次更新 {} 个, 失败 {} 个", count[SYNC_UPDATE], count[SYNC_ERROR]);
        } catch (Exception e) {
            log.error("同步出错[{}]: {}", e.getClass().getSimpleName(), e.getMessage());
            // 同步超时强行中断程序退出
            System.exit(1);
        } finally {
            log.info("同步耗时: {}", DateUtil.formatBetween(System.currentTimeMillis() - startTime, BetweenFormatter.Level.MILLISECOND));
            // 处理脚本缓存
            SCRIPT_CACHE.clear();
            GROOVY_SHELL.resetLoadedClasses();
        }
    }

    /**
     * 同步指定软件清单文件
     *
     * @return 同步状态
     */
    private int sync(File manifest) throws Exception {
        JSONObject manifestJson = JSON.parseObject(FileUtil.readUtf8String(manifest));
        if (manifestJson == null || manifestJson.isEmpty()) {
            log.error("manifest [{}] parsed is null or empty", manifest.getName());
            return SYNC_ERROR;
        }

        String code = FileUtil.mainName(manifest).toLowerCase();
        String name = manifestJson.getString("name");
        String homepage = manifestJson.getString("homepage");
        // String logo = manifestJson.getString("logo");
        String author = manifestJson.getString("author");
        String description = manifestJson.getString("description");
        String category = manifestJson.getString("category");
        String platform = manifestJson.getString("platform");
        String version = manifestJson.getString("version");
        // Object url = manifestJson.get("url");
        Object scriptValue = manifestJson.get("script");

        if (StrUtil.isBlank(name) ||
                StrUtil.isBlank(homepage) ||
                StrUtil.isBlank(category) || !categories.containsKey(category.toLowerCase()) ||
                StrUtil.isBlank(platform) || !platforms.containsKey(platform.toLowerCase())) {
            log.error("manifest [{}] has illegal attribute", manifest.getName());
            return SYNC_ERROR;
        }

        // 是否更新清单文件
        boolean updateManifest = false;

        if (StrUtil.isBlank(author)) {
            manifestJson.put("author", "[Unknown]");
            updateManifest = true;
        }
        if (StrUtil.isBlank(description)) {
            manifestJson.put("description", "[No Description]");
            updateManifest = true;
        }

        // 清单文件对应的script脚本文件名 默认为清单文件名一致
        String scriptName = code;
        // 脚本文件执行时的额外参数
        Map<?, ?> scriptArgs = null;
        if (scriptValue instanceof String) {
            // 如果脚本值是字符串，那么直接作为脚本文件名
            scriptName = (String) scriptValue;
        } else if (scriptValue instanceof Map<?, ?> scriptValueMap) {
            // 如果脚本值是Map，那么获取脚本文件名和脚本参数
            Object nameObj = scriptValueMap.get("name");
            if (nameObj instanceof String) {
                scriptName = (String) nameObj;
            }
            Object argsObj = scriptValueMap.get("args");
            if (argsObj instanceof Map) {
                scriptArgs = (Map<?, ?>) argsObj;
            }
        }

        // 检查更新脚本
        File script = new File(repoPath, "scripts" + File.separator + scriptName.toLowerCase() + ".groovy");
        if (script.exists() && script.isFile()) {
            // groovy脚本运行
            String scriptFilename = script.getName();
            Script updateScript = SCRIPT_CACHE.get(scriptFilename);
            if (updateScript == null) {
                updateScript = GROOVY_SHELL.parse(script);
                SCRIPT_CACHE.put(scriptFilename, updateScript);
            }
            // 执行检测App更新的脚本指定方法
            // clone清单文件，避免脚本修改原清单文件内容
            JSONObject manifestClone = manifestJson.clone();
            // 清除脚本属性
            manifestClone.remove("script");
            Object checkUpdateObj = updateScript.invokeMethod("checkUpdate", new Object[]{manifestClone, scriptArgs});
            if (checkUpdateObj instanceof Map<?, ?> checkUpdate) {
                // 获取脚本返回的错误信息
                Object error = checkUpdate.get("error");
                if (error != null) {
                    log.error("exec [{}] script [{}] return error: {}", manifest.getName(), scriptFilename, error);
                    return SYNC_ERROR;
                }

                // 通过循环获取脚本返回的属性值是否跟已有清单文件内属性值一致
                for (Map.Entry<?, ?> entry : checkUpdate.entrySet()) {
                    String key = (String) entry.getKey();
                    Object value = entry.getValue();

                    Object manifestValue = manifestJson.get(key);
                    if (manifestValue == null) {
                        // 清单文件内不存在该属性，直接添加
                        manifestJson.put(key, value);
                        updateManifest = true;
                        continue;
                    }
                    if (value != null && !JSON.toJSONString(value).equals(JSON.toJSONString(manifestValue))) {
                        if ("url".equalsIgnoreCase(key)) {
                            // 开始检查链接是否合法
                            boolean isUrl = false;
                            if (value instanceof String valueStr) {
                                // 只有一个链接，直接检查是否是合法链接形式
                                if (isUrl(valueStr)) {
                                    isUrl = true;
                                }
                            } else if (value instanceof Map<?, ?> updateUrlMap) {
                                // 有多个链接，循环检查是否是合法链接形式
                                if (!updateUrlMap.isEmpty()) {
                                    isUrl = true;
                                    for (Object v : updateUrlMap.values()) {
                                        if (!(v instanceof String) || !isUrl((String) v)) {
                                            isUrl = false;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (!isUrl) {
                                // 如果链接不合法，那么不更新清单文件内的属性值
                                continue;
                            }
                            // url链接就直接存放对应数据类型，不做String化处理
                            manifestJson.put(key, value);
                        } else {
                            // 如果脚本返回的属性值跟清单文件内的属性值不一致，那么更新清单文件内的属性值
                            // 其他属性都是String类型，需要进行String化处理
                            manifestJson.put(key, String.valueOf(value));
                        }
                        updateManifest = true;
                    }
                }

            }
        }

        if (updateManifest) {
            // 将新版清单内容写入文件
            FileUtil.writeUtf8String(JSON.toJSONString(manifestJson, JSONWriter.Feature.PrettyFormat), manifest);
            log.info("manifest [{}] updated: {}->{}", manifest.getName(), version, manifestJson.getString("version"));
            return SYNC_UPDATE;
        }

        return SYNC_NONE;
    }

    /**
     * 判断是否是合法url地址，不匹配 localhost
     */
    public boolean isUrl(String text) {
        return ReUtil.isMatch("^((https?|ftp):)?//[\\w.-]+(?:\\.[\\w.-]+)+[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+", text);
    }

}
