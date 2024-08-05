package com.hmdp.constant;

public class SystemConstants {
    // TODO 部署后记得改
    public static final String IMAGE_UPLOAD_DIR = "D:\\JAVA\\Nginx\\nginx-1.18.0\\html\\hmdp\\imgs\\";
    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;

    // lua脚本
    public static final String SECKILL_LUA = "seckill.lua";
    public static final String UNLOCK_LUA = "unlock.lua";
    // 敏感词库
    public static final String SENSITIVE_FILE_PATH = "sensitive.txt";

    // 限流
    public static final String FLOW_LIMIT_FAIL = "不好意思，当前访问人数过多，请您稍后再试";

    public static final String ROUTE_LOGIN = "该板块需登录后才可查看，请登录";
    public static final String HANDLER_PREFIX = "com.hmdp.handler.";
}
