package com.somepro.infrastructure.persistence.ledger.po;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 台账聚合查询的投影行（基础设施层）：不是任何一张表的形状，没有 @TableName，
 * 只是 {@code StationMonthLedgerMapper} 那条 UNION ALL + GROUP BY 查询的承接对象。
 *
 * 列别名带下划线，靠 map-underscore-to-camel-case 映射到这些字段。
 */
@Getter
@Setter
public class StationMonthLedgerRow implements Serializable {

    private String stationNo;

    private String stationName;

    /** 自然月，yyyy-MM */
    private String month;

    private Long taskCount;

    private Long obsCount;

    private Long abnormalCount;

    private Long sampleCount;

    private Long positiveCount;

    private Long alertCount;
}
