package com.caritasem.ruleuler.dto;

import lombok.Data;

import java.util.Map;

@Data
public class RespDTO {

    private Integer status;

    private String msg;

    private Object data;

    private Map<String, Object> meta;  // 系统元数据，与用户规则输出分组

    /** 兼容旧客户端（如 risk-alpha）：以 code 解析状态码，与 status 同值，只读 */
    public Integer getCode() {
        return this.status;
    }

    public RespDTO(Object data) {
        this.status = 0;
        this.msg = "";
        this.data = data;
    }

    public RespDTO(Integer status, String msg) {
        this.status = status;
        this.msg = msg;
        this.data = null;
    }

    public RespDTO(Integer status, String msg, Object data) {
        this.status = status;
        this.msg = msg;
        this.data = data;
    }
}
