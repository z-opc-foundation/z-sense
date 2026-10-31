package com.zifang.z.sense.core.domain;

import java.util.regex.Pattern;

/**
 * 一条敏感识别规则（**规则即数据**，可入库、可下发、可热更）。
 *
 * <p>规则由五要素构成：<b>识别模式</b>（{@link #pattern}）、<b>类别</b>、<b>分级</b>、
 * <b>校验器</b>（{@link #validator}，可选）、<b>优先级</b>（{@link #priority}）。
 *
 * <p><b>为什么要把校验器单列</b>：正则只能保证「形状像」，不能保证「是真的」。
 * 身份证号要过校验位、手机号要过号段、银行卡要过 Luhn —— 这些都超出正则能力。
 * 本仓用 {@link #validator} 挂一个可选校验函数，形状过了再过语义 ⇒ 误报率可控。
 *
 * <p><b>优先级口径</b>：数字小的先匹配；同优先级按注册顺序。
 * 用于「先长后短」的场景（先匹��� {@code 18 位身份证} 再匹配 {@code 15 位}）。
 *
 * @author zifang
 */
public class SenseRule {

    /** 规则标识（唯一，稳定 —— 命中记录要靠它追溯）。 */
    private String ruleId;

    /** 规则名称（人读的）。 */
    private String ruleName;

    /** 敏感类别。 */
    private SensitiveCategory category;

    /** 敏感分级。 */
    private SensitiveLevel level;

    /** 识别正则（**必须带捕获组**；第 1 个捕获组是敏感值本体，其余是校验位等辅助组）。 */
    private Pattern pattern;

    /** 校验器（可选）：对捕获组 1 做语义校验，返回 {@code false} ⇒ 放弃该命中。 */
    private RuleValidator validator;

    /** 优先级（数字小的先匹配）。 */
    private int priority;

    /** 是否启用。 */
    private boolean enabled = true;

    /** 规则备注。 */
    private String remark;

    /**
     * 规则校验器：形状匹配之后、命中成立之前的语义校验。
     */
    @FunctionalInterface
    public interface RuleValidator {
        /**
         * 校验捕获到的敏感值是否成立。
         *
         * @param value  捕获组 1（敏感值本体）
         * @param groups 全部捕获组（组 0 是整段匹配）
         * @return {@code true} 校验通过（成立）；{@code false} 放弃该命中
         */
        boolean validate(String value, java.util.List<String> groups);
    }

    public SenseRule() {
    }

    public SenseRule(String ruleId, String ruleName, SensitiveCategory category, SensitiveLevel level,
                     Pattern pattern, int priority) {
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.category = category;
        this.level = level;
        this.pattern = pattern;
        this.priority = priority;
    }

    /** 便捷构造：从正则字符串建规则。 */
    public static SenseRule of(String ruleId, String ruleName, SensitiveCategory category,
                               SensitiveLevel level, String regex, int priority) {
        return new SenseRule(ruleId, ruleName, category, level, Pattern.compile(regex), priority);
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public SensitiveCategory getCategory() {
        return category;
    }

    public void setCategory(SensitiveCategory category) {
        this.category = category;
    }

    public SensitiveLevel getLevel() {
        return level;
    }

    public void setLevel(SensitiveLevel level) {
        this.level = level;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public void setPattern(Pattern pattern) {
        this.pattern = pattern;
    }

    public RuleValidator getValidator() {
        return validator;
    }

    public void setValidator(RuleValidator validator) {
        this.validator = validator;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    @Override
    public String toString() {
        return "SenseRule{" + ruleId + ", " + category + "/" + level + ", prio=" + priority + "}";
    }
}
