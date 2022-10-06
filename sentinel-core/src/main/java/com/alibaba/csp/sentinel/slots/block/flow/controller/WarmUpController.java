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
package com.alibaba.csp.sentinel.slots.block.flow.controller;

import java.util.concurrent.atomic.AtomicLong;

import com.alibaba.csp.sentinel.util.TimeUtil;
import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.slots.block.flow.TrafficShapingController;

/**
 * <p>
 * The principle idea comes from Guava. However, the calculation of Guava is
 * rate-based, which means that we need to translate rate to QPS.
 * 主要的想法来自于Guava。但是，Guava的计算是基于速率的，这意味着我们需要将速率转换为QPS
 * 此冷数据限流策略，如果流量波动较大时，qps限流会经常进入冷访问阶段
 * </p>
 *
 * <p>
 * Requests arriving at the pulse may drag down long idle systems even though it
 * has a much larger handling capability in stable period. It usually happens in
 * scenarios that require extra time for initialization, e.g. DB establishes a connection,
 * connects to a remote service, and so on. That’s why we need “warm up”.
 * </p>
 *
 * <p>
 * Sentinel's "warm-up" implementation is based on the Guava's algorithm.
 * However, Guava’s implementation focuses on adjusting the request interval,
 * which is similar to leaky bucket. Sentinel pays more attention to
 * controlling the count of incoming requests per second without calculating its interval,
 * which resembles token bucket algorithm.
 * </p>
 *
 * <p>
 * The remaining tokens in the bucket is used to measure the system utility.
 * Suppose a system can handle b requests per second. Every second b tokens will
 * be added into the bucket until the bucket is full. And when system processes
 * a request, it takes a token from the bucket. The more tokens left in the
 * bucket, the lower the utilization of the system; when the token in the token
 * bucket is above a certain threshold, we call it in a "saturation" state.
 * </p>
 *
 * <p>
 * Base on Guava’s theory, there is a linear equation we can write this in the
 * form y = m * x + b where y (a.k.a y(x)), or qps(q)), is our expected QPS
 * given a saturated period (e.g. 3 minutes in), m is the rate of change from
 * our cold (minimum) rate to our stable (maximum) rate, x (or q) is the
 * occupied token.
 * </p>
 * warm-up使用到的令牌桶的限流算法
 * @author jialiang.linjl
 */
public class WarmUpController implements TrafficShapingController {

    /**
     * FlowRule中设定的阈值
     */
    protected double count;
    /**
     * 冷却因子，默认为3，表示倍数，即系统最"冷"时(令牌桶饱和时)，令牌生成时间间隔是正常情况下的多少倍
     */
    private int coldFactor;
    /**
     * 预警值，表示进入预热或预热完毕
     */
    protected int warningToken = 0;
    /**
     * 最大可用token值，计算公式：warningToken+(2*时间*阈值)/(1+因子)，默认情况下为warningToken的2倍
     */
    private int maxToken;
    /**
     * 斜度，(coldFactor-1)/count/(maxToken-warningToken)，用于计算token生成的时间间隔，进而计算当前token生成速度，最终比较token生成速度与消费速度，决定是否限流
     */
    protected double slope;

    /**
     * 姑且可以理解为令牌桶中令牌的数量
     */
    protected AtomicLong storedTokens = new AtomicLong(0);
    /**
     * 上次填充的时间
     */
    protected AtomicLong lastFilledTime = new AtomicLong(0);

    public WarmUpController(double count, int warmUpPeriodInSec, int coldFactor) {
        construct(count, warmUpPeriodInSec, coldFactor);
    }

    public WarmUpController(double count, int warmUpPeriodInSec) {
        construct(count, warmUpPeriodInSec, 3);
    }

    /**
     * warmUpPeriodInSec：系统预热时间
     * 例：count：5，warmUpPeriodInSec：10，coldFactor：3
     * warningToken=25
     * maxToken = 25+（2*10*5/(1+3)）=50
     * slope=2/5/25 = 0.016
     */
    private void construct(double count, int warmUpPeriodInSec, int coldFactor) {

        if (coldFactor <= 1) {
            throw new IllegalArgumentException("Cold factor should be larger than 1");
        }

        this.count = count;

        // 默认3
        this.coldFactor = coldFactor;

        // stableInterval：令牌生成的时间间隔：200ms
        // warningToken:
        // thresholdPermits = 0.5 * warmupPeriod / stableInterval.
        // warningToken = 100;
        warningToken = (int)(warmUpPeriodInSec * count) / (coldFactor - 1);
        // / maxPermits = thresholdPermits + 2 * warmupPeriod /
        // (stableInterval + coldInterval)
        // maxToken = 200
        maxToken = warningToken + (int)(2 * warmUpPeriodInSec * count / (1.0 + coldFactor));

        // slope
        // slope = (coldIntervalMicros - stableIntervalMicros) / (maxPermits
        // - thresholdPermits);
        slope = (coldFactor - 1.0) / count / (maxToken - warningToken);

    }

    @Override
    public boolean canPass(Node node, int acquireCount) {
        return canPass(node, acquireCount, false);
    }

    @Override
    public boolean canPass(Node node, int acquireCount, boolean prioritized) {
        // 获取当前1s的QPS
        long passQps = (long) node.passQps();

        // 获取上一窗口通过的qps
        long previousQps = (long) node.previousPassQps();
        // 生成和滑落token
        syncToken(previousQps);

        // 开始计算它的斜率
        // 如果进入了警戒线，开始调整他的qps
        long restToken = storedTokens.get();
        // 如果令牌桶中的token数量大于警戒值，说明还未预热结束，需要判断token的生成速度和消费速度
        if (restToken >= warningToken) {
            long aboveToken = restToken - warningToken;
            // 消耗的速度要比warning快，但是要比慢
            // current interval = restToken*slope+1/count
            double warningQps = Math.nextUp(1.0 / (aboveToken * slope + 1.0 / count));
            if (passQps + acquireCount <= warningQps) {
                return true;
            }
        } else {
            // 预热结束，直接判断是否超过设置的阈值
            if (passQps + acquireCount <= count) {
                return true;
            }
        }

        return false;
    }

    protected void syncToken(long passQps) {
        // 毫秒级时间戳-秒级整数
        long currentTime = TimeUtil.currentTimeMillis();
        currentTime = currentTime - currentTime % 1000;

        // 判断成立，如果小于，说明可能出现了时钟回拨
        // 如果等于，说明当前请求都处于同一秒内，则不进行token添加和滑落操作，避免的重复扣减
        // 时间窗口的跨度为1s
        long oldLastFillTime = lastFilledTime.get();
        if (currentTime <= oldLastFillTime) {
            return;
        }

        // token数量
        long oldValue = storedTokens.get();
        // 最新的令牌数
        long newValue = coolDownTokens(currentTime, passQps);
//        System.err.println("oldValue:" + oldValue + "newValue:" + newValue + "passQps:" + passQps);
        // 重置token数量
        if (storedTokens.compareAndSet(oldValue, newValue)) {
            // token滑落，即token消费
            // 减去上一个时间窗口的通过请求数
            long currentValue = storedTokens.addAndGet(0 - passQps);
            if (currentValue < 0) {
                storedTokens.set(0L);
            }
            // 设置最后添加令牌时间
            lastFilledTime.set(currentTime);
        }

    }

    /**
     * warmUpPeriodInSec：系统预热时间
     * 例：count：5，warmUpPeriodInSec：10，coldFactor：3
     * warningToken=25
     * maxToken = 25+（2*10*5/(1+3)）=50
     * slope=2/5/25 = 0.016
     */
    private long coolDownTokens(long currentTime, long passQps) {
        long oldValue = storedTokens.get();
        long newValue = oldValue;

        // 添加令牌的判断前提条件:
        // 当令牌的消耗程度远远低于警戒线的时候
        if (oldValue < warningToken) {
            // 计算过去一段时间内，可以通过的QPS总量
            // 初始加载时，令牌数量达到maxToken
            newValue = (long)(oldValue + (currentTime - lastFilledTime.get()) * count / 1000);
//            System.err.println("oldValue < warningToken oldValue" + oldValue + "newValue" + newValue + "passQps" + passQps);
        } else if (oldValue > warningToken) {
            // 大于警戒线时，passQps< 5/3=1
            /**
             * 当流量正常在count / coldFactor以内以冷数据提供服务，当流量激增，这里就不会增加newValue，因为passQps一定不会小于1/3限流值
             * 当令牌越来越小时，aboveToken就会变小，warningQps就会变大，所以qps就可以缓慢的上升,passQps会越来越大，进而消费令牌
             * 直到令牌数到达告警阈值，此时就认为预热完毕，正常按照令牌桶限流策略提供服务
             */
            if (passQps < (int)count / coldFactor) {
                newValue = (long)(oldValue + (currentTime - lastFilledTime.get()) * count / 1000);
            }
//            System.err.println("oldValue > warningToken oldValue" + oldValue + "newValue" + newValue + "passQps" + passQps + "count / coldFactor" + count / coldFactor);
        }
        return Math.min(newValue, maxToken);
    }

}
