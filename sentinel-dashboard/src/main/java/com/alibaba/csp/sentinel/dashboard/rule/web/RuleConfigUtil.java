package com.alibaba.csp.sentinel.dashboard.rule.web;

/**
 * @Author: fss
 * @Date: 2022/10/5 15
 * @Description:
 */
import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.RuleEntity;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.fastjson.JSON;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author FengJianxin
 * @since 1.8.4
 */
public final class RuleConfigUtil {

    private static final Converter<Object, String> ENCODER = JSON::toJSONString;
    private static final Map<Class<?>, Object> DECODER_MAP = new HashMap<>();

    private RuleConfigUtil() {
    }


    public static String getDataId(String appName, RuleType ruleType) {
        return String.format("%s-%s", appName, ruleType.getName());
    }


    public static Converter<Object, String> getEncoder() {
        return ENCODER;
    }

    @SuppressWarnings("unchecked")
    public static synchronized <T extends RuleEntity> Converter<String, List<T>> getDecoder(Class<T> clazz) {
        Object decoder = DECODER_MAP.computeIfAbsent(clazz, new Function<Class<?>, Converter<String, List<T>>>() {
            @Override
            public Converter<String, List<T>> apply(final Class<?> targetClass) {
                return source -> JSON.parseArray(source, clazz);
            }
        });
        return (Converter<String, List<T>>) decoder;
    }

}
