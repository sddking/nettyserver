package com.firmiana.domain.nettyserver.enums;

public enum RouteEnum {
    SEND_ALL_MESSAGE("/sendAll","发送给全部的socket连接");

    private String path;

    private String remark;

    RouteEnum(String path, String remark){
        this.path = path;
        this.remark = remark;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
