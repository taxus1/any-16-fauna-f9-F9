package com.somepro.domain.ledger.model;

import com.somepro.common.exception.BizException;

/**
 * 监测台账一行（领域值对象，不可变 record）：一个站在一个自然月里的汇总数。
 *
 * 不落新表 —— 台账是按「站 × 自然月」从任务/观测/样本/预警各档账上现取数铺出来的，
 * 一个站一个自然月只出一行；哪个站哪个月一档数都没有，就不占行。
 *
 * 归月口径各算各的，互不串：
 * - 巡护任务按计划日期（planned_date）所在自然月；
 * - 观测按观测时刻（observed_at）所在自然月；
 * - 样本按送检时刻（sent_at）所在自然月；
 * - 预警按发布时刻（raised_at）所在自然月。
 * 异常个体条数是观测条数的子集（健康状态为受伤/死亡/疑似疫病），阳性份数是送检份数的子集，
 * 两档之间天然对得上：异常 ≤ 观测、阳性 ≤ 送检。
 */
public record StationMonthLedger(String stationNo,
                                 String stationName,
                                 String month,
                                 long taskCount,
                                 long obsCount,
                                 long abnormalCount,
                                 long sampleCount,
                                 long positiveCount,
                                 long alertCount) {

    /** 紧凑构造器：守住「异常不超观测、阳性不超送检」的台账口径，计数不许为负。 */
    public StationMonthLedger {
        if (stationNo == null || stationNo.isBlank()) {
            throw new BizException("站点编号不能为空");
        }
        if (month == null || month.isBlank()) {
            throw new BizException("月份不能为空");
        }
        if (taskCount < 0 || obsCount < 0 || abnormalCount < 0
                || sampleCount < 0 || positiveCount < 0 || alertCount < 0) {
            throw new BizException("台账计数不能为负");
        }
        if (abnormalCount > obsCount) {
            throw new BizException("异常个体条数不能多于观测条数");
        }
        if (positiveCount > sampleCount) {
            throw new BizException("阳性份数不能多于送检份数");
        }
    }
}
