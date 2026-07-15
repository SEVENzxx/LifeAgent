package com.lifeagent.config;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;

/**
 * 企业微信回调安全 XML 解析。
 *
 * <p>禁用 DTD、外部实体、XInclude 和外部 Schema，禁止 XXE。
 * 只提取受限白名单字段。</p>
 */
@Slf4j
public class WeComXmlParser {

    private static final int MAX_XML_BODY_SIZE = 64 * 1024; // 64 KiB

    private final DocumentBuilder documentBuilder;

    public WeComXmlParser() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 禁用 DTD
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            // 禁用外部实体
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            // 禁用 XInclude
            factory.setXIncludeAware(false);
            // 禁用外部 Schema
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setExpandEntityReferences(false);

            // 不加载 DTD
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            // 不保留注释和空白
            factory.setIgnoringComments(true);
            factory.setIgnoringElementContentWhitespace(true);

            this.documentBuilder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("XML 解析器初始化失败", e);
        }
    }

    /**
     * 解析回调外层 XML。
     */
    public CallbackXml parseCallbackXml(String xml) {
        if (xml == null || xml.length() > MAX_XML_BODY_SIZE) {
            throw new IllegalArgumentException("请求体大小超过限制");
        }
        try {
            Document doc = documentBuilder.parse(new InputSource(new StringReader(xml)));
            Element root = doc.getDocumentElement();
            String toUserName = getTextContent(root, "ToUserName");
            String agentId = getTextContent(root, "AgentID");
            String encrypt = getTextContent(root, "Encrypt");
            if (encrypt == null || encrypt.isEmpty()) {
                throw new IllegalArgumentException("缺少 Encrypt 字段");
            }
            return new CallbackXml(toUserName, agentId, encrypt);
        } catch (SAXException e) {
            throw new IllegalArgumentException("XML 格式非法", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("XML 读取失败", e);
        }
    }

    /**
     * 解析解密后的文本消息 XML。
     */
    public TextMessageXml parseTextMessageXml(String xml) {
        if (xml == null || xml.length() > MAX_XML_BODY_SIZE) {
            throw new IllegalArgumentException("XML 大小超过限制");
        }
        try {
            Document doc = documentBuilder.parse(new InputSource(new StringReader(xml)));
            Element root = doc.getDocumentElement();

            String toUserName = getTextContent(root, "ToUserName");
            String fromUserName = getTextContent(root, "FromUserName");
            String createTimeStr = getTextContent(root, "CreateTime");
            String msgType = getTextContent(root, "MsgType");
            String content = getTextContent(root, "Content");
            String msgId = getTextContent(root, "MsgId");
            String agentId = getTextContent(root, "AgentID");

            return new TextMessageXml(toUserName, fromUserName, createTimeStr,
                    msgType, content, msgId, agentId);
        } catch (SAXException e) {
            throw new IllegalArgumentException("XML 格式非法", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("XML 读取失败", e);
        }
    }

    /**
     * 构造回复 success XML。
     */
    public String buildSuccessXml(String encrypt, String signature,
                                   String timestamp, String nonce) {
        return "<xml>\n"
                + "<Encrypt><![CDATA[" + encrypt + "]]></Encrypt>\n"
                + "<MsgSignature><![CDATA[" + signature + "]]></MsgSignature>\n"
                + "<TimeStamp>" + timestamp + "</TimeStamp>\n"
                + "<Nonce><![CDATA[" + nonce + "]]></Nonce>\n"
                + "</xml>";
    }

    private static String getTextContent(Element parent, String tagName) {
        var nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent();
    }

    /**
     * 回调外层 XML 解析结果。
     */
    public record CallbackXml(String toUserName, String agentId, String encrypt) {
    }

    /**
     * 解密后文本消息 XML 解析结果。
     * 字段与 switch/case 在调用方配合使用，构造方法不校验单个字段。
     */
    public record TextMessageXml(
            String toUserName,
            String fromUserName,
            String createTime,
            String msgType,
            String content,
            String msgId,
            String agentId
    ) {
    }
}
