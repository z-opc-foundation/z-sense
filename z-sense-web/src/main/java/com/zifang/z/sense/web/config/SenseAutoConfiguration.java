package com.zifang.z.sense.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * z-sense <b>管控面（web）自动装配</b>。
 *
 * <p><b>只管部署关切</b>：本域数据源 + 健康检查。
 * <b>引擎装配已拆到 {@code z-sense-starter}</b>（M2 #23）——
 * {@link com.zifang.z.sense.core.service.SenseService} 由
 * {@code com.zifang.z.sense.starter.config.SenseStarterAutoConfiguration} 提供，
 * 本类不再重复定义（重复定义会靠 {@code @ConditionalOnMissingBean} 侥幸让位，但读代码的人会以为有两处真装配）。
 *
 * <p>宿主引用本仓时：只想嵌入识别能力 ⇒ 依赖 {@code z-sense-starter}（轻）；
 * 想跑完整管控面 ⇒ 依赖 {@code z-sense-web}（它传递带上 starter）。
 *
 * <p>⚠️ 本装配**不扫描**包树（无 {@code @ComponentScan}）—— 跨域交互按组织口径走 HTTP 边界，
 * 不把别人的 jar 拖进本仓上下文。
 *
 * @author zifang
 */
@AutoConfiguration
@Import(SenseModuleDataSource.class)
public class SenseAutoConfiguration {

    /**
     * 本域健康检查（探活用）。
     *
     * @return 健康标识
     */
    @Bean
    @ConditionalOnMissingBean
    public SenseHealthIndicator senseHealthIndicator() {
        return new SenseHealthIndicator("z-sense");
    }
}
