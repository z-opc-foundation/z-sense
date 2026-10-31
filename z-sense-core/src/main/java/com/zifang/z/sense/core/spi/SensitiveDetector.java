package com.zifang.z.sense.core.spi;

import com.zifang.z.sense.core.domain.SenseResult;

/**
 * 敏感识别器 SPI —— **可插拔的识别实现**出口。
 *
 * <p>本仓内置的是规则引擎实现（{@code RuleSensitiveDetector}）。留这个 SPI 的原因：
 * 真正的敏感识别往往需要模型（正则 + 字典 + NER 模型 + DLP 引擎），
 * 业务方要能把自己的实现顶进来，而不必改本仓代码。
 *
 * <p><b>实现约定</b>：
 * <ul>
 *   <li>**零 Spring**：实现类不应依赖容器注入（由本仓 web 层装配时手动 new 或用
 *       {@code @Bean} 工厂方法）；</li>
 *   <li>**不得抛异常给调用方**：识别失败应返回空结果 + 记录日志，而不是让业务链路崩掉
 *       （安全组件拖垮主链路是典型事故）；</li>
 *   <li>实现应保证<b>幂等</b>：同一文本多次识别结果一致。</li>
 * </ul>
 *
 * @author zifang
 */
public interface SensitiveDetector {

    /**
     * 识别文本中的敏感项。
     *
     * @param text 待识别文本（可空；空串 / null ⇒ 返回空结果）
     * @return 识别结果（**永不为 null**）
     */
    SenseResult detect(String text);

    /**
     * 识别器标识（用于日志与多实现并存时的区分）。
     *
     * @return 标识
     */
    String name();
}
