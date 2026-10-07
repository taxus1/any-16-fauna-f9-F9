package com.somepro.application.ledger;

import com.somepro.common.exception.BizException;
import com.somepro.domain.ledger.model.StationMonthLedger;
import com.somepro.domain.ledger.repository.StationMonthLedgerRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 监测台账应用层：按站点 × 自然月翻台账的用例编排。
 *
 * 只做取数编排，不落任何新表：数据由仓储层从各档账上按业务时刻归月汇总。
 * 月份入参按自然月 yyyy-MM 解析（上个月最后一天、这个月头一天各归各月，边界在 SQL 里落）。
 */
@Service
public class StationMonthLedgerAppService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final StationMonthLedgerRepository ledgerRepository;

    public StationMonthLedgerAppService(StationMonthLedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    /**
     * 台账分页：month 为空翻所有自然月，stationNo 为空翻全部站；
     * 哪个站哪个月没数不占行，整月没数回空页。
     */
    public Mono<PageResult<StationMonthLedger>> pageLedger(int pageNum, int pageSize,
                                                           String stationNo, String month) {
        return ledgerRepository.page(pageNum, pageSize, normalize(stationNo), parseMonth(month));
    }

    /** 月份只认 yyyy-MM 自然月，格式不对返回业务失败，不甩底层解析异常。 */
    private static YearMonth parseMonth(String month) {
        String value = normalize(month);
        if (value == null) {
            return null;
        }
        try {
            return YearMonth.parse(value, MONTH_FORMAT);
        } catch (DateTimeParseException e) {
            throw new BizException("月份格式非法，应为 yyyy-MM（如 2026-09）");
        }
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
