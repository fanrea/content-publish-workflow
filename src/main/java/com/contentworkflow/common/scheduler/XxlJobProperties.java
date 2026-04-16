package com.contentworkflow.common.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置属性载体，用于绑定外部配置项并向运行时组件提供参数。
 */
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobProperties {

    private final Admin admin = new Admin();
    private final Executor executor = new Executor();

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public Admin getAdmin() {
        return admin;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public Executor getExecutor() {
        return executor;
    }

    /**
     * 当前模块中的核心类型，用于承载对应场景下的业务数据或处理能力。
     */

    public static class Admin {
        private String addresses;
        private String accessToken;
        private int timeout = 3;

        /**
         * 根据输入条件获取对应的业务数据详情。
         *
         * @return 方法处理后的结果对象
         */

        public String getAddresses() {
            return addresses;
        }

        /**
         * 处理 set addresses 相关逻辑，并返回对应的执行结果。
         *
         * @param addresses 参数 addresses 对应的业务输入值
         */

        public void setAddresses(String addresses) {
            this.addresses = addresses;
        }

        /**
         * 根据输入条件获取对应的业务数据详情。
         *
         * @return 方法处理后的结果对象
         */

        public String getAccessToken() {
            return accessToken;
        }

        /**
         * 处理 set access token 相关逻辑，并返回对应的执行结果。
         *
         * @param accessToken 参数 accessToken 对应的业务输入值
         */

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        /**
         * 根据输入条件获取对应的业务数据详情。
         *
         * @return 统计值或数量结果
         */

        public int getTimeout() {
            return timeout;
        }

        /**
         * 处理 set timeout 相关逻辑，并返回对应的执行结果。
         *
         * @param timeout 参数 timeout 对应的业务输入值
         */

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }
    }

    /**
     * 当前模块中的核心类型，用于承载对应场景下的业务数据或处理能力。
     */

    public static class Executor {
        private boolean enabled = false;
        private String appname = "content-publish-workflow";
        private String address;
        private String ip;
        private int port = 9999;
        private String logpath = "./logs/xxl-job/jobhandler";
        private int logretentiondays = 30;

        /**
         * 判断当前对象是否处于特定状态。
         *
         * @return 返回 true 表示条件成立或处理成功，返回 false 表示条件不成立或未命中
         */

        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 处理 set enabled 相关逻辑，并返回对应的执行结果。
         *
         * @param enabled 参数 enabled 对应的业务输入值
         */

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 根据输入条件获取对应的业务数据详情。
         *
         * @return 方法处理后的结果对象
         */

        public String getAppname() {
            return appname;
        }

        /**
         * 处理 set appname 相关逻辑，并返回对应的执行结果。
         *
         * @param appname 参数 appname 对应的业务输入值
         */

        public void setAppname(String appname) {
            this.appname = appname;
        }

        /**
         * 根据输入条件获取对应的业务数据详情。
         *
         * @return 方法处理后的结果对象
         */

        public String getAddress() {
            return address;
        }

        /**
         * 处理 set address 相关逻辑，并返回对应的执行结果。
         *
         * @param address 参数 address 对应的业务输入值
         */

        public void setAddress(String address) {
            this.address = address;
        }

        /**
         * 根据输入条件获取对应的业务数据详情。
         *
         * @return 方法处理后的结果对象
         */

        public String getIp() {
            return ip;
        }

        /**
         * 处理 set ip 相关逻辑，并返回对应的执行结果。
         *
         * @param ip 参数 ip 对应的业务输入值
         */

        public void setIp(String ip) {
            this.ip = ip;
        }

        /**
         * 根据输入条件获取对应的业务数据详情。
         *
         * @return 统计值或数量结果
         */

        public int getPort() {
            return port;
        }

        /**
         * 处理 set port 相关逻辑，并返回对应的执行结果。
         *
         * @param port 参数 port 对应的业务输入值
         */

        public void setPort(int port) {
            this.port = port;
        }

        /**
         * 根据输入条件获取对应的业务数据详情。
         *
         * @return 方法处理后的结果对象
         */

        public String getLogpath() {
            return logpath;
        }

        /**
         * 处理 set logpath 相关逻辑，并返回对应的执行结果。
         *
         * @param logpath 参数 logpath 对应的业务输入值
         */

        public void setLogpath(String logpath) {
            this.logpath = logpath;
        }

        /**
         * 根据输入条件获取对应的业务数据详情。
         *
         * @return 统计值或数量结果
         */

        public int getLogretentiondays() {
            return logretentiondays;
        }

        /**
         * 处理 set logretentiondays 相关逻辑，并返回对应的执行结果。
         *
         * @param logretentiondays 参数 logretentiondays 对应的业务输入值
         */

        public void setLogretentiondays(int logretentiondays) {
            this.logretentiondays = logretentiondays;
        }
    }
}
