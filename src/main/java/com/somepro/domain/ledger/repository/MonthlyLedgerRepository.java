package com.somepro.domain.ledger.repository;

import com.somepro.domain.ledger.model.MonthlyLedger;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * 站点月度监测台账仓储端口（领域层）。
 *
 * 台账不落新表，是四张事实表（任务 / 观测 / 样本 / 预警）按站按月现取聚合的只读视图，
 * 所以这里只有一个分页查询，没有写入。
 */
public interface MonthlyLedgerRepository {

    /**
     * 按站 × 自然月分页取台账行。
     *
     * @param stationId 只看某个站时传，null 翻全站
     * @param monthFrom 只看「不早于」哪个自然月（yyyy-MM，含本月），null 不设下界
     * @param monthTo   只看「不晚于」哪个自然月（yyyy-MM，含本月），null 不设上界
     */
    Mono<PageResult<MonthlyLedger>> page(int pageNum, int pageSize,
                                        Long stationId, String monthFrom, String monthTo);
}
