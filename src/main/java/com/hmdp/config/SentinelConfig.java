package com.hmdp.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.nacos.api.exception.NacosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import javax.annotation.PostConstruct;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@RefreshScope
@Configuration
public class SentinelConfig {

    private final Logger log = LoggerFactory.getLogger(SentinelConfig.class);

    private final SentinelProperties sentinelProperties;

    public SentinelConfig(SentinelProperties sentinelProperties) {
        this.sentinelProperties = sentinelProperties;
    }

    @PostConstruct
    public void init() throws NacosException {
        loadFlowRules();
        loadHotFlowRules();
    }

    /**
     * 添加配置变更监听，热更新限流规则
     * @param event
     */
    @EventListener
    public void handleEvent(ApplicationEvent event) {
        if (event instanceof EnvironmentChangeEvent) {
            EnvironmentChangeEvent environmentChangeEvent = (EnvironmentChangeEvent) event;
            Set<String> keys = environmentChangeEvent.getKeys();
            for (String key : keys) {
                if (sentinelProperties.getConfigKey().contains(key)){
                    if(key.equals("sentinel.flowRules")){
                        loadFlowRules();
                    }
                    else if(key.equals("sentinel.hotRules")){
                        loadHotFlowRules();
                    }
                }
            }
        }
    }

    private void loadFlowRules() {
        if (sentinelProperties.isEnable()) {
            // 保证Sentinel规则校验一定通过(即与之前的规则不同)
            List<FlowRule> newRules = new LinkedList<>(sentinelProperties.getFlowRules());
            FlowRuleManager.loadRules(newRules);

            log.info("Sentinel限流规则更新: {}", sentinelProperties.getFlowRules());
        }
    }

    private void loadHotFlowRules(){
        List<ParamFlowRule> newRules = new LinkedList<>(sentinelProperties.getHotFlowRules());
        ParamFlowRuleManager.loadRules(newRules);

        log.info("Sentinel热点参数限流规则更新: {}", sentinelProperties.getFlowRules());
    }

}
