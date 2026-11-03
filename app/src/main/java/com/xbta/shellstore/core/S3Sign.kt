package com.xbta.shellstore.core

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

/**
 * AWS Signature V4（只做 S3 需要的部分）。
 *
 * 单独放 core 里，是因为这段逻辑一旦写错，表现为「403 SignatureDoesNotMatch」，
 * 光看 App 日志很难判断是哪一步错的。抽出来就能用 AWS 官方测试向量在桌面上跑通。
 *
 * 只依赖 JVM 自带的 javax.crypto / java.security，不依赖 Android。
 */
object S3Sign {

    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    const val SERVICE = "s3"
    const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v < 16) sb.append('0')
            sb.append(Integer.toHexString(v))
        }
        return sb.toString()
    }

    fun sha256Hex(data: ByteArray): String =
        hex(MessageDigest.getInstance("SHA-256").digest(data))

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    fun hmac(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    /**
     * RFC 3986 编码：只放行 A-Z a-z 0-9 - _ . ~（keepSlash 时额外保留 /）。
     * 注意不能用 URLEncoder —— 它把空格编成 +，S3 会算成不同签名。
     */
    fun encode(input: String, keepSlash: Boolean = false): String {
        val sb = StringBuilder()
        for (ch in input) {
            val ok = ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' ||
                ch == '-' || ch == '_' || ch == '.' || ch == '~' ||
                (keepSlash && ch == '/')
            if (ok) {
                sb.append(ch)
            } else {
                val bytes = ch.toString().toByteArray(Charsets.UTF_8)
                for (b in bytes) {
                    sb.append('%')
                    val v = b.toInt() and 0xFF
                    if (v < 16) sb.append('0')
                    sb.append(Integer.toHexString(v).uppercase())
                }
            }
        }
        return sb.toString()
    }

    /** 规范化 URI：每段单独编码，斜杠保留 */
    fun canonicalUri(path: String): String {
        val p = if (path.startsWith("/")) path else "/$path"
        return encode(p, keepSlash = true)
    }

    /** 规范化查询串：按 key 升序，key 与 value 都编码；空集合返回空串 */
    fun canonicalQuery(params: List<Pair<String, String>>): String =
        params.sortedBy { it.first }
            .joinToString("&") { "${encode(it.first)}=${encode(it.second)}" }

    fun canonicalHeaders(headers: List<Pair<String, String>>): String =
        headers.sortedBy { it.first.lowercase() }
            .joinToString("") { "${it.first.lowercase()}:${it.second.trim()}\n" }

    fun signedHeaders(headers: List<Pair<String, String>>): String =
        headers.map { it.first.lowercase() }.sorted().joinToString(";")

    fun canonicalRequest(
        method: String,
        uri: String,
        query: String,
        headers: List<Pair<String, String>>,
        payloadHash: String
    ): String = buildString {
        append(method.uppercase()).append('\n')
        append(uri).append('\n')
        append(query).append('\n')
        append(canonicalHeaders(headers))
        append('\n')
        append(signedHeaders(headers)).append('\n')
        append(payloadHash)
    }

    fun scope(dateStamp: String, region: String): String =
        "$dateStamp/$region/$SERVICE/aws4_request"

    fun stringToSign(amzDate: String, scope: String, canonicalRequest: String): String =
        "$ALGORITHM\n$amzDate\n$scope\n${sha256Hex(canonicalRequest)}"

    /** kSigning = HMAC(HMAC(HMAC(HMAC("AWS4"+secret, date), region), service), "aws4_request") */
    fun signingKey(secret: String, dateStamp: String, region: String): ByteArray {
        val k1 = hmac(("AWS4$secret").toByteArray(Charsets.UTF_8), dateStamp)
        val k2 = hmac(k1, region)
        val k3 = hmac(k2, SERVICE)
        return hmac(k3, "aws4_request")
    }

    fun signature(secret: String, dateStamp: String, region: String, stringToSign: String): String =
        hex(hmac(signingKey(secret, dateStamp, region), stringToSign))

    /** 完整 Authorization 头 */
    fun authorization(
        accessKey: String,
        secret: String,
        amzDate: String,
        dateStamp: String,
        region: String,
        headers: List<Pair<String, String>>,
        canonicalRequest: String
    ): String {
        val s2s = stringToSign(amzDate, scope(dateStamp, region), canonicalRequest)
        val sig = signature(secret, dateStamp, region, s2s)
        return "$ALGORITHM Credential=$accessKey/${scope(dateStamp, region)}, " +
            "SignedHeaders=${signedHeaders(headers)}, Signature=$sig"
    }

    // ---------- 地址拼装（同样放这里，便于桌面自测） ----------

    /** 拆出协议与主机名；不写协议默认 https */
    fun splitEndpoint(endpoint: String): Pair<String, String> {
        var s = endpoint.trim()
        if (s.isEmpty()) return "https" to ""
        var scheme = "https"
        when {
            s.startsWith("https://", true) -> { scheme = "https"; s = s.substring(8) }
            s.startsWith("http://", true) -> { scheme = "http"; s = s.substring(7) }
        }
        while (s.endsWith("/")) s = s.dropLast(1)
        return scheme to s
    }

    fun host(endpoint: String, pathStyle: Boolean, bucket: String): String {
        val h = splitEndpoint(endpoint).second
        return if (pathStyle) h else "$bucket.$h"
    }

    fun objectPath(pathStyle: Boolean, bucket: String, key: String): String {
        val k = key.trim().trimStart('/')
        return if (pathStyle) "/$bucket/$k" else "/$k"
    }

    fun objectUrl(endpoint: String, pathStyle: Boolean, bucket: String, key: String): String {
        val scheme = splitEndpoint(endpoint).first
        return "$scheme://${host(endpoint, pathStyle, bucket)}${canonicalUri(objectPath(pathStyle, bucket, key))}"
    }
}
