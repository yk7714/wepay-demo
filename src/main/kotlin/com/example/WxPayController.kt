package com.example

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.wxpay.sdk.WXPayConfig
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.core.MediaType
import kotlin.concurrent.timerTask


@Path("/WxPay")
class WxPayController{

    val executorService: ExecutorService = Executors.newCachedThreadPool()
    val client = ClientBuilder.newBuilder().executorService(executorService).build()





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
        val endpoint = "https://api.weixin.qq.com/cgi-bin/ticket/getticket?access_token=$access_token&type=jsapi"
        val res = client.target(endpoint)
            .request()
            .get()
        val entity = res.readEntity(ObjectNode::class.java)
        Session.cache.put("ticket", entity.get("ticket").toString())
    }

    //get pay config
    @GET
    @Path("/GetPayConfig")
    @Produces(MediaType.APPLICATION_JSON)
    fun GetPayConfig(
        @QueryParam("url") url: String
    ): String {
        val mapper = ObjectMapper()
        val jsapi_ticket = "LIKLckvwlJT9cWIhEQTwfDvK5RXd_GUYN8YIKxB6WceqZbpaS_Kiy5q3dkE5gkRhysoq1WhtHQk8Ktf0EN0dtQ"
        val map = Sign.sign(jsapi_ticket, url)
        return mapper.writeValueAsString(map)
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
        return "ce24463682d67dcea1ae7b1d3015ab38"
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
        val certPath = "/path/to/apiclient_cert.p12"
        val file = File(certPath)
        val certStream: InputStream = FileInputStream(file)
        certData = ByteArray(file.length().toInt())
        certStream.read(certData)
        certStream.close()
    }
}