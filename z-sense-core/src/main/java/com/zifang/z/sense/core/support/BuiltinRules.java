package com.zifang.z.sense.core.support;

import com.zifang.z.sense.core.domain.SenseRule;
import com.zifang.z.sense.core.domain.SensitiveCategory;
import com.zifang.z.sense.core.domain.SensitiveLevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 内置规则目录（M1 基线集）。
 *
 * <p><b>设计口径</b>：
 * <ul>
 *   <li>规则**带语义校验**（身份证校验位、手机号号段、银行卡 Luhn）⇒ 形状像但语义不成立的
 *       值不会被误判为敏感；</li>
 *   <li>规则**优先级**实现「先长后短」：18 位身份证优先于 15 位；</li>
 *   <li>规则是**数据不是代码** —— 下游可从库里取规则覆盖内置集（M2 接规则表）。</li>
 * </ul>
 *
 * <p><b>刻意不做的事</b>：不做语义理解（那句话是不是在骂人）、不做上下文判定
 * （这个手机号是不是客服号）。M1 是「形状 + 校验」层，语义层留给可插拔的
 * {@link com.zifang.z.sense.core.spi.SensitiveDetector} 实现。
 *
 * @author zifang
 */
public final class BuiltinRules {

    private BuiltinRules() {
    }

    /**
     * 构造内置规则集（每次调用返回**新列表**，规则对象本身可安全共享 —— 它们不可变）。
     *
     * @return 内置规则列表
     */
    public static List<SenseRule> rules() {
        List<SenseRule> list = new ArrayList<>();

        // ---- 证件号码：身份证（18 位，带校验位）----
        // 优先级 10：先于 15 位旧证匹配
        list.add(applyValidator(SenseRule.of("id_card_18", "身份证号(18位)", SensitiveCategory.ID_CARD,
                        SensitiveLevel.L3, "(?<![0-9A-Za-z])(\\d{17}[0-9Xx])(?![0-9A-Za-z])", 10),
                BuiltinRules::validIdCard));

        // 15 位旧身份证（无校验位，形态为 6 位地区 + 6 位生日 + 3 位序）
        list.add(SenseRule.of("id_card_15", "身份证号(15位)", SensitiveCategory.ID_CARD,
                SensitiveLevel.L3, "(?<![0-9A-Za-z])(\\d{6}\\d{6}\\d{3}[0-9Xx])(?![0-9A-Za-z])", 11));

        // ---- 联系方式：手机号 ----
        // 13x-19x 开头；号段校验：中间四位不能全 0
        list.add(applyValidator(SenseRule.of("mobile_phone", "手机号", SensitiveCategory.CONTACT,
                        SensitiveLevel.L3, "(?<![0-9])(1[3-9]\\d{9})(?![0-9])", 20),
                BuiltinRules::validMobile));

        // 邮箱
        list.add(SenseRule.of("email", "电子邮箱", SensitiveCategory.CONTACT,
                SensitiveLevel.L3, "([A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,})", 21));

        // ---- 金融账号：银行卡（Luhn 校验）----
        list.add(applyValidator(SenseRule.of("bank_card", "银行卡号", SensitiveCategory.BANK_ACCOUNT,
                        SensitiveLevel.L4, "(?<![0-9])([0-9]{13,19})(?![0-9])", 30),
                BuiltinRules::validBankCard));

        // ---- 凭据：密钥 / 口令 / token ----
        // 只抓「键=值」形态里明显像凭据的值，避免误伤普通文本
        list.add(SenseRule.of("credential_kv", "凭据键值对", SensitiveCategory.CREDENTIAL,
                SensitiveLevel.L4,
                "(?i)\\b(?:password|passwd|pwd|secret|token|apikey|api_key|access_key)\\b\\s*[:=]\\s*[\"']?([^\\s\"',;]{6,64})", 40));

        // ---- 位置信息：身份证地址 / 详细地址（含门牌号）----
        list.add(SenseRule.of("address_detail", "详细地址", SensitiveCategory.ADDRESS,
                SensitiveLevel.L2, "([\\u4e00-\\u9fa5]{2,20}(?:省|市|区|县)[\\u4e00-\\u9fa5]{0,20}(?:路|街|道|巷|弄)\\d{1,5}号)", 50));

        // ---- 个人身份：姓名（启发式：姓 + 1~2 字，弱规则，L2）----
        // ⚠ 弱规则必须带**姓氏白名单 + 通用词排除**：「手机」「卡号」这类词也是 2 个汉字，
        //   不加排除会把整篇文档的普通词全判成人名（实测「手机 139…」误判出「手机」）。
        list.add(applyValidator(SenseRule.of("person_name", "中文姓名", SensitiveCategory.PERSONAL_NAME,
                        SensitiveLevel.L2, "(?:^|[\\s,，、:：])([\\u4e00-\\u9fa5]{2,4})(?=[,，、。;；\\s]|$)", 60),
                BuiltinRules::validPersonName));

        // ---- 知识产权：代码里的疑似密钥常量（第二道凭据防线）----
        list.add(SenseRule.of("secret_constant", "疑似密钥常量", SensitiveCategory.CREDENTIAL,
                SensitiveLevel.L4, "(?i)\\b(?:SECRET|PRIVATE_KEY|ACCESS_KEY)\\b\\s*=\\s*[\"']([^\"']{8,128})[\"']", 45));

        return list;
    }

    /** 给规则挂上校验器（builder 风格，避免构造后再 set 的两段式）。 */
    private static SenseRule applyValidator(SenseRule rule, SenseRule.RuleValidator validator) {
        rule.setValidator(validator);
        return rule;
    }

    // ------------------------------------------------------------------
    // 校验器实现
    // ------------------------------------------------------------------

    /**
     * 身份证校验（GB 11643-1999 校验位算法）。
     *
     * @param value  18 位身份证
     * @param groups 捕获组
     * @return 是否成立
     */
    static boolean validIdCard(String value, List<String> groups) {
        if (value == null || value.length() != 18) {
            return false;
        }
        // 前 17 位必须是数字
        for (int i = 0; i < 17; i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        // 生日位（第 7~14 位）必须是合法日期
        String birth = value.substring(6, 14);
        int year = Integer.parseInt(birth.substring(0, 4));
        int month = Integer.parseInt(birth.substring(4, 6));
        int day = Integer.parseInt(birth.substring(6, 8));
        if (year < 1900 || year > 2100 || month < 1 || month > 12 || day < 1 || day > 31) {
            return false;
        }
        // 校验位：加权因子 7 9 10 5 8 4 2 1 6 3 7 9 10 5 8 4 2，映射码 "10X98765432"
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checkCodes = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (value.charAt(i) - '0') * weights[i];
        }
        char expected = checkCodes[sum % 11];
        char actual = Character.toUpperCase(value.charAt(17));
        return expected == actual;
    }

    /**
     * 手机号校验：号段 + 中间位不能全 0（排除 13800000000 这类测试号）。
     *
     * @param value  手机号
     * @param groups 捕获组
     * @return 是否成立
     */
    static boolean validMobile(String value, List<String> groups) {
        if (value == null || value.length() != 11) {
            return false;
        }
        if (!value.matches("1[3-9]\\d{9}")) {
            return false;
        }
        // 中间 4 位（索引 3..6）不能全 0
        String mid = value.substring(3, 7);
        return !mid.equals("0000");
    }

    /**
     * 银行卡号校验：Luhn 校验位。
     *
     * @param value  卡号
     * @param groups 捕获组
     * @return 是否成立
     */
    static boolean validBankCard(String value, List<String> groups) {
        if (value == null || value.length() < 13 || value.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = value.length() - 1; i >= 0; i--) {
            int d = value.charAt(i) - '0';
            if (d < 0 || d > 9) {
                return false;
            }
            if (doubleDigit) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    /**
     * 常见测试号（不应被判为敏感）。
     *
     * @param value  待测值
     * @return 是否是「明显假的」号码
     */
    public static boolean looksLikeDummy(String value) {
        if (value == null) {
            return false;
        }
        List<String> dummies = Arrays.asList(
                "13800000000", "13800138000", "11111111111", "00000000000",
                "12345678901", "11010119900307001X");
        return dummies.contains(value);
    }

    /**
     * 姓名校验器：**姓氏白名单 + 通用词黑名单**双闸。
     *
     * <p>姓名识别是弱规则（中文 2~4 字太泛），所以必须收紧：
     * <ul>
     *   <li>首字必须是**常见姓氏**（百家姓常见字集合）⇒ 「手机」「卡号」的首字不在其中，被挡；</li>
     *   <li>整词命中**通用词黑名单**（手机/卡号/合同/金额…）⇒ 双保险。</li>
     * </ul>
     * 代价：少数**单姓/复姓未收录**的人名会漏检 —— 对 L2 级弱规则而言，宁漏不误是对的取舍
     * （误报的代价是下游把普通词当人名脱敏，业务数据被搞脏）。
     *
     * @param value  候选姓名
     * @param groups 捕获组
     * @return 是否判定为姓名
     */
    static boolean validPersonName(String value, List<String> groups) {
        if (value == null || value.length() < 2 || value.length() > 4) {
            return false;
        }
        if (COMMON_WORDS.contains(value)) {
            return false;
        }
        // 组合词黑名单：普通短语首字恰好是姓氏（「都是本人」⇒ 首字「都」是低频姓）。
        // 姓名弱规则宁可漏检 ⇒ 这类组合词一律放弃。
        if (PHRASE_BLACKLIST.contains(value)) {
            return false;
        }
        return SURNAMES.contains(value.charAt(0));
    }

    /**
     * 常见姓氏首字（百家姓高频字 + 复姓首字，覆盖绝大多数中文姓名）。
     *
     * <p>⚠ 用 {@code unmodifiableSet(new LinkedHashSet<>(Arrays.asList(...)))} 而不是
     * {@code Set.of(...)}：后者遇到**重复元素直接抛 IllegalArgumentException**
     * （本集合有 17 个重复首字，如「司」「公」既是单姓也是复姓首字）⇒ 初始化期就炸。
     */
    private static final java.util.Set<Character> SURNAMES = surnames();

    /** 构造姓氏首字集合（去重 + 不可变）。 */
    private static java.util.Set<Character> surnames() {
        List<Character> chars = new ArrayList<>(Arrays.asList(
                '赵', '钱', '孙', '李', '周', '吴', '郑', '王', '冯', '陈', '褚', '卫', '蒋', '沈', '韩', '杨',
                '朱', '秦', '尤', '许', '何', '吕', '施', '张', '孔', '曹', '严', '华', '金', '魏', '陶', '姜',
                '戚', '谢', '邹', '喻', '柏', '水', '窦', '章', '云', '苏', '潘', '葛', '奚', '范', '彭', '郎',
                '鲁', '韦', '昌', '马', '苗', '凤', '花', '方', '俞', '任', '袁', '柳', '酆', '鲍', '史', '唐',
                '费', '廉', '岑', '薛', '雷', '贺', '倪', '汤', '滕', '殷', '罗', '毕', '郝', '邬', '安', '常',
                '乐', '于', '时', '傅', '皮', '卞', '齐', '康', '伍', '余', '元', '卜', '顾', '孟', '平', '黄',
                '和', '穆', '萧', '尹', '姚', '邵', '湛', '汪', '祁', '毛', '禹', '狄', '米', '贝', '明', '臧',
                '计', '伏', '成', '戴', '谈', '宋', '茅', '庞', '熊', '纪', '舒', '屈', '项', '祝', '董', '梁',
                '杜', '阮', '蓝', '闵', '席', '季', '麻', '强', '贾', '路', '娄', '危', '江', '童', '颜', '郭',
                '梅', '盛', '林', '刁', '钟', '徐', '邱', '骆', '高', '夏', '蔡', '田', '樊', '胡', '凌', '霍',
                '虞', '万', '支', '柯', '昝', '管', '卢', '莫', '房', '裘', '缪', '干', '解', '应', '宗', '丁',
                '宣', '贲', '邓', '郁', '单', '杭', '洪', '包', '诸', '左', '石', '崔', '吉', '钮', '龚', '程',
                '嵇', '邢', '滑', '裴', '陆', '荣', '翁', '荀', '羊', '於', '惠', '甄', '曲', '家', '封', '芮',
                '羿', '储', '靳', '汲', '邴', '糜', '松', '井', '段', '富', '巫', '乌', '焦', '巴', '弓', '牧',
                '隗', '山', '谷', '车', '侯', '宓', '蓬', '全', '郗', '班', '仰', '秋', '仲', '伊', '宫', '宁',
                '仇', '栾', '暴', '甘', '钭', '厉', '戎', '祖', '武', '符', '刘', '景', '詹', '束', '龙', '叶',
                '幸', '司', '韶', '郜', '黎', '蓟', '薄', '印', '宿', '白', '怀', '蒲', '邰', '从', '鄂', '索',
                '咸', '籍', '赖', '卓', '蔺', '屠', '蒙', '池', '乔', '阴', '胥', '能', '苍', '双', '闻',
                '莘', '党', '翟', '谭', '贡', '劳', '逄', '姬', '申', '扶', '堵', '冉', '宰', '郦', '雍', '卻',
                '璩', '桑', '桂', '濮', '牛', '寿', '通', '边', '扈', '燕', '冀', '浦', '尚', '农', '温', '别',
                '庄', '晏', '柴', '瞿', '阎', '充', '慕', '连', '茹', '习', '宦', '艾', '鱼', '容', '向', '古',
                '易', '慎', '戈', '廖', '庾', '终', '暨', '居', '衡', '步', '都', '耿', '满', '弘', '匡', '国',
                '文', '寇', '广', '禄', '阙', '东', '欧', '殳', '沃', '利', '蔚', '越', '夔', '隆', '师', '巩',
                '厍', '聂', '晁', '勾', '敖', '融', '冷', '訾', '辛', '阚', '那', '简', '饶', '空', '曾', '毋',
                '沙', '乜', '养', '鞠', '须', '丰', '巢', '关', '蒯', '相', '查', '后', '荆', '红', '游', '竺',
                '权', '逯', '盖', '益', '桓', '公', '万', '司', '上', '欧', '夏', '诸', '闻',
                '东', '赫', '尉', '公', '澹', '宗', '濮', '淳', '太', '申', '仲', '轩', '令', '钟', '宇', '长',
                '慕', '鲜', '闾', '司', '亓', '巫', '颛', '壤', '漆', '乐', '谷', '拓', '夹', '百', '呼', '第', '公'));
        return java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(chars));
    }

    /**
     * 通用词黑名单（易与 2~4 字中文人名混淆的常用词）。
     *
     * <p>⚠ 同样用 {@code LinkedHashSet} 去重构造 —— {@code Set.of} 遇重复元素直接抛异常。
     */
    private static final java.util.Set<String> COMMON_WORDS = java.util.Collections.unmodifiableSet(
            new java.util.LinkedHashSet<>(Arrays.asList(
                    "手机", "电话", "号码", "卡号", "银行", "账号", "合同", "金额", "地址", "姓名", "性别", "民族",
                    "出生", "证件", "类型", "状态", "备注", "邮箱", "密码", "用户", "客户", "公司", "部门", "职位",
                    "员工", "发票", "订单", "商品", "数量", "单价", "总计", "标题", "内容", "日期", "时间",
                    "开始", "结束", "创建", "更新", "删除", "查询", "结果", "数据", "文件", "图片", "表格", "附件")));

    /**
     * 组合词黑名单（首字是低频姓的普通短语）。
     *
     * <p>姓氏白名单里有「都」「查」「相」「和」「于」这类**低频姓 / 多音姓**，
     * 它们是普通短语的开头高频字 ⇒ 光靠姓氏白名单挡不住（实测「都是本人」被判成人名）。
     * 这类组合词直接放弃 —— 姓名是 L2 弱规则，**漏检的代价远小于误报**。
     */
    private static final java.util.Set<String> PHRASE_BLACKLIST = java.util.Collections.unmodifiableSet(
            new java.util.LinkedHashSet<>(Arrays.asList(
                    "都是本人", "都是自", "于是", "和平", "和信", "相等", "查询结", "查找到", "相同",
                    "和为", "和美", "都有", "和谐", "于今", "于是乎", "查看", "查阅", "相片", "相同点",
                    "相比", "相应", "相望", "查询", "查收", "查证", "查明", "和平共", "和气",
                    "都爱", "都是", "于下", "于公", "查档", "照片", "相等值", "个人", "个别")));
}
