package com.somepro.domain.ledger.model;

/**
 * 站点月度监测台账行（领域值对象，不可变 record）：一行 = 一个站 × 一个自然月的汇总数。
 *
 * 这是一张「现取现拼」的汇总账，不落新表：各档数分别从巡护任务 / 观测 / 样本 / 预警
 * 四张事实表按各自的业务时刻归月后聚合，一个站一个月里各算各的，不串月不串站。
 *
 * 归月口径（看业务时刻落在哪个自然月，不看登记/创建时刻）：
 * - 巡护任务：按 planned_date（计划日期）归月；
 * - 观测：按 observed_at（观测时刻）归月；
 * - 样本：按 sent_at（送检时刻）归月；
 * - 预警：按 raised_at（发布时刻）归月。
 *
 * 数账约束（同一套内连接、同一套 del_flag=0 过滤，天然对得上）：
 * - 阳性份数 ≤ 送检份数（阳性是送检样本里结果为阳性的那部分）；
 * - 异常条数 ≤ 观测条数（异常是观测里健康状态非正常的那部分）。
 *
 * 已销账（del_flag=1）的任务/观测/样本/预警都当没发生过，不计入；
 * 已关闭（status=CLOSED）的站不进台账。一个站一个月一档数都没有就不占行。
 */
public record MonthlyLedger(Long stationId,
                            String stationNo,
                            /** 自然月，格式 yyyy-MM（按业务时刻归属，跨月不串） */
                            String month,
                            /** 该站该月派发的巡护任务条数（按计划日期归月） */
                            int taskCount,
                            /** 该站该月录入的观测条数（按观测时刻归月） */
                            int obsCount,
                            /** 其中异常个体条数（健康状态非正常；按观测时刻归月） */
                            int abnormalCount,
                            /** 送检样本份数（按送检时刻归月） */
                            int sampleCount,
                            /** 检出阳性份数（送检样本里结果为阳性；按送检时刻归月） */
                            int positiveCount,
                            /** 立起的预警条数（按发布时刻归月） */
                            int alertCount) {
}
