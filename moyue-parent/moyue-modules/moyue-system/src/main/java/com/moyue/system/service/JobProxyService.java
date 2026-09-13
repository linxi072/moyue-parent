package com.moyue.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyue.common.exception.BizException;
import com.moyue.common.core.domain.ResultCode;
import com.moyue.common.core.domain.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * XXL-Job Admin 代理服务（定时任务管理真源为 XXL-Job Admin 库，本服务只做 HTTP 代理）。
 *
 * <p>登录：POST {admin}/login（表单 userName/password，yml {@code xxl.job.admin.username/password}），
 * 成功后从 Set-Cookie 提取 XXL_LOGIN_ID 会话 cookie 并保持；会话失效自动重登一次。</p>
 *
 * <p>容错：调度中心不可达 / 返回异常一律转 BizException(INTERNAL_ERROR)，附中文提示；
 * 登录 cookie 通过 synchronized 串行化重登，避免并发风暴。</p>
 */
@Service
public class JobProxyService {

    private static final Logger log = LoggerFactory.getLogger(JobProxyService.class);

    /** XXL-Job Admin 登录成功的响应体约定（原始字符串 "200"） */
    private static final String LOGIN_SUCCESS = "200";

    /** ReturnT 成功码 */
    private static final int RETURN_T_SUCCESS = 200;

    private final RestTemplate restTemplate = new RestTemplate();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Admin 会话 cookie（XXL_LOGIN_ID=...），volatile 保证可见性 */
    private volatile String sessionCookie;

    /** 重登锁（串行化并发重登） */
    private final Object loginLock = new Object();

    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;

    @Value("${xxl.job.admin.username:admin}")
    private String adminUsername;

    @Value("${xxl.job.admin.password:123456}")
    private String adminPassword;

    // ====================== 公开代理方法 ======================

    /** 任务分页：代理 /jobinfo/pageList（DataTables 格式 → PageResult） */
    public PageResult<Map<String, Object>> pageJobs(int page, int size, Integer jobGroup,
                                                    Integer triggerStatus, String jobDesc, String executorHandler) {
        MultiValueMap<String, String> form = basePageForm(page, size);
        form.add("jobGroup", String.valueOf(jobGroup == null ? 0 : jobGroup));
        form.add("triggerStatus", String.valueOf(triggerStatus == null ? -1 : triggerStatus));
        form.add("jobDesc", nullToEmpty(jobDesc));
        form.add("executorHandler", nullToEmpty(executorHandler));
        form.add("author", "");
        JsonNode root = postForJson("/jobinfo/pageList", form);
        return dataTablesToPageResult(root, page, size);
    }

    /** 任务详情：代理 /jobinfo/loadById */
    public Map<String, Object> loadJob(long id) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", String.valueOf(id));
        JsonNode root = postForJson("/jobinfo/loadById", form);
        JsonNode content = root.get("content");
        if (root.path("code").asInt(-1) != RETURN_T_SUCCESS || content == null || content.isNull()) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "任务不存在（id=" + id + "）");
        }
        return objectMapper.convertValue(content,
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
    }

    /** 新增任务：代理 /jobinfo/add */
    public void addJob(MultiValueMap<String, String> form) {
        JsonNode root = postForJson("/jobinfo/add", form);
        assertReturnT(root, "新增任务失败");
    }

    /** 修改任务：代理 /jobinfo/update */
    public void updateJob(MultiValueMap<String, String> form) {
        JsonNode root = postForJson("/jobinfo/update", form);
        assertReturnT(root, "修改任务失败");
    }

    /** 删除任务：代理 /jobinfo/remove */
    public void removeJob(long id) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", String.valueOf(id));
        JsonNode root = postForJson("/jobinfo/remove", form);
        assertReturnT(root, "删除任务失败");
    }

    /** 启动任务：代理 /jobinfo/start */
    public void startJob(long id) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", String.valueOf(id));
        JsonNode root = postForJson("/jobinfo/start", form);
        assertReturnT(root, "启动任务失败");
    }

    /** 停止任务：代理 /jobinfo/stop */
    public void stopJob(long id) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", String.valueOf(id));
        JsonNode root = postForJson("/jobinfo/stop", form);
        assertReturnT(root, "停止任务失败");
    }

    /** 手动执行一次：代理 /jobinfo/trigger */
    public void triggerJob(long id, String executorParam, String addressList) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", String.valueOf(id));
        form.add("executorParam", nullToEmpty(executorParam));
        form.add("addressList", nullToEmpty(addressList));
        JsonNode root = postForJson("/jobinfo/trigger", form);
        assertReturnT(root, "执行任务失败");
    }

    /** 调度日志分页：代理 /joblog/pageList（DataTables 格式 → PageResult） */
    public PageResult<Map<String, Object>> pageLogs(int page, int size, Integer jobGroup, Long jobId,
                                                    Integer logStatus, String filterTime) {
        MultiValueMap<String, String> form = basePageForm(page, size);
        form.add("jobGroup", String.valueOf(jobGroup == null ? 0 : jobGroup));
        form.add("jobId", String.valueOf(jobId == null ? -1 : jobId));
        form.add("logStatus", String.valueOf(logStatus == null ? -1 : logStatus));
        form.add("filterTime", nullToEmpty(filterTime));
        JsonNode root = postForJson("/joblog/pageList", form);
        return dataTablesToPageResult(root, page, size);
    }

    // ====================== 登录与会话 ======================

    /** 登录 Admin 并保存会话 cookie（synchronized 防并发重登风暴） */
    private void login() {
        synchronized (loginLock) {
            String url = adminUrl("/login");
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("userName", adminUsername);
            form.add("password", adminPassword);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            try {
                org.springframework.http.ResponseEntity<String> resp =
                        restTemplate.postForEntity(url, new HttpEntity<>(form, headers), String.class);
                if (!LOGIN_SUCCESS.equals(resp.getBody())) {
                    throw new BizException(ResultCode.INTERNAL_ERROR,
                            "XXL-Job Admin 登录失败（检查 xxl.job.admin.username/password 配置）");
                }
                String cookie = resp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
                if (cookie != null) {
                    // 只保留 name=value 部分（不含 Path/HttpOnly 属性）
                    int semicolon = cookie.indexOf(';');
                    this.sessionCookie = semicolon > 0 ? cookie.substring(0, semicolon) : cookie;
                } else {
                    this.sessionCookie = null;
                    throw new BizException(ResultCode.INTERNAL_ERROR, "XXL-Job Admin 登录成功但未返回会话 cookie");
                }
                log.info("[job-proxy] XXL-Job Admin 登录成功：{}", adminAddresses);
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                throw new BizException(ResultCode.INTERNAL_ERROR,
                        "XXL-Job Admin 不可达：" + adminAddresses + "（" + e.getMessage() + "）");
            }
        }
    }

    /** 带会话 POST 表单请求；未登录 / 会话失效自动重登一次后重试 */
    private JsonNode postForJson(String path, MultiValueMap<String, String> form) {
        JsonNode root = doPostForJson(path, form, false);
        // Admin 返回未登录（body 为 "200" 以外的短串）或 JSON 解析失败时重登一次
        if (root == null) {
            root = doPostForJson(path, form, true);
            if (root == null) {
                throw new BizException(ResultCode.INTERNAL_ERROR, "XXL-Job Admin 接口响应异常：" + path);
            }
        }
        return root;
    }

    private JsonNode doPostForJson(String path, MultiValueMap<String, String> form, boolean forceRelogin) {
        try {
            if (forceRelogin || sessionCookie == null) {
                login();
            }
            String url = adminUrl(path);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            if (sessionCookie != null) {
                headers.add(HttpHeaders.COOKIE, sessionCookie);
            }
            org.springframework.http.ResponseEntity<String> resp =
                    restTemplate.postForEntity(url, new HttpEntity<>(form, headers), String.class);
            String body = resp.getBody();
            if (body == null || body.isBlank() || !body.trim().startsWith("{")) {
                // 非 JSON（如被重定向到登录页）→ 返回 null 触发重登
                return null;
            }
            return objectMapper.readTree(body);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            if (forceRelogin) {
                throw new BizException(ResultCode.INTERNAL_ERROR,
                        "调用 XXL-Job Admin 失败：" + path + "（" + e.getMessage() + "）");
            }
            return null;
        }
    }

    // ====================== 工具 ======================

    private MultiValueMap<String, String> basePageForm(int page, int size) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        // DataTables 分页参数：start 从 0 起
        form.add("start", String.valueOf(Math.max(0, (page - 1) * size)));
        form.add("length", String.valueOf(size));
        return form;
    }

    /** DataTables 响应 → PageResult（recordsFiltered 为总数） */
    private PageResult<Map<String, Object>> dataTablesToPageResult(JsonNode root, int page, int size) {
        PageResult<Map<String, Object>> pr = new PageResult<>();
        pr.setPage(page);
        pr.setSize(size);
        if (root == null) {
            pr.setTotal(0);
            pr.setRecords(new ArrayList<>());
            return pr;
        }
        pr.setTotal(root.path("recordsFiltered").asLong(root.path("recordsTotal").asLong(0)));
        JsonNode data = root.get("data");
        var type = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, objectMapper.getTypeFactory()
                        .constructMapType(Map.class, String.class, Object.class));
        pr.setRecords(objectMapper.convertValue(data == null ? java.util.Collections.emptyList() : data, type));
        return pr;
    }

    /** ReturnT 校验（code=200 成功，否则带 msg 抛业务异常） */
    private void assertReturnT(JsonNode root, String action) {
        int code = root == null ? -1 : root.path("code").asInt(-1);
        if (code != RETURN_T_SUCCESS) {
            String msg = root == null ? "响应为空" : root.path("msg").asText("未知原因");
            throw new BizException(ResultCode.INTERNAL_ERROR, action + "：" + msg);
        }
    }

    private String adminUrl(String path) {
        String base = adminAddresses == null ? "" : adminAddresses.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
