package com.zifang.z.sense.starter;

import com.example.embedhost.StarterHostTestApplication;
import com.zifang.z.sense.core.service.SenseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * z-sense starter <b>嵌入式装配</b>验收（M2 #23）。
 *
 * <p>判据：宿主只把 starter jar 放进 classpath（**无 web、无手工 @Import**）⇒ 引擎 bean 必须自己长出来。
 * 走的机制是 {@code AutoConfiguration.imports}，不是组件扫描 —— 后者会把笔误掩盖掉（见
 * {@link StarterHostTestApplication} 的说明）。
 *
 * @author zifang
 */
class StarterEmbedTest {

    @Test
    @DisplayName("★ 宿主只放 starter jar（非 web 上下文）⇒ 识别服务经 imports 机制自动装配")
    void hostEmbeddingStarterGetsEngineBean() {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(StarterHostTestApplication.class)
                .web(WebApplicationType.NONE)
                .run()) {

            assertNotNull(ctx.getBean(SenseService.class),
                    "★ 宿主应用必须能仅靠 AutoConfiguration.imports 装配出 SenseService");
        }
    }
}
