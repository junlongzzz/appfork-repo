package plus.junlong.appfork.script;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * 脚本文件内可使用的变量
 *
 * @author Junlong
 */
public final class ScriptVars {

    /**
     * HTTP请求 User-Agent
     */
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36";

    /**
     * HTTP 请求默认超时时间（从请求发出到收到响应的总时长），防止服务端接受连接后不返回数据导致无限等待
     */
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);

    /**
     * 默认的 HttpClient
     */
    public static final HttpClient HTTP_CLIENT = newHttpClientBuilder().build();

    /**
     * 创建一个 HttpClient.Builder
     * * 自动跟随重定向
     * * 连接超时 30s
     * * 使用虚拟线程
     */
    public static HttpClient.Builder newHttpClientBuilder() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(30))
                // 虚拟线程
                .executor(Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * 创建一个预设了请求超时与默认 User-Agent 的 HttpRequest.Builder
     * 脚本可在其基础上设置 uri 与请求方法，避免遗漏超时设置
     */
    public static HttpRequest.Builder newRequestBuilder() {
        return HttpRequest.newBuilder()
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT);
    }

}
