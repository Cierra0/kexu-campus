package com.kexu.campus.dns;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 最小 DNS 报文处理：解析查询域名，构造应答。
 *
 * 拦截策略选择「返回 0.0.0.0 (A) / :: (AAAA)」而非 NXDOMAIN：
 * 广告 SDK 对 NXDOMAIN 常触发重试或走降级逻辑，0.0.0.0 让它直接连接失败、更干脆；
 * 同时对业务域名影响最小（我们只对命中规则的域名这么回）。
 *
 * 移植自开源项目 TangLi（MIT License）。
 */
final class DnsPacket {

    private static final int TYPE_A = 1;
    private static final int TYPE_AAAA = 28;

    private DnsPacket() {}

    /** 提取查询域名；解析失败返回 null */
    static String parseQueryName(byte[] buf, int len) {
        if (len < 12) return null;
        int qdcount = ((buf[4] & 0xFF) << 8) | (buf[5] & 0xFF);
        if (qdcount < 1) return null;

        StringBuilder sb = new StringBuilder(64);
        int pos = 12;
        int safety = 0;
        while (pos < len) {
            int labelLen = buf[pos] & 0xFF;
            if (labelLen == 0) {
                pos++;
                break;
            }
            if ((labelLen & 0xC0) == 0xC0) return null;
            pos++;
            if (pos + labelLen > len) return null;
            if (sb.length() > 0) sb.append('.');
            for (int i = 0; i < labelLen; i++) {
                sb.append((char) (buf[pos + i] & 0xFF));
            }
            pos += labelLen;
            if (++safety > 128) return null;
        }
        String name = sb.toString().toLowerCase();
        return name.isEmpty() ? null : name;
    }

    /** 提取问题段的 qtype；失败返回 -1 */
    static int parseQueryType(byte[] buf, int len) {
        int pos = skipQuestionName(buf, len);
        if (pos < 0 || pos + 2 > len) return -1;
        return ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF);
    }

    private static int skipQuestionName(byte[] buf, int len) {
        int pos = 12;
        while (pos < len) {
            int l = buf[pos] & 0xFF;
            if (l == 0) return pos + 1;
            if ((l & 0xC0) == 0xC0) return pos + 2;
            pos += 1 + l;
        }
        return -1;
    }

    /** 构造「拦截」应答：A 查返回 0.0.0.0，AAAA 返回 ::，其他类型返回空 NOERROR。 */
    static byte[] buildBlockedResponse(byte[] query, int queryLen, int qtype) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(128);

        out.write(query[0]);
        out.write(query[1]);
        out.write(0x81);
        out.write(0x80);
        out.write(0x00); out.write(0x01);
        int answerCount = (qtype == TYPE_A || qtype == TYPE_AAAA) ? 1 : 0;
        out.write(0x00); out.write(answerCount);
        out.write(0x00); out.write(0x00);
        out.write(0x00); out.write(0x00);

        int qEnd = skipQuestionName(query, queryLen);
        if (qEnd < 0 || qEnd + 4 > queryLen) return null;
        int qEndFull = qEnd + 4;
        out.write(query, 12, qEndFull - 12);

        if (answerCount == 0) return out.toByteArray();

        out.write(0xC0); out.write(0x0C);
        out.write(0x00); out.write((byte) qtype);
        out.write(0x00); out.write(0x01);
        out.write(0x00); out.write(0x00); out.write(0x00); out.write(0x3C);

        if (qtype == TYPE_A) {
            out.write(0x00); out.write(0x04);
            out.write(0x00); out.write(0x00); out.write(0x00); out.write(0x00);
        } else {
            out.write(0x00); out.write(0x10);
            for (int i = 0; i < 16; i++) out.write(0x00);
        }
        return out.toByteArray();
    }

    /** 构造 SERVFAIL，用于上游不可达时避免客户端长等 */
    static byte[] buildServFail(byte[] query, int queryLen) {
        int qEnd = skipQuestionName(query, queryLen);
        int end = (qEnd < 0) ? 12 : Math.min(queryLen, qEnd + 4);
        byte[] resp = new byte[end];
        System.arraycopy(query, 0, resp, 0, end);
        resp[2] = (byte) 0x81;
        resp[3] = (byte) 0x82;
        resp[6] = 0x00; resp[7] = 0x00;
        resp[8] = 0x00; resp[9] = 0x00;
        resp[10] = 0x00; resp[11] = 0x00;
        return resp;
    }
}
