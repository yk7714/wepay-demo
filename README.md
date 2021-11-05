# wePay profitSharing demo

##1、jsSDK实现获取access_token、openId

1. 公众号内点击链接跳转到redirect_uri并携带code
    
   链接形如: https://open.weixin.qq.com/connect/oauth2/authorize?appid=$appid&redirect_uri=$redirect_uri&response_type=code&scope=snsapi_userinfo&state=STATE#wechat_redirect
   
    >注意：该redirect_uri必须在可信域名下；即需要在微信公众后台配置JS接口安全域名
   
2. 传code调用接口：
   
    https://api.weixin.qq.com/sns/oauth2/access_token?appid=${appId}&secret=${appSecret}&code=$code&grant_type=authorization_code
   
    获取openid、access_token、refresh_token

3. access_token定时刷新，可使用refresh_token调用：
   
    https://api.weixin.qq.com/sns/oauth2/refresh_token?appid=${appId}&grant_type=refresh_token&refresh_token=${refresh_token}
   
    刷新access_token


##2、统一下单（jsapi微信支付）
1. 使用jsapi微信支付方式；html文件中须引入如下JS文件：
   
    http://res.wx.qq.com/open/js/jweixin-1.0.0.js

2. 通过config接口进行权限接口验证配置
    ```JavaScript
        wx.config({
            debug: false, // 开启调试模式,成功失败都会有alert框
            appId: data.appId, // 必填，公众号的唯一标识
            timestamp: data.timeStamp, // 必填，生成签名的时间戳
            nonceStr: data.nonceStr, // 必填，生成签名的随机串
            signature: data.signature,// 必填，签名
            jsApiList: ['chooseWXPay'] // 必填，需要使用的JS接口列表
        });
    ```
    
    使用ajax调用我们的后台接口`/WxPay/GetConfig`，来进行签名，然后将传回来的参数赋值给上面的`config`中即可
    >注：wx是微信浏览器特有的元素，和下面的`WeixinJSBridge`类似
    
    > 具体签名方式见`Sign.sign`

3. 统一下单

    html中使用ajax调用我们的后台接口`/WxPay/GetPaySign`，该接口中实现了统一下单功能； 
   
    统一下单借助微信官方提供的sdk，其中封装了微信支付常用的接口调用，以及MD5、HMACSHA256加密；需要引入依赖：
    
    ```shell
        implementation 'com.github.wxpay:wxpay-sdk:0.0.3'
    ```

    统一下单接口需从传单传入：`body、total_fee、openid`；而且后台中需要为其传参`notify_url`作为支付成功的回调url，也是为了后面的分账做准备
   
    统一下单接口返回值将作为微信支付接口的参数，见html中：
    
    ```javascript
        getBrandWCPayRequest = {
                "appId": res.appId,
                "timeStamp": res.timeStamp,
                "nonceStr": res.nonceStr,
                "package": res.package,
                "signType": res.signType,
                "paySign": res.paySign
            }
    ```
   
   下单后将调用支付接口`WeixinJSBridge.invoke`

   > 注意：统一下单接口不需要证书，加密方式为MD5

##3、直联商户分账

   支付成功将回调到统一下单传入的`notify_url`

   我们可以在该url对应的接口上实现分账功能
   
   1. 分账前需要添加分账接收方（如果在微信商户平台手动添加可以不用在code中添加）

      添加分账接收方调用：
   
      https://api.mch.weixin.qq.com/v3/profitsharing/receivers/add
   
      >注意：这是`apiv3`的接口和之前的微信支付中统一下单不是一类，该类接口需要借助API证书，读取证书中的私钥来加密；
      > 在请求头中添加`Authorization`；实现见`KeyPairFactory`
      
   2. 调用分账接口：
      
      https://api.mch.weixin.qq.com/secapi/pay/profitsharing

      可借助微信sdk中`wxpay.requestWithCert`方法来实现
      
      >注意：分账接口需要证书，加密方式为HMACSHA256


>注意：
> 1. `openid`要在frontend存储；`access_token`要在后台存储，其有效期为2h
> 2. 分账接口分为直联商户和服务商，服务商使用的是`apiv3`接口，在`LocalWxPayUtils.fuWuShangProfitSharing`实现
> 3. 统一下单、直联商户分账使用的是`secapi`，它和`apiv3`加密细节不同，见code实现
> 4. 添加分账接收方我实际上使用的是`apiv3`；经过测试，该接口同样可以添加商户平台的分账接收方


##REFERENCE
[jssdk](https://developers.weixin.qq.com/doc/offiaccount/OA_Web_Apps/JS-SDK.html)

[jsapi](https://pay.weixin.qq.com/wiki/doc/api/jsapi.php?chapter=9_1)

[直联商户分账](https://pay.weixin.qq.com/wiki/doc/api/allocation.php?chapter=26_1)

[服务商分账](https://pay.weixin.qq.com/wiki/doc/api/allocation_sl.php?chapter=24_1)