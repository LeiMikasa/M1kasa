package com.m1kasa.common.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "m1kasa.auth")
public class AuthProperties {

    // 默认启用认证
    private boolean enabled = true;

    // 默认拦截所有路径
    private List<String> includePaths =
            new ArrayList<>(List.of("/**"));

    // 默认没有放行路径
    private List<String> excludePaths = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getIncludePaths() {
        return includePaths;
    }

    public void setIncludePaths(List<String> includePaths) {
        this.includePaths = includePaths;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }
}
