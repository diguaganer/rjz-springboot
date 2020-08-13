package cc.niushuai.rjz.user.entity;

import lombok.Data;

/**
 * @author ns
 * @date 2020/8/13
 */
@Data
public class Code2Session {
    private String openid;    //	用户唯一标识
    private String session_key;    //	会话密钥
    private String unionid;    //	用户在开放平台的唯一标识符，在满足 UnionID 下发条件的情况下会返回，详见 UnionID 机制说明。
    private int errcode;    //错误码
    private String errmsg;    //错误信息

    public boolean isSuccess() {
        return errcode == 0;
    }

}
