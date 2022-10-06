package com.alibaba.csp.sentinel.dashboard.rule.web.nacos;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.RuleEntity;
import com.alibaba.csp.sentinel.dashboard.rule.nacos.NacosConfigUtil;
import com.alibaba.csp.sentinel.dashboard.rule.web.DynamicRuleStore;
import com.alibaba.csp.sentinel.dashboard.rule.web.RuleConfigUtil;
import com.alibaba.csp.sentinel.dashboard.rule.web.RuleType;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.nacos.api.config.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: fss
 * @Date: 2022/10/5 16
 * @Description:
 */
public class DynamicRuleNacosStore<T extends RuleEntity> extends DynamicRuleStore<T> {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicRuleNacosStore.class);

    private ConfigService configService;

    public DynamicRuleNacosStore(final RuleType ruleType, final ConfigService configService) {
        super.ruleType = ruleType;
        this.configService = configService;
    }

    @Override
    public List<T> getRules(String appName) throws Exception {
        String rules = configService.getConfig(RuleConfigUtil.getDataId(appName, ruleType), NacosConfigUtil.GROUP_ID, 3000);
        if (StringUtil.isEmpty(rules)) {
            return new ArrayList<>();
        }

        Converter<String, List<T>> decoder = RuleConfigUtil.getDecoder(ruleType.getClazz());
        return decoder.convert(rules);
    }

    @Override
    public void publish(String app, List<T> rules) throws Exception {
        if (rules == null) {
            return;
        }

        Converter<Object, String> encoder = RuleConfigUtil.getEncoder();
        String value = encoder.convert(rules);

        LOG.info("publish dataId:{},group:{},content:{}", RuleConfigUtil.getDataId(app, ruleType), NacosConfigUtil.GROUP_ID, value);
        boolean result = configService.publishConfig(RuleConfigUtil.getDataId(app, ruleType), NacosConfigUtil.GROUP_ID, value);
        LOG.info("publish rule success - app: {}, type: {}, value: {} ,执行结果:{}", app, ruleType.getName(), value, result);
    }
}
