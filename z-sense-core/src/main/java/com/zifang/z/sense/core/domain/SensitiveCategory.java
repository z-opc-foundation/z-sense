package com.zifang.z.sense.core.domain;

/**
 * 敏感数据**类别**（分类）与 {@link SensitiveLevel}（分级）正交：
 * 类别回答「这是什么数据」，分级回答「有多敏感」。
 *
 * <p>类别是脱敏策略的**选择键** —— 同一类别配一套策略（如「个人身份信息 ⇒ 保留前 3 后 4」），
 * 分级是策略的**强度调节**（L4 走更强的一档）。两者分开建模，策略表才不会退化成
 * 「L3 的身份证号 / L4 的身份证号」这种笛卡尔积。
 *
 * @author zifang
 */
public enum SensitiveCategory {

    /** 姓名 / 昵称等身份标识。 */
    PERSONAL_NAME("个人身份"),
    /** 手机号 / 座机 / 邮箱等联系方式。 */
    CONTACT("联系方式"),
    /** 身份证 / 护照 / 社保号等证件号。 */
    ID_CARD("证件号码"),
    /** 银行卡 / 支付账号等金融账号。 */
    BANK_ACCOUNT("金融账号"),
    /** 住址 / 户籍等位置信息。 */
    ADDRESS("位置信息"),
    /** 密钥 / 口令 / 令牌等凭据。 */
    CREDENTIAL("凭据"),
    /** 源码 / 配置 / 算法等知识产权。 */
    IP("知识产权"),
    /** 商业合同 / 报价 / 客户名单等商业秘密。 */
    BUSINESS_SECRET("商业秘密"),
    /** 组织内部一般经营信息。 */
    INTERNAL_INFO("内部信息"),
    /** 未归类 —— 兜底类别。 */
    OTHER("其它");

    private final String label;

    SensitiveCategory(String label) {
        this.label = label;
    }

    /** 中文标签。 */
    public String getLabel() {
        return label;
    }

    /**
     * 宽松解析类别字符串（接受枚举名、中文标签；不可识别返回 {@code null}）。
     *
     * @param code 类别字符串（可空）
     * @return 解析结果或 {@code null}
     */
    public static SensitiveCategory fromCode(String code) {
        if (code == null) {
            return null;
        }
        String s = code.trim();
        if (s.isEmpty()) {
            return null;
        }
        for (SensitiveCategory c : values()) {
            if (c.name().equalsIgnoreCase(s) || c.label.equals(s)) {
                return c;
            }
        }
        return null;
    }
}
