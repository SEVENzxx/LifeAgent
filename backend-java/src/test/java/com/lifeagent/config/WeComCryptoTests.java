package com.lifeagent.config;

import com.lifeagent.config.WeComCrypto.DecryptResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WeCom 加解密与安全 XML 解析测试。
 *
 * <p>验证签名算法正确性、AES 加解密往返（PKCS#7 填充到 32 字节倍数）、
 * 安全 XML 解析防护。</p>
 */
class WeComCryptoTests {

    private static final String TOKEN = "QDG6eK";
    private static final String CORP_ID = "wx5823bf96d3bd56c7";
    private static final String ENCODING_AES_KEY = "jWmYm7qr5FMoAMwV8KNiK8YY6GGL3GjH1jTz1Jc4C4T";

    // WeChat 官方文档测试向量（不同 key）
    private static final String OFFICIAL_AES_KEY = "jWmYm7qr5nMoAUwZRjGtBxmz3KA1tkAj3ykkR6q2B2C";
    private static final String OFFICIAL_ENCRYPT_XML = """
            <xml>
                <ToUserName><![CDATA[wx5823bf96d3bd56c7]]></ToUserName>
                <Encrypt><![CDATA[RypEvHKD8QQKFhvQ6QleEB4J58tiPdvo+rtK1I9qca6aM/wvqnLSV5zEPeusUiX5L5X/0lWfrf0QADHHhGd3QczcdCUpj911L3vg3W/sYYvuJTs3TUUkSUXxaccAS0qhxchrRYt66wiSpGLYL42aM6A8dTT+6k4aSknmPj48kzJs8qLjvd4Xgpue06DOdnLxAUHzM6+kDZ+HMZfJYuR+LtwGc2hgf5gsijff0ekUNXZiqATP7PF5mZxZ3Izoun1s4zG4LUMnvw2r+KqCKIw+3IQH03v+BCA9nMELNqbSf6tiWSrXJB3LAVGUcallcrw8V2t9EL4EhzJWrQUax5wLVMNS0+rUPA3k22Ncx4XXZS9o0MBH27Bo6BpNelZpS+/uh9KsNlY6bHCmJU9p8g7m3fVKn28H3KDYA5Pl/T8Z1ptDAVe0lXdQ2YoyyH2uyPIGHBZZIs2pDBS8R07+qN+E7Q==]]></Encrypt>
                <AgentID><![CDATA[218]]></AgentID>
            </xml>""";
    private static final String OFFICIAL_EXPECTED_XML = """
            <xml>
                <ToUserName><![CDATA[wx5823bf96d3bd56c7]]></ToUserName>
                <FromUserName><![CDATA[mycreate]]></FromUserName>
                <CreateTime>1409659813</CreateTime>
                <MsgType><![CDATA[text]]></MsgType>
                <Content><![CDATA[hello]]></Content>
                <MsgId>4561255354251345929</MsgId>
                <AgentID>218</AgentID>
            </xml>""";

    private static final String TIMESTAMP = "1409659813";
    private static final String NONCE = "1372623149";

    // ==================== 加解密往返 ====================

    @Test
    @DisplayName("AES 加解密往返：加密再解密得到原文")
    void encryptDecryptRoundTrip() {
        WeComCrypto crypto = new WeComCrypto(ENCODING_AES_KEY, TOKEN);
        assertRoundTrip(crypto, "<xml><Content>hello</Content></xml>");
    }

    @Test
    @DisplayName("加密内容含中文时往返正确")
    void encryptDecryptWithChinese() {
        WeComCrypto crypto = new WeComCrypto(ENCODING_AES_KEY, TOKEN);
        assertRoundTrip(crypto, "<xml><Content>你好世界</Content></xml>");
    }

    @Test
    @DisplayName("密文被篡改时解密抛异常")
    void tamperedCiphertextFails() {
        WeComCrypto crypto = new WeComCrypto(ENCODING_AES_KEY, TOKEN);
        String encrypted = crypto.encryptContent("<xml>test</xml>", CORP_ID);
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "AAAA";
        assertThrows(RuntimeException.class, () -> crypto.decryptAndParse(tampered));
    }

    // ==================== PKCS#7-32 填充边界 ====================

    @Test
    @DisplayName("PKCS7-32 填充值 = 1 边界")
    void paddingValue1() {
        WeComCrypto crypto = new WeComCrypto(ENCODING_AES_KEY, TOKEN);
        assertRoundTrip(crypto, "x".repeat(25)); // 总长 ≡ 31 (mod 32)，补 1
    }

    @Test
    @DisplayName("PKCS7-32 填充值 = 16 边界")
    void paddingValue16() {
        WeComCrypto crypto = new WeComCrypto(ENCODING_AES_KEY, TOKEN);
        assertRoundTrip(crypto, "x".repeat(10)); // 总长 ≡ 16 (mod 32)，补 16
    }

    @Test
    @DisplayName("PKCS7-32 填充值 = 17 边界")
    void paddingValue17() {
        WeComCrypto crypto = new WeComCrypto(ENCODING_AES_KEY, TOKEN);
        assertRoundTrip(crypto, "x".repeat(9)); // 总长 ≡ 15 (mod 32)，补 17
    }

    @Test
    @DisplayName("PKCS7-32 填充值 = 31 边界")
    void paddingValue31() {
        WeComCrypto crypto = new WeComCrypto(ENCODING_AES_KEY, TOKEN);
        assertRoundTrip(crypto, "x".repeat(27)); // 总长 ≡ 1 (mod 32)，补 31
    }

    @Test
    @DisplayName("PKCS7-32 填充值 = 32 边界（加满一整块）")
    void paddingValue32() {
        WeComCrypto crypto = new WeComCrypto(ENCODING_AES_KEY, TOKEN);
        assertRoundTrip(crypto, "x".repeat(26)); // 总长 ≡ 0 (mod 32)，补 32
    }

    @Test
    @DisplayName("1～63 字节内容长度全覆盖填充边界")
    void paddingMixedLengths() {
        WeComCrypto crypto = new WeComCrypto(ENCODING_AES_KEY, TOKEN);
        for (int len = 1; len <= 63; len++) {
            assertRoundTrip(crypto, "a".repeat(len));
        }
    }

    // ==================== 官方测试向量解密 ====================

    @Test
    @DisplayName("解析并解密 WeChat 官方 POST 回调测试向量")
    void decryptOfficialTestVector() {
        WeComXmlParser parser = new WeComXmlParser();
        WeComXmlParser.CallbackXml result = parser.parseCallbackXml(OFFICIAL_ENCRYPT_XML);
        assertEquals("wx5823bf96d3bd56c7", result.toUserName());
        assertEquals("218", result.agentId());

        // 使用官方文档的 key 解密
        WeComCrypto crypto = new WeComCrypto(OFFICIAL_AES_KEY, TOKEN);
        DecryptResult decrypt = crypto.decryptAndParse(result.encrypt());

        assertEquals(CORP_ID, decrypt.receiveId());
        String normalizedActual = decrypt.content().replaceAll("\\s+", "");
        String normalizedExpected = OFFICIAL_EXPECTED_XML.replaceAll("\\s+", "");
        assertEquals(normalizedExpected, normalizedActual);
    }

    // ==================== 签名验证 ====================

    @Test
    @DisplayName("签名算法自验证：相同输入产生相同签名")
    void signatureSelfConsistent() {
        WeComCrypto crypto = new WeComCrypto(ENCODING_AES_KEY, TOKEN);
        String encrypted = crypto.encryptContent("<xml><Content>test</Content></xml>", CORP_ID);
        String signature = crypto.generateSignature(TIMESTAMP, NONCE, encrypted);

        assertTrue(crypto.verifySignature(signature, TIMESTAMP, NONCE, encrypted));
        assertFalse(crypto.verifySignature("0000000000000000000000000000000000000000", TIMESTAMP, NONCE, encrypted));
    }

    @Test
    @DisplayName("签名算法：不同加密内容产生不同签名")
    void differentContentDifferentSignature() {
        WeComCrypto crypto = new WeComCrypto(ENCODING_AES_KEY, TOKEN);
        String sig1 = crypto.generateSignature(TIMESTAMP, NONCE, crypto.encryptContent("<xml>a</xml>", CORP_ID));
        String sig2 = crypto.generateSignature(TIMESTAMP, NONCE, crypto.encryptContent("<xml>b</xml>", CORP_ID));
        assertNotEquals(sig1, sig2);
    }

    @Test
    @DisplayName("签名算法：不同 timestamp 产生不同签名")
    void differentTimestampDifferentSignature() {
        WeComCrypto crypto = new WeComCrypto(ENCODING_AES_KEY, TOKEN);
        String encrypted = crypto.encryptContent("<xml>test</xml>", CORP_ID);
        String sig1 = crypto.generateSignature("1000000000", NONCE, encrypted);
        String sig2 = crypto.generateSignature("2000000000", NONCE, encrypted);
        assertNotEquals(sig1, sig2);
    }

    @Test
    @DisplayName("签名验证：token 不同时失败")
    void wrongTokenFailsSignature() {
        WeComCrypto crypto = new WeComCrypto(ENCODING_AES_KEY, TOKEN);
        WeComCrypto wrongTokenCrypto = new WeComCrypto(ENCODING_AES_KEY, "WRONG_TOKEN");

        String encrypted = crypto.encryptContent("<xml>test</xml>", CORP_ID);
        String signature = crypto.generateSignature(TIMESTAMP, NONCE, encrypted);

        assertFalse(wrongTokenCrypto.verifySignature(signature, TIMESTAMP, NONCE, encrypted));
    }

    // ==================== 密钥初始化 ====================

    @Test
    @DisplayName("非法 EncodingAESKey 抛出异常")
    void invalidEncodingAesKey() {
        assertThrows(IllegalArgumentException.class,
                () -> new WeComCrypto("too-short-key", TOKEN));
    }

    @Test
    @DisplayName("EncodingAESKey 43 字符解码成功")
    void valid43CharKeyDecodes() {
        assertNotNull(new WeComCrypto(ENCODING_AES_KEY, TOKEN));
    }

    // ==================== 响应解析 ====================

    @Test
    @DisplayName("解密的 receiveId 匹配 CorpID")
    void decryptReceiveIdMatchesCorpId() {
        WeComCrypto crypto = new WeComCrypto(ENCODING_AES_KEY, TOKEN);
        String encrypted = crypto.encryptContent("<xml>test</xml>", CORP_ID);
        assertEquals(CORP_ID, crypto.decryptAndParse(encrypted).receiveId());
    }

    // ==================== XML 解析 ====================

    @Test
    @DisplayName("解析 POST 回调外层 XML，提取 Encrypt、ToUserName、AgentID")
    void parseCallbackXml() {
        WeComXmlParser parser = new WeComXmlParser();
        WeComXmlParser.CallbackXml result = parser.parseCallbackXml(OFFICIAL_ENCRYPT_XML);

        assertEquals("wx5823bf96d3bd56c7", result.toUserName());
        assertEquals("218", result.agentId());
        assertNotNull(result.encrypt());
        assertTrue(result.encrypt().startsWith("RypEvHKD"));
    }

    @Test
    @DisplayName("解析自定义文本消息 XML")
    void parseTextMessageXml() {
        WeComXmlParser parser = new WeComXmlParser();
        String xml = "<xml><ToUserName><![CDATA[corp1]]></ToUserName>"
                + "<FromUserName><![CDATA[user1]]></FromUserName>"
                + "<CreateTime>1234567890</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[hello]]></Content>"
                + "<MsgId>1234567890123456</MsgId>"
                + "<AgentID>1</AgentID></xml>";

        WeComXmlParser.TextMessageXml msg = parser.parseTextMessageXml(xml);

        assertEquals("corp1", msg.toUserName());
        assertEquals("user1", msg.fromUserName());
        assertEquals("1234567890", msg.createTime());
        assertEquals("text", msg.msgType());
        assertEquals("hello", msg.content());
        assertEquals("1234567890123456", msg.msgId());
        assertEquals("1", msg.agentId());
    }

    @Test
    @DisplayName("DTD 声明导致解析失败（XXE 防护）")
    void dtdRejected() {
        WeComXmlParser parser = new WeComXmlParser();
        String xmlWithDtd = """
                <?xml version="1.0"?>
                <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <xml><Content>&xxe;</Content></xml>
                """;
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseTextMessageXml(xmlWithDtd));
    }

    @Test
    @DisplayName("超大 XML body 被拒绝")
    void oversizedXmlRejected() {
        WeComXmlParser parser = new WeComXmlParser();
        String oversized = "<xml><Content>" + "x".repeat(70000) + "</Content></xml>";
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseTextMessageXml(oversized));
    }

    @Test
    @DisplayName("非法 XML 被拒绝")
    void invalidXmlRejected() {
        WeComXmlParser parser = new WeComXmlParser();
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseTextMessageXml("not xml at all"));
    }

    @Test
    @DisplayName("构造回复 XML 格式正确")
    void buildSuccessXml() {
        WeComXmlParser parser = new WeComXmlParser();
        String xml = parser.buildSuccessXml("encrypted", "signature", "123", "nonce");

        assertTrue(xml.contains("encrypted"));
        assertTrue(xml.contains("signature"));
        assertTrue(xml.contains("123"));
        assertTrue(xml.contains("nonce"));
        assertTrue(xml.contains("<Encrypt>"));
        assertTrue(xml.contains("<MsgSignature>"));
    }

    // ==================== 辅助方法 ====================

    /** 加密再解密，验证内容和 receiveId 一致。 */
    private static void assertRoundTrip(WeComCrypto crypto, String content) {
        String encrypted = crypto.encryptContent(content, CORP_ID);
        DecryptResult result = crypto.decryptAndParse(encrypted);
        assertEquals(content, result.content());
        assertEquals(CORP_ID, result.receiveId());
    }
}
