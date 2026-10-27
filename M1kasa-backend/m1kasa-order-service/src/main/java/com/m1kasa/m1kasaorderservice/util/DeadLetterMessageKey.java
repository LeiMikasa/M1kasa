package com.m1kasa.m1kasaorderservice.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class DeadLetterMessageKey {

    private DeadLetterMessageKey() {
    }

    public static String calculate(
            String sourceQueue,
            String messageId,
            byte[] body
    ) {
        if (sourceQueue == null || sourceQueue.isBlank()) {
            throw new IllegalArgumentException("来源队列不能为空");
        }

        if (body == null) {
            throw new IllegalArgumentException("消息体不能为 null");
        }

        try {
            //创建一个SHA-256计算器
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            // 第一部分：原业务队列
            updatePart(
                    digest,
                    sourceQueue.getBytes(StandardCharsets.UTF_8)
            );

            // 第二部分：消息 ID，允许原消息没有设置 ID
            updatePart(
                    digest,
                    messageId == null
                            ? new byte[0]
                            : messageId.getBytes(StandardCharsets.UTF_8)
            );

            // 第三部分：原始消息字节
            updatePart(digest, body);

            // 将摘要转换为 64 个十六进制字符
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "当前环境不支持 SHA-256",
                    ex
            );
        }
    }

    private static void updatePart(
            MessageDigest digest,
            byte[] bytes
    ) {
        // 先记录这一部分的字节长度，再加入实际内容
        digest.update(
                ByteBuffer.allocate(Integer.BYTES)
                        .putInt(bytes.length)
                        .array()
        );

        digest.update(bytes);
    }
}
