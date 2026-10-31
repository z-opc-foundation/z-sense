package com.zifang.z.sense.core.domain;

/**
 * 敏感数据分级（L1–L4）。
 *
 * <p><b>分级口径</b>（对齐《数据安全法》与行业通行实践，M1 用四级）：
 * <ul>
 *   <li>{@link #L1} 公开 —— 可对外披露，泄露无实质影响；</li>
 *   <li>{@link #L2} 内部 —— 仅限组织内部流转，泄露有一般影响；</li>
 *   <li>{@link #L3} 敏感 —— 泄露有较大影响（个人信息、商业秘密一般项），脱敏默认档；</li>
 *   <li>{@link #L4} 核心 —— 泄露有严重影响（个人敏感信息、核心商业秘密、密钥），**强制脱敏**。</li>
 * </ul>
 *
 * <p><b>为什么要显式建模而不是用字符串</b>：分级直接决定下游行为（是否强制脱敏、是否留痕、
 * 留存期多长），散成字符串迟早出现 "L3" / "3" / "level3" 三种写法 ⇒ 本仓用枚举收口，
 * 并提供 {@link #fromCode(String)} 做外部字符串的**宽松解析**。
 *
 * <p><b>比较口径</b>：本枚举实现 {@link Comparable}（由 {@link Enum} 提供），
 * 声明顺序与权重一致（L1 → L4）；但**下游判阈值请用 {@link #atLeast(SensitiveLevel)}**
 * 或 {@link #getWeight()}，不要依赖 {@code compareTo} 的顺序语义。
 *
 * @author zifang
 */
public enum SensitiveLevel {

    /** 公开：可对外披露。 */
    L1(1, "公开"),
    /** 内部：仅限组织内部流转。 */
    L2(2, "内部"),
    /** 敏感：脱敏默认档。 */
    L3(3, "敏感"),
    /** 核心：强制脱敏 + 强制留痕。 */
    L4(4, "核心");

    private final int weight;
    private final String label;

    SensitiveLevel(int weight, String label) {
        this.weight = weight;
        this.label = label;
    }

    /** 权重（1..4，越大越敏感）。 */
    public int getWeight() {
        return weight;
    }

    /** 中文标签（供接口展示与文档对照）。 */
    public String getLabel() {
        return label;
    }

    /**
     * 宽松解析外部传入的分级字符串。
     *
     * <p>接受 {@code "L3"} / {@code "l3"} / {@code "3"} / {@code "敏感"}（中文标签）四种写法。
     *
     * @param code 分级字符串（可空）
     * @return 解析出的分级；无法识别时返回 {@code null}（**不抛异常** —— 由调用方决定兜底档）
     */
    public static SensitiveLevel fromCode(String code) {
        if (code == null) {
            return null;
        }
        String s = code.trim();
        if (s.isEmpty()) {
            return null;
        }
        String upper = s.toUpperCase();
        if (upper.length() == 1 && upper.charAt(0) >= '1' && upper.charAt(0) <= '4') {
            return values()[upper.charAt(0) - '1'];
        }
        if (upper.length() == 2 && upper.charAt(0) == 'L'
                && upper.charAt(1) >= '1' && upper.charAt(1) <= '4') {
            return values()[upper.charAt(1) - '1'];
        }
        for (SensitiveLevel lv : values()) {
            if (lv.label.equals(s)) {
                return lv;
            }
        }
        return null;
    }

    /**
     * 与指定分级比较（**分级不小于给定值**）。
     *
     * <p>注意：枚举自带的 {@code compareTo} 按**声明顺序**比较（恰好与权重一致，
     * 但那是巧合不是契约）⇒ 下游判阈值一律用本方法，别直接用 {@code compareTo}。
     *
     * @param other 参照分级（{@code null} ⇒ 视为最敏感，即恒真）
     * @return 本级别是否达到参照级别
     */
    public boolean atLeast(SensitiveLevel other) {
        if (other == null) {
            return true;
        }
        return this.weight >= other.weight;
    }
}
