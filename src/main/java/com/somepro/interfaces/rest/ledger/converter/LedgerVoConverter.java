package com.somepro.interfaces.rest.ledger.converter;

import com.somepro.domain.ledger.model.StationMonthLedger;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.interfaces.rest.common.vo.PageVO;
import com.somepro.interfaces.rest.ledger.vo.StationMonthLedgerVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * StationMonthLedger（领域值对象）→ 对外 VO 转换器（用户接口层）。
 */
public final class LedgerVoConverter {

    private LedgerVoConverter() {
    }

    public static StationMonthLedgerVO toVo(StationMonthLedger ledger) {
        return new StationMonthLedgerVO(
                ledger.stationNo(),
                ledger.stationName(),
                ledger.month(),
                ledger.taskCount(),
                ledger.obsCount(),
                ledger.abnormalCount(),
                ledger.sampleCount(),
                ledger.positiveCount(),
                ledger.alertCount());
    }

    public static PageVO<StationMonthLedgerVO> toPageVo(PageResult<StationMonthLedger> page) {
        List<StationMonthLedgerVO> content = page.content().stream()
                .map(LedgerVoConverter::toVo)
                .collect(Collectors.toList());
        return new PageVO<>(content, page.total(), page.pageNum(), page.pageSize(), page.totalPages());
    }
}
