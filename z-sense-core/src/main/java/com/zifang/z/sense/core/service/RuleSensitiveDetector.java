package com.zifang.z.sense.core.service;

import com.zifang.z.sense.core.domain.SenseResult;
import com.zifang.z.sense.core.domain.SenseRule;
import com.zifang.z.sense.core.domain.SensitiveHit;
import com.zifang.z.sense.core.spi.SensitiveDetector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则引擎实现：按优先级顺序跑规则集，产出带定位的命中列表。
 *
 * <p><b>算法口径</b>：
 * <ol>
 *   <li>规则按 {@link SenseRule#getPriority()} 升序（数字小的先跑），同优先级按注册顺序；</li>
 *   <li>每条规则用 {@link Matcher#find()} 扫描全文（不是 {@code matches} —— 要找多处）；</li>
 *   <li>命中位置 = <b>第 1 个捕获组</b>的起止（没有捕获组则用整段匹配）；</li>
 *   <li>校验器不通过 ⇒ 放弃该命中（不算误报）；</li>
 *   <li>结果做**重叠消解**（级别高者留），并按起点升序输出。</li>
 * </ol>
 *
 * <p><b>为什么不自己实现正则引擎</b>：用 JDK 的 {@link Pattern}，不引第三方正则库
 * （组织口径：第三方依赖由 z-boot-parent 统一锁版本，本仓不引新件）。
 *
 * <p><b>性能口径</b>：M1 是逐条规则线性扫描（O(规则数 × 文本长度)），对万字文本 + 数十条规则
 * 足够（毫秒级）。真要提速，M2 换 Aho-Corasick 多模式匹配 —— 但那属于「规则上量」之后的事，
 * 现在不预先优化。
 *
 * @author zifang
 */
public class RuleSensitiveDetector implements SensitiveDetector {

    /** 规则集（已按优先级排好序）。 */
    private final List<SenseRule> rules;

    public RuleSensitiveDetector(List<SenseRule> rules) {
        List<SenseRule> copy = new ArrayList<>();
        if (rules != null) {
            for (SenseRule r : rules) {
                if (r != null && r.isEnabled() && r.getPattern() != null) {
                    copy.add(r);
                }
            }
        }
        copy.sort(Comparator.comparingInt(SenseRule::getPriority));
        this.rules = copy;
    }

    /** 当前生效的规则数。 */
    public int ruleCount() {
        return rules.size();
    }

    @Override
    public String name() {
        return "rule-engine";
    }

    @Override
    public SenseResult detect(String text) {
        long begin = System.currentTimeMillis();
        SenseResult result = new SenseResult(text == null ? 0 : text.length());
        if (text == null || text.isEmpty()) {
            result.setElapsedMillis(System.currentTimeMillis() - begin);
            return result;
        }
        for (SenseRule rule : rules) {
            Matcher matcher = rule.getPattern().matcher(text);
            while (matcher.find()) {
                int start;
                int end;
                String value;
                if (matcher.groupCount() >= 1 && matcher.group(1) != null) {
                    start = matcher.start(1);
                    end = matcher.end(1);
                    value = matcher.group(1);
                } else {
                    start = matcher.start();
                    end = matcher.end();
                    value = matcher.group();
                }
                if (start < 0 || end <= start) {
                    continue;
                }
                // 校验器：形状过了再过语义
                if (rule.getValidator() != null) {
                    List<String> groups = new ArrayList<>();
                    for (int i = 0; i <= matcher.groupCount(); i++) {
                        groups.add(matcher.group(i));
                    }
                    boolean ok;
                    try {
                        ok = rule.getValidator().validate(value, groups);
                    } catch (RuntimeException e) {
                        // 校验器异常 ⇒ 视为不成立（宁可漏报也不误报）
                        ok = false;
                    }
                    if (!ok) {
                        continue;
                    }
                }
                SensitiveHit hit = new SensitiveHit(rule.getCategory(), rule.getLevel(), start, end,
                        snippet(text, start, end), rule.getRuleId());
                result.addHit(hit);
            }
        }
        result.setElapsedMillis(System.currentTimeMillis() - begin);
        return result;
    }

    /** 截取命中片段（超长截断，两端各留一半便于辨认上下文）。 */
    private static String snippet(String text, int start, int end) {
        int len = end - start;
        if (len <= SensitiveHit.MAX_SNIPPET) {
            return text.substring(start, end);
        }
        int head = SensitiveHit.MAX_SNIPPET / 2;
        int tail = SensitiveHit.MAX_SNIPPET - head;
        return text.substring(start, start + head) + "…" + text.substring(end - tail, end);
    }
}
