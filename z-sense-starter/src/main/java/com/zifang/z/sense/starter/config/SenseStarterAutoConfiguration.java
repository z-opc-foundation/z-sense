package com.zifang.z.sense.starter.config;

import com.zifang.z.sense.core.service.SenseService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * z-sense <b>starter 自动装配</b>（引擎装配，M2 #23 starter 拆分）。
 *
 * <p><b>定位</b>：本类只负责把<b>引擎</b>（{@link SenseService}）装配进宿主应用的 Spring 上下文。
 * 宿主只需把 {@code z-sense-starter} 放进 classpath，{@code META-INF/spring/
 * org.springframework.boot.autoconfigure.AutoConfiguration.imports} 会把本装配挂上 ⇒ 零手工接线。
 *
 * <p><b>为什么不放 web</b>：早先引擎装配长在 {@code z-sense-web} 里 ⇒ 宿主要嵌入识别能力就得连
 * {@code spring-boot-starter-web}、健康检查、{@code z-boot-datasource-starter}（druid + mybatis +
 * mysql 驱动）一起拖进来。对「已经在跑自己 web 服务 / 自己数据源」的宿主而言全是包袱。
 * starter 把引擎单独摘出来 ⇒ 引擎是引擎，部署是部署。
 *
 * <p><b>让位口径</b>：全部 {@code @ConditionalOnMissingBean} ⇒ 宿主自己装配了同类型 bean（比如想用
 * 自定义规则集的 {@code SenseService}）时本装配让位，不覆盖宿主的选择。
 *
 * <p>⚠️ 本装配**不扫描**包树（无 {@code @ComponentScan}）—— 跨域交互按组织口径走 HTTP 边界，
 * 不把别人的 jar 拖进本仓上下文。
 *
 * @author zifang
 */
@AutoConfiguration
public class SenseStarterAutoConfiguration {

    /**
     * 敏感识别服务（**内置规则集**）。
     *
     * <p>{@code @ConditionalOnMissingBean} ⇒ 宿主自己装配了同类型 bean 时本仓让位
     * （比如宿主想用自定义规则集）。
     *
     * @return 识别服务
     */
    @Bean
    @ConditionalOnMissingBean
    public SenseService senseService() {
        return new SenseService();
    }
}
