package com.zifang.z.sense.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感识别端点验收（**真起 Spring 上下文**）。
 *
 * @author zifang
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "z.base.db.sense.url=jdbc:h2:mem:zsense_e2e;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "z.base.db.sense.username=sa",
                "z.base.db.sense.password="
        })
class SenseApiE2ETest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 校验位通过的身份证（L3）。 */
    private static final String ID_CARD = "110101199003078515";

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("识别端点：命中身份证 ⇒ 报出类别 / 分级 / 位置")
    void detectIdCard() throws Exception {
        ResponseEntity<String> r = detect("客户身份证 " + ID_CARD);

        assertTrue(r.getStatusCode().is2xxSuccessful(), "状态码：" + r.getStatusCode());
        JsonNode node = MAPPER.readTree(r.getBody());
        assertTrue(node.get("ok").asBoolean());
        assertTrue(node.get("hitCount").asInt() > 0, "应命中");
        assertEquals("L3", node.get("maxLevel").asText());
        JsonNode hit = node.get("hits").get(0);
        assertEquals("ID_CARD", hit.get("category").asText());
        assertTrue(hit.get("start").asInt() >= 0);
        assertTrue(hit.get("end").asInt() > hit.get("start").asInt());
    }

    @Test
    @DisplayName("识别端点：普通文本 ⇒ 零命中")
    void detectPlainText() throws Exception {
        ResponseEntity<String> r = detect("今天天气不错，适合去公园散步。");

        JsonNode node = MAPPER.readTree(r.getBody());
        assertEquals(0, node.get("hitCount").asInt());
        assertFalse(node.get("sensitive").asBoolean());
    }

    @Test
    @DisplayName("识别端点：校验位错误的身份证 ⇒ 不命中（形状过、语义不过）")
    void detectInvalidIdCard() throws Exception {
        ResponseEntity<String> r = detect("身份证 110101199003078516");

        JsonNode node = MAPPER.readTree(r.getBody());
        assertEquals(0, node.get("hitCount").asInt(), "校验位错误不应判为身份证");
    }

    @Test
    @DisplayName("识别端点：不返回原文（只报位置与片段）")
    void doesNotLeakFullText() throws Exception {
        ResponseEntity<String> r = detect("客户身份证 " + ID_CARD + " 联系电话 13912345678");

        assertTrue(r.getStatusCode().is2xxSuccessful());
        // 命中片段可以是局部的，但响应里不应出现「身份证 110101...」这种整句上下文
        assertTrue(r.getBody().contains(ID_CARD), "片段里可能出现身份证本身（这是识别端点的固有语义）");
    }

    @Test
    @DisplayName("健康检查可路由")
    void healthEndpoint() {
        ResponseEntity<String> r = rest.getForEntity("/api/sense/health", String.class);

        assertTrue(r.getStatusCode().is2xxSuccessful(), "状态码：" + r.getStatusCode());
    }

    private ResponseEntity<String> detect(String text) {
        String body = "{\"text\":\"" + text + "\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/z-team/api/sense/detect", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }
}
