package plus.junlong.appfork;

import cn.hutool.core.date.BetweenFormatter;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

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
    private static final Map<String, Script> SCRIPT_CACHE = new ConcurrentHashMap<>();

    // 正常完成同步但未更新
    private static final int SYNC_NONE = 0;
    // 正常完成同步并更新了清单文件信息
    private static final int SYNC_UPDATE = 1;
    // 未完成同步，失败
    private static final int SYNC_ERROR = 2;

    // 匹配版本号 x.y.z
    private final Pattern versionPattern = Pattern.compile("^(?<version>[\\d.]+)$");

    // 程序需要同步的软件库根目录
    @Value("${config.repo-path}")
    private String repoPath;

    // 当前环境 profile
    @Value("${spring.profiles.active}")
    private String activeProfile;

    /**
     * 运行Updater
     */
    public void run(String... args) {
        log.info("同步软件库开始...");

        // 清单文件目录 test环境下使用manifests-test目录
        Path manifestsDir = Path.of(repoPath, "manifests" + ("test".equals(activeProfile) ? "-" + activeProfile : ""));
        log.info("扫描清单文件目录 {}", manifestsDir.toAbsolutePath());
        if (!Files.exists(manifestsDir) || !Files.isDirectory(manifestsDir)) {
            log.error("清单文件目录不存在");
            return;
        }
        // 获取清单文件列表，目录下所有层级文件夹内的文件
        List<File> manifests = FileUtil.loopFiles(manifestsDir, pathname ->
                pathname.getName().toLowerCase().endsWith(".json") &&
                        pathname.isFile() &&
                        pathname.canRead() &&
                        pathname.canWrite());
        if (manifests.isEmpty()) {
            log.error("目录内无清单文件，同步取消");
            return;
        }
        log.info("共扫描到清单文件 {} 个", manifests.size());

        long startTime = System.currentTimeMillis();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // 统计同步结果
            int[] count = {0, 0, 0};
            // 同时执行同步的数量
            int asyncCount = Math.min(manifests.size(), 10);
            // 创建异步任务
            List<CompletableFuture<Integer>> futures = new ArrayList<>(asyncCount);
            // 获取同步结果
            Consumer<CompletableFuture<Integer>> futureConsumer = future -> {
                try {
                    count[future.get(30, TimeUnit.MINUTES)]++;
                } catch (Exception e) {
                    log.error("获取同步结果出错[{}]: {}", e.getClass().getSimpleName(), e.getMessage());
                }
            };
            for (File manifestFile : manifests) {
                futures.add(CompletableFuture.supplyAsync(() -> sync(manifestFile), executor));
                if (futures.size() == asyncCount) {
                    futures.forEach(futureConsumer);
                    futures.clear();
                }
            }
            if (!futures.isEmpty()) {
                // 剩余任务
                futures.forEach(futureConsumer);
                futures.clear();
            }
            log.info("同步完成: 本次更新 {} 个, 失败 {} 个, 未更新 {} 个", count[SYNC_UPDATE], count[SYNC_ERROR], count[SYNC_NONE]);
        } catch (Exception e) {
            log.error("同步出错[{}]: {}", e.getClass().getSimpleName(), e.getMessage());
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
    private int sync(File manifest) {
        try {
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
                    StrUtil.isBlank(author) ||
                    StrUtil.isBlank(description) ||
                    StrUtil.isBlank(category) || !categories.containsKey(category.toLowerCase()) ||
                    StrUtil.isBlank(platform) || !platforms.containsKey(platform.toLowerCase())) {
                log.error("manifest [{}] has illegal attribute", manifest.getName());
                return SYNC_ERROR;
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

            // 有更新的属性名称集合
            Map<String, Object> changedAttrs = new HashMap<>();

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

                    boolean isValidVersion = true;
                    Object checkVersion = checkUpdate.remove("version");
                    if (checkVersion instanceof String && !version.equals(checkVersion)) {
                        // 版本号有变更
                        String checkVersionStr = checkVersion.toString();
                        if (ReUtil.isMatch(versionPattern, checkVersionStr) && ReUtil.isMatch(versionPattern, version)
                                && StrUtil.compareVersion(checkVersionStr, version) < 0) {
                            // x.y.z类型版本号比较，如果脚本返回的版本号小于清单文件内的版本号，则跳过
                            log.warn("manifest [{}] version [{}] is great than check [{}]", manifest.getName(), version, checkVersion);
                            isValidVersion = false;
                        } else {
                            // 非语义化版本号有变更，直接更新清单文件属性
                            changedAttrs.put("version", checkVersion);
                        }
                    }

                    // 通过循环获取脚本返回的属性值是否跟已有清单文件内属性值一致
                    for (Map.Entry<?, ?> entry : checkUpdate.entrySet()) {
                        String key = (String) entry.getKey();
                        Object value = entry.getValue();

                        if (key == null || key.isEmpty() || value == null) {
                            continue;
                        }
                        key = key.toLowerCase();

                        // 按照key排序后比较
                        if (!JSON.toJSONString(value, JSONWriter.Feature.SortMapEntriesByKeys)
                                .equals(JSON.toJSONString(manifestJson.get(key), JSONWriter.Feature.SortMapEntriesByKeys))) {
                            if ("url".equals(key)) {
                                if (!isValidVersion) {
                                    // 合法的版本号才去更新url
                                    continue;
                                }
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
                                    log.warn("manifest [{}] url [{}] is not a valid url", manifest.getName(), value);
                                    // 链接不合法，版本号也不能更新，移除掉
                                    changedAttrs.remove("version");
                                    continue;
                                }
                            }
                            // 如果脚本返回的属性值跟清单文件内的属性值不一致，那么更新清单文件内的属性值
                            changedAttrs.put(key, value);
                        }
                    }
                }
            }

            if (!changedAttrs.isEmpty()) {
                // 将新版清单内容写入文件
                manifestJson.putAll(changedAttrs);
                FileUtil.writeUtf8String(JSON.toJSONString(manifestJson, JSONWriter.Feature.PrettyFormat), manifest);
                String manifestVersion = manifestJson.getString("version");
                if (!version.equals(manifestVersion)) {
                    // 版本有变更
                    log.info("manifest [{}] upgraded: {}->{}", manifest.getName(), version, manifestVersion);
                } else {
                    log.info("manifest [{}] attributes updated: {}", manifest.getName(), changedAttrs);
                }
                return SYNC_UPDATE;
            }

            return SYNC_NONE;
        } catch (Exception e) {
            log.error("sync [{}] error[{}]: {}", manifest.getName(), e.getClass().getSimpleName(), e.getMessage());
            return SYNC_ERROR;
        }
    }

    /**
     * 判断是否是合法url地址，不匹配 localhost
     */
    public boolean isUrl(String text) {
        return ReUtil.isMatch("^((https?|ftp):)?//[\\w.-]+(?:\\.[\\w.-]+)+[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+", text);
    }

}
