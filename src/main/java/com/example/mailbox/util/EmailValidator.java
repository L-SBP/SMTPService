package com.example.mailbox.util;

import java.util.regex.Pattern;

/**
 * 邮件地址格式校验工具类
 */
public class EmailValidator {

    // RFC 5322 兼容的正则表达式（简化版）
    private static final String EMAIL_PATTERN =
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@" +
            "(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";

    private static final Pattern pattern = Pattern.compile(EMAIL_PATTERN);

    /**
     * 校验邮件地址格式是否合法
     * @param email 邮件地址
     * @return true if 格式合法, false otherwise
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        // 基本检查：必须包含@符号
        if (!email.contains("@")) {
            return false;
        }

        // 检查@符号的位置
        int atIndex = email.indexOf('@');
        if (atIndex == 0 || atIndex == email.length() - 1) {
            return false;
        }

        // 检查是否包含多个@符号
        if (email.indexOf('@', atIndex + 1) != -1) {
            return false;
        }

        // 使用正则表达式进行详细校验
        return pattern.matcher(email.trim()).matches();
    }

    /**
     * 校验邮件头部字段格式
     * @param headerValue 头部字段值
     * @return true if 格式合法, false otherwise
     */
    public static boolean isValidHeader(String headerValue) {
        if (headerValue == null || headerValue.trim().isEmpty()) {
            return false;
        }

        // 检查是否包含非法字符
        String invalidChars = "\r\n\t";
        for (char c : invalidChars.toCharArray()) {
            if (headerValue.indexOf(c) != -1) {
                return false;
            }
        }

        return true;
    }

    /**
     * 清理邮件地址，移除多余的空格和特殊字符
     * @param email 原始邮件地址
     * @return 清理后的邮件地址
     */
    public static String sanitizeEmail(String email) {
        if (email == null) {
            return null;
        }

        return email.trim().toLowerCase();
    }
}
