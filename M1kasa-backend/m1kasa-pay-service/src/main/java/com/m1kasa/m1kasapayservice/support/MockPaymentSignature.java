package com.m1kasa.m1kasapayservice.support;

import com.m1kasa.m1kasapayservice.service.model.PaymentSuccessResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
@Profile("local")
public class MockPaymentSignature {

    private final byte[] secret;

    public MockPaymentSignature(
            @Value("${m1kasa.pay.mock.callback-secret:}")
            String secret
    ) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "模拟支付回调密钥至少需要32个字符"
            );
        }

        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    // 发送端使用：根据成功结果生成签名
    public String sign(PaymentSuccessResult result) {
        return HexFormat.of().formatHex(calculate(result));
    }

    // 接收端使用：检查收到的签名
    public boolean verify(
            PaymentSuccessResult result,
            String signature
    ) {
        if (signature == null || signature.length() != 64) {
            return false;
        }

        byte[] received;

        try {
            received = HexFormat.of().parseHex(signature);
        } catch (IllegalArgumentException ex) {
            return false;
        }

        byte[] expected = calculate(result);

        return MessageDigest.isEqual(expected, received);
    }

    private byte[] calculate(PaymentSuccessResult result) {
        if (result == null
                || result.payNo() == null
                || !result.payNo().matches("[0-9a-f]{32}")
                || !"MOCK".equals(result.channel())
                || result.channelTradeNo() == null
                || !result.channelTradeNo().matches("[0-9a-f]{32}")
                || result.payAmountFen() == null
                || result.payAmountFen() <= 0
                || result.paidTime() == null) {
            throw new IllegalArgumentException(
                    "模拟支付回调数据不合法"
            );
        }

        // 固定字段顺序，保证发送端和接收端计算方式一致
        String content = String.join(
                "\n",
                result.payNo(),
                result.channel(),
                result.channelTradeNo(),
                result.payAmountFen().toString(),
                result.paidTime().toString()
        );

        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            mac.init(new SecretKeySpec(
                    secret,
                    "HmacSHA256"
            ));

            return mac.doFinal(
                    content.getBytes(StandardCharsets.UTF_8)
            );
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(
                    "计算模拟支付签名失败",
                    ex
            );
        }
    }
}
