package com.example

import io.quarkus.arc.config.ConfigProperties
import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.enterprise.context.ApplicationScoped
import javax.ws.rs.client.ClientBuilder
import kotlin.collections.HashMap


internal object Sign {


//    val executorService: ExecutorService = Executors.newCachedThreadPool();
//    val client = ClientBuilder.newBuilder().executorService(executorService).build()

//    @ApplicationScoped
//    fun main(args: Array<String>) {
//
////        val jsapi_ticket = Session.cache.get("ticket")
//        val jsapi_ticket = "LIKLckvwlJT9cWIhEQTwfDvK5RXd_GUYN8YIKxB6WceqZbpaS_Kiy5q3dkE5gkRhysoq1WhtHQk8Ktf0EN0dtQ"
//
//        // 注意 URL 一定要动态获取，不能 hardcode
//        val url = "http://example.com"
//        val map = sign(jsapi_ticket!!, url)
//        for ((key, value) in map) {
//            println("$key, $value")
//        }
//
//
//    }


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
        ret["appId"] = "wx7dfd9085faa115ea"
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
    private fun create_nonce_str(): String {
        return UUID.randomUUID().toString()
    }

    //create timestamp
    private fun create_timestamp(): String {
        return java.lang.Long.toString(System.currentTimeMillis() / 1000)
    }
}

@ApplicationScoped
object Session{
    val cache = HashMap<String, String>()
    val timer = Timer()
}

//@ConfigProperties(prefix = "mchInfo")
//class MchInfo{
//
//    private val appId: String = ""
//    private val appSecret: String = ""
//    private val mchId: String = ""
//    private val paternerKey: String = ""
//}
//

