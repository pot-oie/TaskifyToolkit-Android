package com.example.taskifyapp.util

import java.util.regex.Pattern

/**
 * 一个用于验证IP地址格式的工具类
 */
object IpValidator {

    // IP地址正则表达式（支持IPv4）
    private val IP_PATTERN: Pattern = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
                "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )

    /**
     * 验证IP地址格式是否为有效的IPv4
     * @param ip 要验证的IP字符串
     * @return 如果格式有效则返回 true，否则返回 false
     */
    fun isValidIp(ip: String): Boolean {
        if (ip.isBlank()) {
            return false
        }
        return IP_PATTERN.matcher(ip).matches()
    }
}