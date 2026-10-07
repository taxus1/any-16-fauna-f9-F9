package com.somepro.interfaces.rest.ledger;

import com.somepro.application.ledger.StationMonthLedgerAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.common.vo.PageVO;
import com.somepro.interfaces.rest.ledger.converter.LedgerVoConverter;
import com.somepro.interfaces.rest.ledger.vo.StationMonthLedgerVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 监测台账接口（用户接口层）：一张按站点 × 自然月铺开的只读汇总表。
 *
 * 一行 = 一个站在一个自然月里的数（任务/观测/异常/送检/阳性/预警）。
 * 可按 month（yyyy-MM）、stationNo 筛，PageHelper 分页一页一页走；
 * 没数的站月不占行，整月没数回空页。
 */
@RestController
@RequestMapping("/api/ledger")
public class StationMonthLedgerController {

    private final StationMonthLedgerAppService ledgerAppService;

    public StationMonthLedgerController(StationMonthLedgerAppService ledgerAppService) {
        this.ledgerAppService = ledgerAppService;
    }

    @GetMapping({"", "/list"})
    public Mono<Result<PageVO<StationMonthLedgerVO>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String stationNo,
            @RequestParam(required = false) String month) {
        return ledgerAppService.pageLedger(pageNum, pageSize, stationNo, month)
                .map(LedgerVoConverter::toPageVo)
                .map(Result::ok);
    }
}
