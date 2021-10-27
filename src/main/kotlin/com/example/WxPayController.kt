package com.example

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.wxpay.sdk.WXPay
import com.github.wxpay.sdk.WXPayConfig
import com.github.wxpay.sdk.WXPayConstants
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.ws.rs.*
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.core.MediaType
import kotlin.concurrent.timerTask


@Path("/WxPay")
class WxPayController{


    //get pay config
    @GET
    @Path("/GetConfig")
    @Produces(MediaType.APPLICATION_JSON)
    fun GetPayConfig(
        @QueryParam("url") url: String
    ): String {
        val mapper = ObjectMapper()
        val jsapi_ticket = "LIKLckvwlJT9cWIhEQTwfDvK5RXd_GUYN8YIKxB6WceqZbpaS_Kiy5q3dkE5gkRhysoq1WhtHQk8Ktf0EN0dtQ"
        val map = Sign.sign(jsapi_ticket, url)
        return mapper.writeValueAsString(map)
    }

    @POST
    @Path("/GetSign")
    fun GetPaySign(): String{
//        val config = MyConfig()
//        val wxPay = WXPay(config, WXPayConstants.SignType.HMACSHA256);
//        val map: MutableMap<String, String> = mutableMapOf("appid" to config.appID, "mch_id" to config.mchID, "nonce_str" to )
//
//
//
//        return ""

        val config = MyConfig()
        val wxpay = WXPay(config, WXPayConstants.SignType.HMACSHA256)

        val data: MutableMap<String, String> = HashMap()
        data["body"] = "腾讯充值中心-QQ会员充值"
        data["out_trade_no"] = "2016090910595900000012"
        data["fee_type"] = "CNY"
        data["total_fee"] = "1"
        data["spbill_create_ip"] = "123.12.12.123"
        data["notify_url"] = "http://www.example.com/wxpay/notify"
        data["trade_type"] = "JSAPI" // 此处指定为扫码支付

        data["openid"] = "o_NIi5jNCHccjjCUJ2Ut3ExfWFUc"

        try {
            val resp = wxpay.unifiedOrder(data)
            logger.info(resp.toString())
            println(resp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    @GET
    @Path("/getOpenId")
    fun getOpenId(@QueryParam("code") code: String): Map<String, String> {
        if(Session.cache.get("openid").isNullOrEmpty()){
            getOpenIdRemote(code)
        }
        val openid = Session.cache.get("openid").toString()
        val map = mapOf("openid" to openid)
        Session.timer.scheduleAtFixedRate(
            timerTask {
                refreshAccessToken()
            }, 0, 6900000
        )
        return map
    }

    //get openid and remote access_token
    fun getOpenIdRemote(code: String){

        val executorService: ExecutorService = Executors.newCachedThreadPool()
        val client = ClientBuilder.newBuilder().executorService(executorService).build()
        val endpoint = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=$AppId&secret=$AppSecret&code=$code&grant_type=authorization_code"
        val res = client.target(endpoint)
            .request()
            .get()
        val entity = res.readEntity(ObjectNode::class.java)
        Session.cache.put("openid", entity.get("openid").toString())
        Session.cache.put("access_token", entity.get("access_token").toString())
        Session.cache.put("refresh_token", entity.get("refresh_token").toString())
    }

    //todo: refresh access_token
    fun refreshAccessToken(){
        val executorService: ExecutorService = Executors.newCachedThreadPool()
        val client = ClientBuilder.newBuilder().executorService(executorService).build()
        val refresh_token = Session.cache.get("refresh_token")
        if(!refresh_token.isNullOrEmpty()){
            val endpoint = "https://api.weixin.qq.com/sns/oauth2/refresh_token?appid=$AppId&grant_type=refresh_token&refresh_token=$refresh_token"
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

    //todo: get jsapi_ticket
    fun getJsApiTicket(access_token: String){
        val executorService: ExecutorService = Executors.newCachedThreadPool()
        val client = ClientBuilder.newBuilder().executorService(executorService).build()
        val endpoint = "https://api.weixin.qq.com/cgi-bin/ticket/getticket?access_token=$access_token&type=jsapi"
        val res = client.target(endpoint)
            .request()
            .get()
        val entity = res.readEntity(ObjectNode::class.java)
        Session.cache.put("ticket", entity.get("ticket").toString())
    }



    companion object{
        val AppId: String = ""
        val AppSecret: String = ""
        val Key: String = ""
        val MchId: String = ""
        val mapper = ObjectMapper()
        val logger = LoggerFactory.getLogger(WxPayController::class.java)

    }

}

class MyConfig : WXPayConfig {

    private val certData: ByteArray

    override fun getAppID(): String {
        return "wx7dfd9085faa115ea"
    }

    override fun getMchID(): String {
        return "1615300707"
    }

    override fun getKey(): String {
        return "dfg8786ghf78hjg09hjkliuytrdfghjl"
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
        val certPath = "D:\\code\\idea\\workspace_1\\wepay-demo\\src\\main\\resources\\META-INF\\resources\\path\\to\\apiclient_cert.p12"
        val file = File(certPath)
        val certStream: InputStream = FileInputStream(file)
        certData = ByteArray(file.length().toInt())
        certStream.read(certData)
        certStream.close()
    }
}