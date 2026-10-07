package com.somepro.interfaces.rest.ledger.converter;

import com.somepro.domain.ledger.model.MonthlyLedger;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.interfaces.rest.common.vo.PageVO;
import com.somepro.interfaces.rest.ledger.vo.MonthlyLedgerVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MonthlyLedger（领域）→ MonthlyLedgerVO（对外）转换器（用户接口层）。
 * 台账只读，没有 VO → 领域方向。
 */
public final class MonthlyLedgerVoConverter {

    private MonthlyLedgerVoConverter() {
    }

    public static MonthlyLedgerVO toVo(MonthlyLedger ledger) {
        return new MonthlyLedgerVO(ledger.stationNo(), ledger.month(),
                ledger.taskCount(), ledger.obsCount(), ledger.abnormalCount(),
                ledger.sampleCount(), ledger.positiveCount(), ledger.alertCount());
    }

    public static PageVO<MonthlyLedgerVO> toPageVo(PageResult<MonthlyLedger> page) {
        List<MonthlyLedgerVO> content = page.content().stream()
                .map(MonthlyLedgerVoConverter::toVo)
                .collect(Collectors.toList());
        return new PageVO<>(content, page.total(), page.pageNum(), page.pageSize(), page.totalPages());
    }
}
