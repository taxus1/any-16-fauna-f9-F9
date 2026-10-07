package com.somepro.interfaces.rest.ledger.vo;

import java.io.Serializable;

/**
 * 站点月度监测台账行对外返回对象（VO，用户接口层）—— 不可变 record。
 *
 * 一行 = 一个站 × 一个自然月：站点编号 + 月份打头，后面依次是该站该月的
 * 巡护任务条数、观测条数（含异常条数）、送检份数（含阳性份数）、预警条数。
 * month 为自然月串 yyyy-MM，按各档事实的业务时刻归属，跨月不串。
 */
public record MonthlyLedgerVO(String stationNo, String month,
                              int taskCount, int obsCount, int abnormalCount,
                              int sampleCount, int positiveCount, int alertCount)
        implements Serializable {
}
