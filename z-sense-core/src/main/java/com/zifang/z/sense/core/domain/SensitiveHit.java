package com.zifang.z.sense.core.domain;

/**
 * 一条敏感命中结果（**定位粒度**）。
 *
 * <p>本仓的命中结果**必带定位**（起止偏移 + 原文片段），因为下游 z-mask 的脱敏动作
 * 是「按位置替换」而不是「按正则重新扫一遍」——重新扫会与识别规则漂移，
 * 而按位置替换才是「识别一次、脱敏一次」的确定口径。
 *
 * @author zifang
 */
public class SensitiveHit {

    /** 命中的敏感类别。 */
    private SensitiveCategory category;

    /** 命中的敏感分级。 */
    private SensitiveLevel level;

    /** 命中的字符起始偏移（含）。 */
    private int start;

    /** 命中的字符结束偏移（**不含**，即 [start, end)）。 */
    private int end;

    /** 命中的原文片段（截断到 {@link #MAX_SNIPPET} 长度，避免大对象进日志）。 */
    private String snippet;

    /** 命中的规则标识（哪条规则命中的 —— 可追溯、可统计）。 */
    private String ruleId;

    /** 命中处脱敏后的展示值（识别引擎顺手算好，脱敏仓可直接用，也可自行重算）。 */
    private String maskedPreview;

    /** 片段最大长度（防止把整篇文档塞进命中记录）。 */
    public static final int MAX_SNIPPET = 64;

    public SensitiveHit() {
    }

    public SensitiveHit(SensitiveCategory category, SensitiveLevel level, int start, int end,
                        String snippet, String ruleId) {
        this.category = category;
        this.level = level;
        this.start = start;
        this.end = end;
        this.snippet = snippet;
        this.ruleId = ruleId;
    }

    /** 命中长度（end - start）。 */
    public int length() {
        return Math.max(0, end - start);
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

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getEnd() {
        return end;
    }

    public void setEnd(int end) {
        this.end = end;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getMaskedPreview() {
        return maskedPreview;
    }

    public void setMaskedPreview(String maskedPreview) {
        this.maskedPreview = maskedPreview;
    }

    @Override
    public String toString() {
        return "SensitiveHit{cat=" + category + ", level=" + level
                + ", range=[" + start + "," + end + "), rule=" + ruleId + "}";
    }
}
