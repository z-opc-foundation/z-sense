package com.zifang.z.sense.web.api;

import com.zifang.z.sense.core.domain.SenseResult;
import com.zifang.z.sense.core.domain.SensitiveHit;
import com.zifang.z.sense.core.domain.SensitiveLevel;
import com.zifang.z.sense.core.service.SenseService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 敏感识别端点。
 *
 * <p><b>路由前缀</b>：{@code /z-team/api/sense/**}。
 *
 * <p><b>返回的是命中明细**（位置 / 类别 / 分级），**不返回原文** ——
 * 识别端点只回答「哪里敏感」，脱敏是 z-mask 的活。
 *
 * @author zifang
 */
@RestController
@RequestMapping("/z-team/api/sense")
public class SenseController {

    /** 识别服务（可空 ⇒ 未装配）。 */
    private final ObjectProvider<SenseService> senseServiceProvider;

    /**
     * 构造。
     *
     * @param senseServiceProvider 识别服务提供者
     */
    public SenseController(ObjectProvider<SenseService> senseServiceProvider) {
        this.senseServiceProvider = senseServiceProvider;
    }

    /**
     * 识别一段文本里的敏感数据。
     *
     * @param request 请求体
     * @return 命中明细
     */
    @PostMapping("/detect")
    public ResponseEntity<Map<String, Object>> detect(@RequestBody DetectRequest request) {
        SenseService service = senseServiceProvider.getIfAvailable();
        if (service == null) {
            return ResponseEntity.status(503).body(error("识别服务未装配"));
        }
        SenseResult result = service.detect(request.getText());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("hitCount", result.getHitCount());
        body.put("sensitive", result.isSensitive());
        body.put("maxLevel", result.getMaxLevel() == null ? null : result.getMaxLevel().name());
        body.put("categoryCounts", result.getCategoryCounts());
        body.put("elapsedMillis", result.getElapsedMillis());
        body.put("hits", result.getHits().stream().map(SenseController::hitView).toList());
        return ResponseEntity.ok(body);
    }

    /**
     * 命中详情视图。
     *
     * @param hit 命中
     * @return 视图
     */
    private static Map<String, Object> hitView(SensitiveHit hit) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("ruleId", hit.getRuleId());
        view.put("category", hit.getCategory() == null ? null : hit.getCategory().name());
        view.put("level", hit.getLevel() == null ? null : hit.getLevel().name());
        view.put("start", hit.getStart());
        view.put("end", hit.getEnd());
        view.put("snippet", hit.getSnippet());
        return view;
    }

    /** 错误体。 */
    private static Map<String, Object> error(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("message", message);
        return body;
    }

    /**
     * 识别请求体。
     */
    public static class DetectRequest {

        /** 待识别文本。 */
        private String text;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}
