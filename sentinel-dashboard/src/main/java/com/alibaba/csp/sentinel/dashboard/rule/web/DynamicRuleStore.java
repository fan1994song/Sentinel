package com.alibaba.csp.sentinel.dashboard.rule.web;


import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.RuleEntity;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRuleProvider;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRulePublisher;

import java.util.List;

/**
 * @Author: fss
 * @Date: 2022/10/5 15
 * @Description:
 */
public abstract class DynamicRuleStore<T extends RuleEntity> implements DynamicRuleProvider<List<T>>,
        DynamicRulePublisher<List<T>> {

    protected RuleType ruleType;

    public RuleType getRuleType() {
        return ruleType;
    }

}