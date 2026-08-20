package com.example.demodrug.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysUser {
    private Long id;
    private String username;

    @JsonIgnore
    private String passwordHash;

    private String displayName;
    private String role;
    private String department;
    private String status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @JsonProperty("enabled")
    public boolean isEnabled() {
        return "ENABLED".equals(status);
    }
}
