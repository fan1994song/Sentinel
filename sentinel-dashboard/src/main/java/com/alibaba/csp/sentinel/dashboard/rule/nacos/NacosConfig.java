/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.dashboard.rule.nacos;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.gateway.ApiDefinitionEntity;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.gateway.GatewayFlowRuleEntity;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.AuthorityRuleEntity;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.DegradeRuleEntity;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.FlowRuleEntity;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.ParamFlowRuleEntity;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.SystemRuleEntity;
import com.alibaba.csp.sentinel.dashboard.rule.web.DynamicRuleStore;
import com.alibaba.csp.sentinel.dashboard.rule.web.RuleType;
import com.alibaba.csp.sentinel.dashboard.rule.web.nacos.DynamicRuleNacosStore;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Properties;

/**
 * 参考
 * https://developer.aliyun.com/article/991219?spm=a2c6h.12873639.article-detail.29.768e42b8c5vmRI&scm=20140722.ID_community@@article@@991219._.ID_community@@article@@991219-OR_rec-V_1
 * https://blog.csdn.net/zhuocailing3390/article/details/123257774
 *
 * @author Eric Zhao
 * @since 1.4.0
 */
@Configuration
public class NacosConfig {

    @Autowired
    private ConfigService configService;

    @Bean
    public Converter<List<FlowRuleEntity>, String> flowRuleEntityEncoder() {
        return JSON::toJSONString;
    }

    @Bean
    public Converter<String, List<FlowRuleEntity>> flowRuleEntityDecoder() {
        return s -> JSON.parseArray(s, FlowRuleEntity.class);
    }

    @Bean
    public ConfigService nacosConfigService() throws Exception {
        Properties properties = new Properties();
        // 服务器地址
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        //命名空间
        properties.put(PropertyKeyConst.NAMESPACE, "SENTINEL_GROUP");
        // 用户名
        properties.put(PropertyKeyConst.USERNAME, "nacos");
        // 密码
        properties.put(PropertyKeyConst.PASSWORD, "nacos");
        return ConfigFactory.createConfigService(properties);
//        return ConfigFactory.createConfigService("localhost");
    }


    /**
     * web结合
     */
    @Bean
    public DynamicRuleStore<FlowRuleEntity> flowRuleDynamicRuleStore(ConfigService configService) {
        return new DynamicRuleNacosStore<>(
                RuleType.FLOW,
                configService
        );
    }

    @Bean
    public DynamicRuleStore<DegradeRuleEntity> degradeRuleDynamicRuleStore(ConfigService configService) {
        return new DynamicRuleNacosStore<>(
                RuleType.DEGRADE,
                configService
        );
    }

    @Bean
    public DynamicRuleStore<ParamFlowRuleEntity> paramFlowRuleDynamicRuleStore(ConfigService configService) {
        return new DynamicRuleNacosStore<>(
                RuleType.PARAM_FLOW,
                configService
        );
    }

    @Bean
    public DynamicRuleStore<SystemRuleEntity> systemRuleDynamicRuleStore(ConfigService configService) {
        return new DynamicRuleNacosStore<>(
                RuleType.SYSTEM,
                configService
        );
    }

    @Bean
    public DynamicRuleStore<AuthorityRuleEntity> authorityRuleDynamicRuleStore(ConfigService configService) {
        return new DynamicRuleNacosStore<>(
                RuleType.AUTHORITY,
                configService
        );
    }

    @Bean
    public DynamicRuleStore<GatewayFlowRuleEntity> gatewayFlowRuleDynamicRuleStore(ConfigService configService) {
        return new DynamicRuleNacosStore<>(
                RuleType.GW_FLOW,
                configService
        );
    }

    @Bean
    public DynamicRuleStore<ApiDefinitionEntity> apiDefinitionDynamicRuleStore(ConfigService configService) {
        return new DynamicRuleNacosStore<>(
                RuleType.GW_API_GROUP,
                configService
        );
    }
}
