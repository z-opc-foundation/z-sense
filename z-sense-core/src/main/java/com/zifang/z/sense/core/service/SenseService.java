package com.zifang.z.sense.core.service;

import com.zifang.z.sense.core.domain.SenseResult;
import com.zifang.z.sense.core.domain.SenseRule;
import com.zifang.z.sense.core.spi.SensitiveDetector;
import com.zifang.z.sense.core.support.BuiltinRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 敏感识别门面 —— 本仓对外的**唯一主入口**。
 *
 * <p><b>为什么有门面而不是直接用 {@link RuleSensitiveDetector}</b>：识别能力会被换实现
 * （规则引擎 → DLP 引擎 → 模型引擎）。门面把「用哪个实现」这件事收在一处，
 * 消费方（z-biz-security、z-mask）只依赖门面 ⇒ 换实现不动消费方。
 *
 * <p><b>线程安全</b>：门面持有不可变规则列表与不可变 detector 实例，
 * {@link #detect(String)} 无状态 ⇒ 可安全并发调用。
 *
 * @author zifang
 */
public class SenseService {

    /** 实际干活的识别器。 */
    private final SensitiveDetector detector;

    /** 生效规则（只读）。 */
    private final List<SenseRule> rules;

    /** 用内置规则集构造（默认口径）。 */
    public SenseService() {
        this(BuiltinRules.rules());
    }

    /**
     * 用指定规则集构造。
     *
     * @param rules 规则集（可空 ⇒ 空规则 ⇒ 识别不到任何东西）
     */
    public SenseService(List<SenseRule> rules) {
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules == null ? List.of() : rules));
        this.detector = new RuleSensitiveDetector(this.rules);
    }

    /**
     * 用外部实现构造（换实现入口 —— 比如接 DLP 引擎）。
     *
     * @param detector 识别器实现（**不可为 null**）
     */
    public SenseService(SensitiveDetector detector) {
        if (detector == null) {
            throw new IllegalArgumentException("识别器实现不可为 null");
        }
        this.detector = detector;
        this.rules = Collections.unmodifiableList(new ArrayList<>());
    }

    /**
     * 识别文本中的敏感项（**永不抛异常**，识别失败返回空结果）。
     *
     * @param text 待识别文本（可空）
     * @return 识别结果（永不为 null）
     */
    public SenseResult detect(String text) {
        try {
            return detector.detect(text);
        } catch (RuntimeException e) {
            // 安全组件不得拖垮主链路：识别异常 ⇒ 空结果 + 记日志
            return new SenseResult(text == null ? 0 : text.length());
        }
    }

    /**
     * 判断文本是否敏感（便捷方法）。
     *
     * @param text 待识别文本
     * @param threshold 判定阈值（{@code null} ⇒ 有任何命中即算敏感）
     * @return 是否达到阈值
     */
    public boolean isSensitive(String text, com.zifang.z.sense.core.domain.SensitiveLevel threshold) {
        SenseResult r = detect(text);
        if (threshold == null) {
            return r.isSensitive();
        }
        return r.countAtLeast(threshold) > 0;
    }

    /** 当前生效的规则集（只读）。 */
    public List<SenseRule> getRules() {
        return rules;
    }

    /** 识别器标识。 */
    public String detectorName() {
        return detector.name();
    }
}
