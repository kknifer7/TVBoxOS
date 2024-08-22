package io.knifer.freebox.constant;

/**
 * 返回码
 *
 * @author Knifer
 */
public final class MessageCodes {

    private MessageCodes() {
        throw new UnsupportedOperationException();
    }

    /**
     * 注册
     */
    public static final int REGISTER = 100;

    /**
     * 获取源列表
     */
    public static final int GET_SOURCE_BEAN_LIST = 201;

    /**
     * 获取源列表结果
     */
    public static final int GET_SOURCE_BEAN_LIST_RESULT = 202;

    /**
     * 获取首页信息
     */
    public static final int GET_HOME_CONTENT = 203;

    /**
     * 获取首页信息结果
     */
    public static final int GET_HOME_CONTENT_RESULT = 204;
}
