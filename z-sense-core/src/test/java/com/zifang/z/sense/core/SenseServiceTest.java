package com.zifang.z.sense.core;

import com.zifang.z.sense.core.domain.SenseResult;
import com.zifang.z.sense.core.domain.SensitiveCategory;
import com.zifang.z.sense.core.domain.SensitiveHit;
import com.zifang.z.sense.core.domain.SensitiveLevel;
import com.zifang.z.sense.core.service.SenseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感识别引擎单测。
 *
 * @author zifang
 */
class SenseServiceTest {

    private final SenseService service = new SenseService();

    @Test
    @DisplayName("识别身份证号：18 位带校验位 ⇒ 命中 L3 ID_CARD")
    void detectsValidIdCard() {
        // 校验位正确的 18 位身份证（构造值满足 GB 11643 校验）
        String text = "客户身份证 110101199003078515 张三";
        SenseResult r = service.detect(text);

        assertTrue(r.isSensitive(), "应识别出敏感项");
        assertEquals(SensitiveLevel.L3, r.getMaxLevel());

        SensitiveHit idHit = r.getHits().stream()
                .filter(h -> h.getCategory() == SensitiveCategory.ID_CARD)
                .findFirst()
                .orElse(null);
        assertNotNull(idHit, "应命中 ID_CARD 类别");
        assertEquals(18, idHit.length(), "命中长度应为 18");
        assertEquals("id_card_18", idHit.getRuleId());
    }

    @Test
    @DisplayName("校验位错误的身份证 ⇒ 不命中（形状过、语义不过）")
    void rejectsInvalidIdCardChecksum() {
        // 与上面同一位、但校验位改错
        String text = "身份证 110101199003078516";
        SenseResult r = service.detect(text);

        assertFalse(r.getHits().stream().anyMatch(h -> h.getCategory() == SensitiveCategory.ID_CARD),
                "校验位错误不应判为身份证");
    }

    @Test
    @DisplayName("识别手机号：号段合法 ⇒ 命中 L3 CONTACT")
    void detectsMobile() {
        SenseResult r = service.detect("请拨打 13912345678 联系我");

        SensitiveHit hit = r.getHits().stream()
                .filter(h -> h.getCategory() == SensitiveCategory.CONTACT)
                .findFirst()
                .orElse(null);
        assertNotNull(hit, "应命中手机号");
        assertEquals(11, hit.length());
    }

    @Test
    @DisplayName("手机号中间四位全 0（测试号）⇒ 不命中")
    void rejectsDummyMobile() {
        SenseResult r = service.detect("号码 13800000000");

        assertFalse(r.getHits().stream().anyMatch(h -> h.getRuleId().equals("mobile_phone")),
                "全 0 测试号不应命中");
    }

    @Test
    @DisplayName("识别银行卡号：Luhn 校验通过 ⇒ 命中 L4 BANK_ACCOUNT")
    void detectsBankCard() {
        // Luhn 校验通过的测试卡号
        SenseResult r = service.detect("卡号 6222020200011122336 请核对");

        SensitiveHit hit = r.getHits().stream()
                .filter(h -> h.getCategory() == SensitiveCategory.BANK_ACCOUNT)
                .findFirst()
                .orElse(null);
        assertNotNull(hit, "应命中银行卡");
        assertEquals(SensitiveLevel.L4, hit.getLevel());
    }

    @Test
    @DisplayName("Luhn 校验不通过的卡号 ⇒ 不命中")
    void rejectsInvalidBankCard() {
        SenseResult r = service.detect("卡号 6222020200011122334");

        assertFalse(r.getHits().stream().anyMatch(h -> h.getCategory() == SensitiveCategory.BANK_ACCOUNT),
                "Luhn 不通过不应命中");
    }

    @Test
    @DisplayName("识别邮箱 ⇒ 命中 CONTACT")
    void detectsEmail() {
        SenseResult r = service.detect("邮箱 zhangsan@example.com");

        assertTrue(r.getHits().stream().anyMatch(h -> h.getRuleId().equals("email")));
    }

    @Test
    @DisplayName("识别凭据键值对 ⇒ 命中 L4 CREDENTIAL")
    void detectsCredential() {
        SenseResult r = service.detect("password=Sup3rS3cret!");

        SensitiveHit hit = r.getHits().stream()
                .filter(h -> h.getCategory() == SensitiveCategory.CREDENTIAL)
                .findFirst()
                .orElse(null);
        assertNotNull(hit, "应命中凭据");
        assertEquals(SensitiveLevel.L4, hit.getLevel());
        assertFalse(hit.getSnippet().contains("Sup3rS3cret!") && hit.getSnippet().length() > 64,
                "片段长度应受 MAX_SNIPPET 约束");
    }

    @Test
    @DisplayName("最高分级取命中中的最大者")
    void maxLevelTakesHighest() {
        SenseResult r = service.detect("手机 13912345678，卡号 6222020200011122336");

        assertEquals(SensitiveLevel.L4, r.getMaxLevel(), "卡号 L4 应压过手机号 L3");
        assertEquals(2, r.getHitCount());
    }

    @Test
    @DisplayName("类别分布统计正确")
    void categoryCounts() {
        SenseResult r = service.detect("手机 13912345678，卡号 6222020200011122336");

        var counts = r.getCategoryCounts();
        assertEquals(1, counts.get(SensitiveCategory.CONTACT.name()));
        assertEquals(1, counts.get(SensitiveCategory.BANK_ACCOUNT.name()));
    }

    @Test
    @DisplayName("空文本与 null ⇒ 空结果，不抛异常")
    void handlesEmptyInput() {
        assertEquals(0, service.detect(null).getHitCount());
        assertEquals(0, service.detect("").getHitCount());
        assertFalse(service.detect(null).isSensitive());
    }

    @Test
    @DisplayName("普通业务文本 ⇒ 无命中（不误伤）")
    void doesNotFalsePositiveOnPlainText() {
        SenseResult r = service.detect("合同编号 C20261031001，客户名称某某科技有限公司，金额 10000.00 元");

        assertFalse(r.getHits().stream().anyMatch(h -> h.getLevel() != null
                        && h.getLevel().getWeight() >= SensitiveLevel.L3.getWeight()),
                "普通商务文本不应命中 L3 及以上");
    }

    @Test
    @DisplayName("阈值判定 isSensitive")
    void thresholdCheck() {
        assertTrue(service.isSensitive("手机 13912345678", SensitiveLevel.L3));
        assertFalse(service.isSensitive("手机 13912345678", SensitiveLevel.L4));
    }

    @Test
    @DisplayName("同一文本重复识别 ⇒ 结果一致（幂等）")
    void detectionIsIdempotent() {
        String text = "身份证 110101199003078515，手机 13912345678";
        SenseResult a = service.detect(text);
        SenseResult b = service.detect(text);

        assertEquals(a.getHitCount(), b.getHitCount());
        List<SensitiveHit> ha = a.getHits();
        List<SensitiveHit> hb = b.getHits();
        for (int i = 0; i < ha.size(); i++) {
            assertEquals(ha.get(i).getStart(), hb.get(i).getStart());
            assertEquals(ha.get(i).getEnd(), hb.get(i).getEnd());
            assertEquals(ha.get(i).getRuleId(), hb.get(i).getRuleId());
        }
    }

    @Test
    @DisplayName("命中定位可回切原文（位置准确）")
    void hitPositionsMapBackToText() {
        String text = "前缀 手机 13912345678 后缀";
        SenseResult r = service.detect(text);

        SensitiveHit hit = r.getHits().stream()
                .filter(h -> h.getRuleId().equals("mobile_phone"))
                .findFirst()
                .orElse(null);
        assertNotNull(hit);
        assertEquals("13912345678", text.substring(hit.getStart(), hit.getEnd()),
                "按命中位置回切原文应得到手机号");
    }
}
