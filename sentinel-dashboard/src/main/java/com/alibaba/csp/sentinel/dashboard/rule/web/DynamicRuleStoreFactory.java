package com.alibaba.csp.sentinel.dashboard.rule.web;

/**
 * @Author: fss
 * @Date: 2022/10/5 15
 * @Description:
 */

import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.RuleEntity;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author FengJianxin
 * @since 2022/7/29
 */
@Component
public class DynamicRuleStoreFactory {

    private final Map<RuleType, DynamicRuleStore<? extends RuleEntity>> storeMap;

    public DynamicRuleStoreFactory(final List<DynamicRuleStore<? extends RuleEntity>> storeList) {
        Objects.requireNonNull(storeList, "store list must not be null");
        storeMap = new HashMap<>(storeList.size());
        storeList.forEach(item -> storeMap.putIfAbsent(item.getRuleType(), item));
    }

    @SuppressWarnings({"unchecked"})
    public <T extends RuleEntity> DynamicRuleStore<T> getDynamicRuleStoreByType(final RuleType ruleType) {
        DynamicRuleStore<T> store = (DynamicRuleStore<T>) storeMap.get(ruleType);
        if (store == null) {
            throw new RuntimeException("can not find DynamicRuleStore by type: " + ruleType.getName());
        }
        return store;
    }

}