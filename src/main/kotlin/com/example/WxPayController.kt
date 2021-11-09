package com.example

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.github.wxpay.sdk.WXPay
import com.github.wxpay.sdk.WXPayConstants
import com.github.wxpay.sdk.WXPayUtil
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import kotlin.collections.HashMap
import kotlin.concurrent.timerTask

data class FenZhangRequest(
    val mch_id: String,
    val appid: String,
    val transaction_id: String,
    val out_order_no: String,
    val receivers: List<Receiver>
)

data class FuWuShangFenZhangRequest(
    val mch_id: String,
    val appid: String,
    val nonce_str: String,
    val transaction_id: String,
    val out_order_no: String,
    val receivers: List<Receiver>
)

data class Receiver(
    val type: ReceiverType,
    val account: String,
    val amount: Int,
    val description: String? = "split to $account"
)

data class AddReceiverRequest(
    val sub_mchid: String,
    val appid: String,
    val type: String,
    val account: String,
    val relation_type: String
)

enum class ReceiverType{
    MERCHANT_ID, PERSONAL_OPENID, PERSONAL_SUB_OPENID
}

@Path("/WxPay")
class WxPayController{

    val utils = LocalWxPayUtils()

    @ConfigProperty(name = "appId")
    lateinit var appId: String

    @ConfigProperty(name = "mch_id")
    lateinit var mch_id: String

    @ConfigProperty(name = "paternerKey")
    lateinit var paternerKey: String

    @ConfigProperty(name = "appSecret")
    lateinit var appSecret: String

    @ConfigProperty(name = "serialNo")
    lateinit var serialNo: String

    @GET
    @Path("/getOpenId")
    fun getOpenId(@QueryParam("code") code: String): Map<String, String> {
        Session.setter(mch_id, appId, paternerKey, appSecret, serialNo)
        if(Session.cache.get("openid").isNullOrEmpty()){
            utils.getOpenIdRemote(code)
        }
        val openid = Session.cache.get("openid").toString()
        val map = mapOf("openid" to openid)
        Session.timer.scheduleAtFixedRate(
            timerTask {
                utils.refreshAccessToken()
            }, 0, 6900000
        )
        return map
    }

    //get pay config
    @GET
    @Path("/GetConfig")
    @Produces(MediaType.APPLICATION_JSON)
    fun GetPayConfig(
        @QueryParam("url") url: String
    ): String {
        val mapper = ObjectMapper()
        lateinit var jsapi_ticket: String
        if(Session.cache.get("ticket").isNullOrEmpty()){
            utils.getJsApiTicket(Session.cache.get("access_token")!!)
        }
//        val jsapi_ticket = "LIKLckvwlJT9cWIhEQTwfDvK5RXd_GUYN8YIKxB6WceqZbpaS_Kiy5q3dkE5gkRhysoq1WhtHQk8Ktf0EN0dtQ"
        jsapi_ticket = Session.cache.get("ticket")!!
        val map = Sign.sign(jsapi_ticket, url)
        return mapper.writeValueAsString(map)
    }

    @POST
    @Path("/GetPaySign")
    @Produces(MediaType.APPLICATION_JSON)
    fun GetPaySign(body: String): String{
        Session.setter(mch_id, appId, paternerKey, appSecret, serialNo)
        val config = MyConfig()
        val wxpay = WXPay(config, WXPayConstants.SignType.MD5)
        val data: MutableMap<String, String> = HashMap()
        val jsonBody = mapper.readTree(body)
        data["body"] = "buy some products"
        data["out_trade_no"] = jsonBody["id"].toString()
        data["fee_type"] = "CNY"
        data["total_fee"] = jsonBody["fee"].toString()
        data["spbill_create_ip"] = "123.12.12.123"
        data["notify_url"] = "http://xxx/WxPay/notify"
        data["trade_type"] = "JSAPI" // 此处指定为扫码支付
        data["profit_sharing"] = "Y"
        //openid 需要通过jssdk获取（由前端传来）
        data["openid"] = jsonBody["openid"].toString()
        val resp: Map<String, String>
        try {
            //统一下单接口
            resp = wxpay.unifiedOrder(data)
            resp.put("timeStamp", Sign.create_timestamp())
            val res = mutableMapOf<String, String>("appId" to resp.get("appid").toString(), "timeStamp" to resp.get("timeStamp").toString(),
                "nonceStr" to resp.get("nonce_str").toString(), "package" to "prepay_id=${resp.get("prepay_id").toString()}", "signType" to "MD5")
            val sign = WXPayUtil.generateSignature(res, config.getKey(), WXPayConstants.SignType.MD5)
            res.put("paySign", sign)
            logger.info("res: " + res.toString())
            return mapper.writeValueAsString(res)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    @POST
    @Path("/notify")
    @Produces(MediaType.APPLICATION_JSON)
    fun notify(body: String){
        val objJson = xmlMapper.readTree(body)
        logger.info("body: " + body)
        logger.info(objJson.get("return_code").asText())
        if(objJson.get("return_code").asText()=="SUCCESS"){
            val mch_id = objJson["mch_id"].asText()
            val appId = objJson["appid"].asText()
            val transaction_id = objJson["transaction_id"].asText()
            val out_trade_no = objJson["out_trade_no"].asText()
            //todo: account and amount need to be filled in dynamically
            val receivers = listOf(Receiver(ReceiverType.PERSONAL_OPENID, "", 2))
            val fenZhangRequest = FenZhangRequest(mch_id, appId, transaction_id, out_trade_no, receivers)
            utils.shangHuProfitSharing(fenZhangRequest)
        }
    }

    companion object{
        val mapper = ObjectMapper()
        val xmlMapper = XmlMapper()
        val logger = LoggerFactory.getLogger(WxPayController::class.java)
    }

}

