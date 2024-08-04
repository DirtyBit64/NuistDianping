package com.hmdp.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "sentinel")
class SentinelProperties {

    private boolean enable = false;

    // 接口限流规则
    private List<FlowRule> flowRules;

    // 热点参数限流规则
    private List<ParamFlowRule> hotFlowRules;

    // 需要推送变更的key
    private Set<String> configKey = new HashSet<>();

    @PostConstruct
    void init(){
        configKey.add("sentinel.flowRules");
        configKey.add("sentinel.hotRules");
    }

}