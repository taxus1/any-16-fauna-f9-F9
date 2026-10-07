package com.somepro.infrastructure.persistence.ledger;

import com.github.pagehelper.PageHelper;
import com.somepro.domain.ledger.model.MonthlyLedger;
import com.somepro.domain.ledger.repository.MonthlyLedgerRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.config.ReactiveOperatorContext;
import com.somepro.infrastructure.persistence.audit.AuditContextHolder;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.function.Supplier;

/**
 * 站点月度监测台账仓储适配器（基础设施层）。
 *
 * 纯只读聚合，没有写侧。分页统一走 PageHelper：startPage 只改写紧随其后的这一次查询，
 * finally 里必须 clearPage，否则分页参数会顺着 boundedElastic 线程池污染下一次调用。
 * 整个月一条数据都没有时，聚合结果就是空表，回空页而不是报错。
 */
@Repository
public class MonthlyLedgerRepositoryImpl implements MonthlyLedgerRepository {

    private final MonthlyLedgerMapper ledgerMapper;

    public MonthlyLedgerRepositoryImpl(MonthlyLedgerMapper ledgerMapper) {
        this.ledgerMapper = ledgerMapper;
    }

    @Override
    public Mono<PageResult<MonthlyLedger>> page(int pageNum, int pageSize,
                                               Long stationId, String monthFrom, String monthTo) {
        return this.<PageResult<MonthlyLedger>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                List<MonthlyLedger> rows = ledgerMapper.selectLedger(stationId, monthFrom, monthTo);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                return new PageResult<>(rows, total, pageNum, pageSize);
            } finally {
                PageHelper.clearPage();
            }
        });
    }

    /**
     * 阻塞 DB 调用 → 响应式链路的桥接器：先取 Reactor Context 里的操作人，
     * 再切到 boundedElastic 执行 JDBC（台账只读不取操作人，但桥接规矩与其它仓储一致）。
     */
    private <T> Mono<T> blocking(Supplier<T> supplier) {
        return Mono.deferContextual(ctx -> {
            String operator = ReactiveOperatorContext.getOperator(ctx);
            return Mono.fromCallable(() -> {
                AuditContextHolder.setOperator(operator);
                try {
                    return supplier.get();
                } finally {
                    AuditContextHolder.clear();
                }
            }).subscribeOn(Schedulers.boundedElastic());
        });
    }
}
