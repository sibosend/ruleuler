package com.caritasem.ruleuler.dto;

import lombok.Data;

import java.util.Map;

@Data
public class RespDTO {

    private Integer status;

    private String msg;

    private Object data;

    private Map<String, Object> meta;  // 系统元数据，与用户规则输出分组

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
