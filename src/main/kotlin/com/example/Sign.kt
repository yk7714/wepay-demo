package com.example

import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*

/**
 * 用于jsapi_ticket签名
 */
internal object Sign {
    fun sign(jsapi_ticket: String, url: String): Map<String, String> {
        val ret: MutableMap<String, String> = HashMap()
        val nonce_str = create_nonce_str()
        val timestamp = create_timestamp()
        val string1: String
        var signature = ""

        //注意这里参数名必须全部小写，且必须有序
        string1 = "jsapi_ticket=" + jsapi_ticket +
                "&noncestr=" + nonce_str +
                "&timestamp=" + timestamp +
                "&url=" + url
        println(string1)
        try {
            val crypt = MessageDigest.getInstance("SHA-1")
            crypt.reset()
            crypt.update(string1.toByteArray(charset("UTF-8")))
            signature = byteToHex(crypt.digest())
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        }
        ret["url"] = url
        ret["jsapi_ticket"] = jsapi_ticket
        ret["nonceStr"] = nonce_str
        ret["timestamp"] = timestamp
        ret["signature"] = signature
        ret["appId"] = Session.appId
        return ret
    }

    //hex
    private fun byteToHex(hash: ByteArray): String {
        val formatter = Formatter()
        for (b in hash) {
            formatter.format("%02x", b)
        }
        val result = formatter.toString()
        formatter.close()
        return result
    }

    //create nonce str
    fun create_nonce_str(): String {
        return UUID.randomUUID().toString()
    }

    //create timestamp
    fun create_timestamp(): String {
        return java.lang.Long.toString(System.currentTimeMillis() / 1000)
    }
}
