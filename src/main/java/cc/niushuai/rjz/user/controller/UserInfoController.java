package cc.niushuai.rjz.user.controller;

import cc.niushuai.rjz.common.bean.R;
import cc.niushuai.rjz.common.cache.ICacheManager;
import cc.niushuai.rjz.common.enums.ResultStatusEnum;
import cc.niushuai.rjz.common.exception.BizException;
import cc.niushuai.rjz.common.util.KeyConstant;
import cc.niushuai.rjz.common.util.RestTemplateUtil;
import cc.niushuai.rjz.common.util.TokenGenerateUtil;
import cc.niushuai.rjz.common.util.WxUrlConstant;
import cc.niushuai.rjz.user.entity.Code2Session;
import cc.niushuai.rjz.user.entity.UserInfo;
import cc.niushuai.rjz.user.entity.UserToken;
import cc.niushuai.rjz.user.service.UserInfoService;
import cc.niushuai.rjz.user.service.UserTokenService;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author ns
 * @date 2020/8/13
 */
@Slf4j
@RestController
@RequestMapping("/sys/user")
public class UserInfoController {

    @Value("${wechat.appid}")
    private String appid;

    @Value("${wechat.appsecret}")
    private String appsecret;

    @Resource
    private UserInfoService userInfoService;
    @Resource
    private UserTokenService userTokenService;
    @Resource
    private ICacheManager ICacheManager;

    @PostMapping("/login")
    public R login(HttpServletRequest request, @RequestBody UserInfo userInfo) {

        if (StrUtil.isEmpty(userInfo.getCode())) {
            return R.error(ResultStatusEnum.WX_CODE_ID_NULL);
        }

        // 根据code请求微信接口获取openid以及session_key
        Code2Session code2Session = RestTemplateUtil.get(WxUrlConstant.AUTH_CODE_SESSION, Code2Session.class, appid, appsecret, userInfo.getCode());
        log.info("code2Session: {}", code2Session);

        if (code2Session.isSuccess()) {
            userInfo.setOpenId(code2Session.getOpenid());
            userInfo.setSessionKey(code2Session.getSession_key());
        } else {
            throw new BizException(ResultStatusEnum.WX_CODE_SESSION_FAILURE);
        }

        UserInfo loginResult = userInfoService.login(userInfo);
        if (null != loginResult) {
            // 生成token
            String userAgentString = request.getHeader(KeyConstant.USER_AGENT);
            UserAgent userAgent = UserAgentUtil.parse(userAgentString);
            DateTime expireTime = DateUtil.offsetHour(new Date(), 1);
            userInfo.setUserToken(
                    UserToken.builder()
                            .userId(userInfo.getId())
                            .ip(ServletUtil.getClientIP(request))
                            .os(userAgent.getOs().getName())
                            .browser(userAgent.getBrowser().getName())
                            .version(userAgent.getVersion())
                            .token(TokenGenerateUtil.generateToken())
                            .expireTime(expireTime)
                            .expireTimeMills(expireTime.toTimestamp().getTime())
                            .userAgent(userAgentString)
                            .isExpire(0)
                            .createTime(new Date())
                            .build()
            );
            userInfo.setEmail(loginResult.getEmail());
            // 缓存token 到 map 拦截器使用
            Map<String, UserToken> dataByKey = (Map<String, UserToken>) ICacheManager.getCacheDataByKey(KeyConstant.USER_TOKEN);
            if (null == dataByKey) {
                dataByKey = new HashMap<>();
            }
            dataByKey.put(userInfo.getUserToken().getToken(), userInfo.getUserToken());
            ICacheManager.putCache(KeyConstant.USER_TOKEN, dataByKey);

            userTokenService.save(userInfo.getUserToken());
        }

        return R.ok().put("user", userInfo);
    }

    @RequestMapping("/updateUserEmail")
    public R updateUserEmail(@RequestBody UserInfo userInfo) {

        if (!Validator.isEmail(userInfo.getEmail())) {

            // 非邮件地址
            return R.error("请输入正确的邮箱地址");
        }

        boolean update = userInfoService.updateById(userInfo);

        return update ? R.ok() : R.error("啊哦, 更新失败, 请联系作者@qq 1225803134");
    }

    @RequestMapping("/getIp")
    public String getIp(HttpServletRequest req, HttpServletResponse response) {

        return ServletUtil.getClientIP(req);
    }
}
