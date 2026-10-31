package com.zifang.z.sense.core.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次识别的**完整结果**。
 *
 * <p>除命中列表外，还给出两个聚合口径（下游最常用的两个问题）：
 * <ul>
 *   <li><b>最高分级</b> {@link #getMaxLevel()} —— 「这份数据最高有多敏感」⇒ 决定整体防护强度；</li>
 *   <li><b>类别分布</b> {@link #getCategoryCounts()} —— 「这份数据含哪些类别的敏感项」⇒
 *       决定脱敏策略组合。</li>
 * </ul>
 *
 * <p><b>命中去重口径</b>：同一位置被多条规则命中时，**保留级别更高的那条**（更敏感优先），
 * 级别相同时保留先注册的那条 ⇒ 同一文本重复识别结果稳定（幂等）。
 *
 * @author zifang
 */
public class SenseResult {

    /** 命中列表（按起点升序）。 */
    private final List<SensitiveHit> hits = new ArrayList<>();

    /** 被识别的原始文本长度（供调用方核对覆盖完整性）。 */
    private int textLength;

    /** 识别耗时（毫秒）—— 性能观测用。 */
    private long elapsedMillis;

    public SenseResult() {
    }

    public SenseResult(int textLength) {
        this.textLength = textLength;
    }

    /**
     * 加入一条命中，并做**重叠消解**：位置重叠时保留级别更高的一条。
     *
     * @param hit 命中（可空）
     * @return 实际被保留的命中（若被更高优先级的命中挤出，返回 {@code null}）
     */
    public SensitiveHit addHit(SensitiveHit hit) {
        if (hit == null) {
            return null;
        }
        for (SensitiveHit exist : hits) {
            if (overlaps(exist, hit)) {
                // 级别更高者留；若新命中级别更高，替换旧命中。
                if (comparePriority(hit, exist) > 0) {
                    int idx = hits.indexOf(exist);
                    hits.set(idx, hit);
                    return hit;
                }
                return null;
            }
        }
        hits.add(hit);
        return hit;
    }

    /** 两个命中区间是否重叠。 */
    private static boolean overlaps(SensitiveHit a, SensitiveHit b) {
        return a.getStart() < b.getEnd() && b.getStart() < a.getEnd();
    }

    /** 命中优先级比较：级别优先，级别相同按注册顺序（先到先留）。 */
    private static int comparePriority(SensitiveHit a, SensitiveHit b) {
        int la = a.getLevel() == null ? 0 : a.getLevel().getWeight();
        int lb = b.getLevel() == null ? 0 : b.getLevel().getWeight();
        return Integer.compare(la, lb);
    }

    /** 命中列表（按起点升序的只读视图）。 */
    public List<SensitiveHit> getHits() {
        List<SensitiveHit> copy = new ArrayList<>(hits);
        copy.sort((x, y) -> Integer.compare(x.getStart(), y.getStart()));
        return Collections.unmodifiableList(copy);
    }

    /** 命中总数。 */
    public int getHitCount() {
        return hits.size();
    }

    /** 是否命中过任何敏感项。 */
    public boolean isSensitive() {
        return !hits.isEmpty();
    }

    /** 最高分级（无命中时返回 {@link SensitiveLevel#L1}，不是 {@code null}）。 */
    public SensitiveLevel getMaxLevel() {
        SensitiveLevel max = SensitiveLevel.L1;
        for (SensitiveHit h : hits) {
            if (h.getLevel() != null && h.getLevel().getWeight() > max.getWeight()) {
                max = h.getLevel();
            }
        }
        return max;
    }

    /** 类别分布（类别名 → 命中条数）。 */
    public Map<String, Integer> getCategoryCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SensitiveHit h : hits) {
            if (h.getCategory() != null) {
                counts.merge(h.getCategory().name(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /** 达到指定分级及以上的命中条数（分级不小于给定值）。 */
    public int countAtLeast(SensitiveLevel threshold) {
        if (threshold == null) {
            return hits.size();
        }
        int n = 0;
        for (SensitiveHit h : hits) {
            if (h.getLevel() != null && h.getLevel().getWeight() >= threshold.getWeight()) {
                n++;
            }
        }
        return n;
    }

    public int getTextLength() {
        return textLength;
    }

    public void setTextLength(int textLength) {
        this.textLength = textLength;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public void setElapsedMillis(long elapsedMillis) {
        this.elapsedMillis = elapsedMillis;
    }

    @Override
    public String toString() {
        return "SenseResult{hits=" + hits.size() + ", maxLevel=" + getMaxLevel() + "}";
    }
}
