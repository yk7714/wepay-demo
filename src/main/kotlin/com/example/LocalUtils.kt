package com.example

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.wxpay.sdk.WXPay
import com.github.wxpay.sdk.WXPayConfig
import com.github.wxpay.sdk.WXPayConstants
import com.github.wxpay.sdk.WXPayUtil
import lombok.SneakyThrows
import org.apache.commons.codec.binary.Base64
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.enterprise.context.ApplicationScoped
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity
import javax.ws.rs.core.MediaType


class LocalWxPayUtils{

    val executorService: ExecutorService = Executors.newCachedThreadPool()
    val client = ClientBuilder.newBuilder().executorService(executorService).build()

    val config = MyConfig()
    val wxpay = WXPay(config, WXPayConstants.SignType.HMACSHA256)



    //todo: get jsapi_ticket
    fun getJsApiTicket(access_token: String){
        val endpoint = "https://api.weixin.qq.com/cgi-bin/ticket/getticket?access_token=$access_token&type=jsapi"
        val res = client.target(endpoint)
            .request()
            .get()
        val entity = res.readEntity(ObjectNode::class.java)
        Session.cache.put("ticket", entity.get("ticket").toString())
    }

    //get openid and remote access_token
    fun getOpenIdRemote(code: String): String{
        val endpoint = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=${Session.appId}&secret=${Session.appSecret}&code=$code&grant_type=authorization_code"
        val res = client.target(endpoint)
            .request()
            .get()
        val entity = res.readEntity(ObjectNode::class.java)
        Session.cache.put("access_token", entity.get("access_token").toString())
        Session.cache.put("refresh_token", entity.get("refresh_token").toString())
        return entity.get("openid").toString()

    }

    //refresh access token
    fun refreshAccessToken(){
        val refresh_token = Session.cache.get("refresh_token")
        if(!refresh_token.isNullOrEmpty()){
            val endpoint = "https://api.weixin.qq.com/sns/oauth2/refresh_token?appid=${Session.appId}&grant_type=refresh_token&refresh_token=$refresh_token"
            val res = client.target(endpoint)
                .request()
                .get()
            val entity = res.readEntity(ObjectNode::class.java)
            if(res.status!=200){
                logger.info("refresh_token is out date")
            }else{
                Session.cache.put("openid", entity.get("openid").toString())
                Session.cache.put("access_token", entity.get("access_token").toString())
            }
        }
    }

    /**
     * 添加分账接受方
     */
    fun addReceiver(addReceiverRequest: AddReceiverRequest, sign: String?): String{
        val endpoint = "https://api.mch.weixin.qq.com/v3/profitsharing/receivers/add"
        val rres = client.target(endpoint)
            .request()
            .header("Accept", "application/json")
            .header("Authorization", "WECHATPAY2-SHA256-RSA2048 $sign")
            .post(Entity.entity(addReceiverRequest, MediaType.APPLICATION_JSON))
        val status = rres.status
        val res = rres.readEntity(JsonNode::class.java).toString()
        logger.info("add profitSharing receiver：$res")
        println(res)
        return status.toString()
    }

    /**
     * 直联商户分账
     */
    fun shangHuProfitSharing(request: FenZhangRequest){
        val receiver = request.receivers[0]
        val map = mutableMapOf("mch_id" to request.mch_id, "appid" to request.appid, "nonce_str" to UUID.randomUUID().toString().substring(0,31), "transaction_id" to request.transaction_id,
            "sign_type" to "HMAC-SHA256", "out_order_no" to request.out_order_no, "receivers" to "[{\"type\":\"${receiver.type}\",\"account\":\"${receiver.account}\",\"amount\":${receiver.amount},\"description\":\"${receiver.description}\"}]")
        val sign = WXPayUtil.generateSignature(map, config.getKey(), WXPayConstants.SignType.HMACSHA256)
        map.put("sign", sign)
        val res = wxpay.requestWithCert(shangHuProfitSharingEndpoint, map, config.httpConnectTimeoutMs, config.httpReadTimeoutMs)
        logger.info("分账： $res")
        print(res)
    }

    /**
     * 服务商分账
     */
    fun fuWuShangProfitSharing(request: FuWuShangFenZhangRequest, sign: String?): String{
        val rres = client.target(fuWuShangProfitSharingEndpoint)
            .request()
            .header("Accept", "application/json")
            .header("Authorization", "WECHATPAY2-SHA256-RSA2048 $sign")
            .post(Entity.entity(request, MediaType.APPLICATION_JSON))
        val status = rres.status
        val res = rres.readEntity(JsonNode::class.java).toString()
        println(res)
        logger.info("profitSharing：$res")
        return status.toString()
    }

    companion object{
        val shangHuProfitSharingEndpoint = "https://api.mch.weixin.qq.com/secapi/pay/profitsharing"
        val fuWuShangProfitSharingEndpoint = "https://api.mch.weixin.qq.com/v3/profitsharing/orders"
        val logger = LoggerFactory.getLogger(LocalWxPayUtils::class.java)
    }
}



/**
 * 利用API证书进行加解密
 */
internal class KeyPairFactory {

    /**
     * 获取公私钥
     */
    fun createPKCS12(keyPath: String?, keyAlias: String?, keyPass: String): KeyPair {
        val file = File(keyPath)
        val certStream: InputStream = FileInputStream(file)
        val pem = keyPass.toCharArray()
        return try {
            val store = KeyStore.getInstance("PKCS12")
            store.load(certStream, pem)
            val certificate: X509Certificate = store!!.getCertificate(keyAlias) as X509Certificate
            certificate.checkValidity()
            // 证书的序列号
            val serialNumber: String = certificate.getSerialNumber().toString(16).toUpperCase()
            println(serialNumber)
            // 证书的公钥
            val publicKey: PublicKey = certificate.getPublicKey()
            // 证书的私钥
            val privateKey = store!!.getKey(keyAlias, pem) as PrivateKey
            KeyPair(publicKey, privateKey)
        } catch (e: Exception) {
            throw IllegalStateException("Cannot load keys from store", e)
        }
    }

    @SneakyThrows
    fun sign(
        method: String?,
        canonicalUrl: String?,
        timestamp: String,
        nonceStr: String?,
        body: String?,
        keyPair: KeyPair
    ): String? {
        val signatureStr: String = Stream.of(method, canonicalUrl, timestamp, nonceStr, body)
            .collect(Collectors.joining("\n", "", "\n"))
        println(signatureStr)
        val sign = Signature.getInstance("SHA256withRSA")
        sign.initSign(keyPair.private)
        sign.update(signatureStr.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeBase64String(sign.sign())
    }

    /**
     * 生成token
     */
    fun token(mchId: String?, nonceStr: String?, timestamp: String, serialNo: String?, signature: String?): String? {
        val TOKEN_PATTERN = "mchid=\"%s\",nonce_str=\"%s\",timestamp=\"%s\",serial_no=\"%s\",signature=\"%s\""
        // 生成token
        return java.lang.String.format(
            TOKEN_PATTERN,
            mchId,
            nonceStr, timestamp, serialNo, signature
        )

    }

    fun getToken(KeyPath: String, mchId: String, method: String, url: String, serialNo: String, body: String?): String?{
        val keyPair = createPKCS12(KeyPath, "Tenpay Certificate", mchId)

        val timestamp = Sign.create_timestamp()
        val nonceStr = Sign.create_nonce_str()

        val signature = sign(method, url, timestamp, nonceStr, body, keyPair = keyPair)

        return token(mchId, nonceStr, timestamp, serialNo, signature)
    }
}



class MyConfig : WXPayConfig {

    private val certData: ByteArray

    override fun getAppID(): String {
        return Session.appId
    }

    override fun getMchID(): String {
        return Session.mch_id
    }

    override fun getKey(): String {
        return Session.paternerKey
    }

    override fun getCertStream(): InputStream {
        return ByteArrayInputStream(certData)
    }

    override fun getHttpConnectTimeoutMs(): Int {
        return 8000
    }

    override fun getHttpReadTimeoutMs(): Int {
        return 10000
    }

    init {
        //todo: download cert
        val certPath = "/Users/lumeng/Desktop/wepay-demo/src/main/resources/path/apiclient_cert.p12"
        val file = File(certPath)
        val certStream: InputStream = FileInputStream(file)
        certData = ByteArray(file.length().toInt())
        certStream.read(certData)
        certStream.close()
    }
}


@ApplicationScoped
object Session{

    lateinit var mch_id: String

    lateinit var appId: String

    lateinit var paternerKey: String

    lateinit var appSecret: String

    lateinit var serialNo: String

    fun setter(mch_id: String, appId: String, paternerKey: String, appSecret: String, serialNo: String){
        this.mch_id = mch_id
        this.appId = appId;
        this.paternerKey = paternerKey
        this.appSecret = appSecret
        this.serialNo = serialNo
    }
    val cache = HashMap<String, String>()
    val timer = Timer()

}

