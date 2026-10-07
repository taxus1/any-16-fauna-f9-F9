package com.somepro.domain.ledger.repository;

import com.somepro.domain.ledger.model.StationMonthLedger;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

import java.time.YearMonth;

/**
 * 监测台账仓储端口（领域层）：按站点 × 自然月汇总取数。
 *
 * 台账不建新表，数据全部从既有任务/观测/样本/预警表上按业务时刻归月现算；
 * 已销账（各表 del_flag=1）的记录一律不算，已关闭（CLOSED）的站不出台账。
 *
 * 筛选条件都可空：stationNo 为空翻全部站，month 为空翻所有自然月；
 * 一个站一个月一档数都没有时不产生行，整月无数据自然回空页，不报错。
 */
public interface StationMonthLedgerRepository {

    /**
     * 按站点 × 月份分页铺台账。
     *
     * @param pageNum   页码（透传 PageHelper，不写死）
     * @param pageSize  每页行数
     * @param stationNo 站点编号精确匹配，可空
     * @param month     自然月（yyyy-MM），可空
     */
    Mono<PageResult<StationMonthLedger>> page(int pageNum, int pageSize,
                                              String stationNo, YearMonth month);
}
