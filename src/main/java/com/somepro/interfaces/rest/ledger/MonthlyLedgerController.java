package com.somepro.interfaces.rest.ledger;

import com.somepro.application.ledger.MonthlyLedgerAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.common.vo.PageVO;
import com.somepro.interfaces.rest.ledger.converter.MonthlyLedgerVoConverter;
import com.somepro.interfaces.rest.ledger.vo.MonthlyLedgerVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 站点月度监测台账接口（用户接口层）：只读汇总，不落新表。
 *
 * 一行是一个站一个自然月：月份按各档事实的业务时刻归（任务看计划日期、观测看观测时刻、
 * 样本看送检时刻、预警看发布时刻）。可按月份区间（monthFrom/monthTo，yyyy-MM）和站点
 * （stationId）过滤，PageHelper 分页一页一页走；哪个月全站一条都没有就回空页，不报错。
 */
@RestController
@RequestMapping("/api/ledger/monthly")
public class MonthlyLedgerController {

    private final MonthlyLedgerAppService ledgerAppService;

    public MonthlyLedgerController(MonthlyLedgerAppService ledgerAppService) {
        this.ledgerAppService = ledgerAppService;
    }

    /**
     * 翻台账：monthFrom/monthTo/stationId 都可空，全空翻全部站月。
     * 返回按 month、stationId 稳定排序的分页结果。
     */
    @GetMapping({"", "/list"})
    public Mono<Result<PageVO<MonthlyLedgerVO>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) String monthFrom,
            @RequestParam(required = false) String monthTo) {
        return ledgerAppService.pageLedger(pageNum, pageSize, stationId, monthFrom, monthTo)
                .map(MonthlyLedgerVoConverter::toPageVo)
                .map(Result::ok);
    }
}
