package com.caritasem.ruleuler.server.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission {
    private Long id;
    private String permissionCode;
    private String name;
    private String type;
    private Long parentId;
    private Integer sortOrder;
}
