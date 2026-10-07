package com.somepro.infrastructure.persistence.ledger;

import com.github.pagehelper.PageHelper;
import com.somepro.domain.ledger.model.StationMonthLedger;
import com.somepro.domain.ledger.repository.StationMonthLedgerRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.config.ReactiveOperatorContext;
import com.somepro.infrastructure.persistence.audit.AuditContextHolder;
import com.somepro.infrastructure.persistence.ledger.po.StationMonthLedgerRow;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 监测台账仓储适配器（基础设施层）：把聚合查询的投影行转成领域值对象并接 PageHelper 分页。
 *
 * 台账是只读汇总，没有写操作。分页统一走 PageHelper（GROUP BY 查询的 count 由插件改写，
 * 数的是归拢后的站点×月份行数），行数多时一页一页取，不一次全塞回来。
 */
@Repository
public class StationMonthLedgerRepositoryImpl implements StationMonthLedgerRepository {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final StationMonthLedgerMapper ledgerMapper;

    public StationMonthLedgerRepositoryImpl(StationMonthLedgerMapper ledgerMapper) {
        this.ledgerMapper = ledgerMapper;
    }

    @Override
    public Mono<PageResult<StationMonthLedger>> page(int pageNum, int pageSize,
                                                     String stationNo, YearMonth month) {
        return this.<PageResult<StationMonthLedger>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                String monthFrom = month == null ? null : month.format(MONTH_FORMAT);
                // 月份上界取「不含」的下一月，下界含、上界不含，两头都落到正确的自然月
                String monthTo = month == null ? null : month.plusMonths(1).format(MONTH_FORMAT);
                List<StationMonthLedgerRow> rows =
                        ledgerMapper.selectLedgerRows(normalize(stationNo), monthFrom, monthTo);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<StationMonthLedger> content = rows.stream()
                        .map(StationMonthLedgerRepositoryImpl::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                PageHelper.clearPage();
            }
        });
    }

    private static StationMonthLedger toDomain(StationMonthLedgerRow row) {
        // 归拢后的组里至少有一档为 1，SUM 不会为 NULL；这里仍按 0 兜底，防空值拆箱。
        return new StationMonthLedger(
                row.getStationNo(),
                row.getStationName(),
                row.getMonth(),
                nz(row.getTaskCount()),
                nz(row.getObsCount()),
                nz(row.getAbnormalCount()),
                nz(row.getSampleCount()),
                nz(row.getPositiveCount()),
                nz(row.getAlertCount()));
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * 阻塞 DB 调用 → 响应式链路的桥接器：先取 Reactor Context 里的操作人，
     * 再切到 boundedElastic 执行 JDBC（台账只读，操作人仅供审计上下文保持一致）。
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
