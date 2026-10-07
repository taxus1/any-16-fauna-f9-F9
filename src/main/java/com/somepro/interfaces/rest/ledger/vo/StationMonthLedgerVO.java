package com.somepro.interfaces.rest.ledger.vo;

import java.io.Serializable;

/**
 * 监测台账一行的对外 VO（不可变 record）：一个站一个自然月的汇总数。
 *
 * 只暴露台账要看的字段，不携带 delFlag / 审计列等内部信息。
 */
public record StationMonthLedgerVO(String stationNo,
                                   String stationName,
                                   String month,
                                   long taskCount,
                                   long obsCount,
                                   long abnormalCount,
                                   long sampleCount,
                                   long positiveCount,
                                   long alertCount)
        implements Serializable {
}
