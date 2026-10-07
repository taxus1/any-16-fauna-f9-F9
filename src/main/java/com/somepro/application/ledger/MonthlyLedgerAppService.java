package com.somepro.application.ledger;

import com.somepro.common.exception.BizException;
import com.somepro.domain.ledger.model.MonthlyLedger;
import com.somepro.domain.ledger.repository.MonthlyLedgerRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

/**
 * 站点月度监测台账应用层：只编排「按站按月翻台账」这一个只读用例。
 *
 * 台账不落新表，数字由仓储层现取聚合；这里只管入参（月份/站/分页）的口径与校验，
 * 不碰任何写操作。月份入参统一 yyyy-MM，按字符串比较与库里的自然月串同构，
 * 区间两端都含本（自）然月。
 */
@Service
public class MonthlyLedgerAppService {

    /** 自然月入参格式：yyyy-MM（如 2026-09）。 */
    private static final Pattern MONTH_PATTERN = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private final MonthlyLedgerRepository ledgerRepository;

    public MonthlyLedgerAppService(MonthlyLedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    /**
     * 翻台账：可按月份区间、按站过滤，分页一页一页走。
     *
     * @param monthFrom 起始自然月（含），可空；可与 monthTo 只传一头
     * @param monthTo   截止自然月（含），可空
     */
    public Mono<PageResult<MonthlyLedger>> pageLedger(int pageNum, int pageSize,
                                                      Long stationId, String monthFrom, String monthTo) {
        if (pageNum < 1) {
            return Mono.error(new BizException("页码不能小于 1"));
        }
        if (pageSize < 1) {
            return Mono.error(new BizException("每页行数不能小于 1"));
        }
        String from = normalizeMonth(monthFrom);
        String to = normalizeMonth(monthTo);
        if (from != null && to != null && from.compareTo(to) > 0) {
            return Mono.error(new BizException("起始月份不能晚于截止月份"));
        }
        return ledgerRepository.page(pageNum, pageSize, stationId, from, to);
    }

    private static String normalizeMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        String value = month.trim();
        if (!MONTH_PATTERN.matcher(value).matches()) {
            throw new BizException("月份格式非法，应为 yyyy-MM（如 2026-09）");
        }
        return value;
    }
}
