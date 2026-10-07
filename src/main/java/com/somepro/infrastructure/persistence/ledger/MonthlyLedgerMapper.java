package com.somepro.infrastructure.persistence.ledger;

import com.somepro.domain.ledger.model.MonthlyLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 站点月度监测台账的只读聚合 Mapper（基础设施层）。
 *
 * 不落新表：一条 UNION ALL 把四档事实各按各的业务时刻归月聚合，再在最外层按
 * 「站 + 月」归并求和。每一档都 inner join 到 t_monitor_station 且限定
 * status &lt;&gt; 'CLOSED'（已关闭的站不进台账），链条上经过的每张表都显式带
 * del_flag = 0 —— 自定义 SQL 不走 @TableLogic，销掉的记录得自己滤干净，
 * 任务/观测/样本/预警销了都当没发生过，不把数字撑大。
 *
 * 归月时刻（各算各的）：任务 planned_date / 观测 observed_at /
 * 样本 sent_at / 预警 raised_at。DATE_FORMAT(...,'%Y-%m') 按自然月切，
 * 上月最后一天与本月头一天各归各月；DATE 类型的 planned_date 用区间归月，
 * 避免对索引列套函数。
 *
 * 阻塞（JDBC）API，只能在 boundedElastic 线程上调用（见 MonthlyLedgerRepositoryImpl#blocking）。
 */
@Mapper
public interface MonthlyLedgerMapper {

    /**
     * 按站 × 月聚合台账。站/月过滤放在最外层，四个分支共享，一处条件不漏给某一档。
     * 排序固定 month、station_id：翻页顺序稳定，且台账按月份、站点铺开。
     */
    @Select("""
            <script>
            SELECT t.station_id    AS stationId,
                   s.station_no    AS stationNo,
                   t.month         AS month,
                   SUM(t.task_cnt)     AS taskCount,
                   SUM(t.obs_cnt)      AS obsCount,
                   SUM(t.abnormal_cnt) AS abnormalCount,
                   SUM(t.sample_cnt)   AS sampleCount,
                   SUM(t.positive_cnt) AS positiveCount,
                   SUM(t.alert_cnt)    AS alertCount
            FROM (
                /* 巡护任务：按计划日期归月，归属站直接取任务上的 station_id */
                SELECT tk.station_id AS station_id,
                       DATE_FORMAT(tk.planned_date, '%Y-%m') AS month,
                       1 AS task_cnt, 0 AS obs_cnt, 0 AS abnormal_cnt,
                       0 AS sample_cnt, 0 AS positive_cnt, 0 AS alert_cnt
                FROM t_patrol_task tk
                INNER JOIN t_monitor_station s ON s.id = tk.station_id AND s.del_flag = 0
                WHERE tk.del_flag = 0 AND s.status &lt;&gt; 'CLOSED'
                  AND tk.planned_date IS NOT NULL
                UNION ALL
                /* 观测：按观测时刻归月，归属站沿观测 -> 任务取（任务销了整条不算） */
                SELECT tk.station_id AS station_id,
                       DATE_FORMAT(o.observed_at, '%Y-%m') AS month,
                       0 AS task_cnt,
                       1 AS obs_cnt,
                       CASE WHEN o.health_status IN ('INJURED', 'DEAD', 'SUSPECT') THEN 1 ELSE 0 END AS abnormal_cnt,
                       0 AS sample_cnt, 0 AS positive_cnt, 0 AS alert_cnt
                FROM t_wildlife_obs o
                INNER JOIN t_patrol_task tk ON tk.id = o.task_id AND tk.del_flag = 0
                INNER JOIN t_monitor_station s ON s.id = tk.station_id AND s.del_flag = 0
                WHERE o.del_flag = 0 AND s.status &lt;&gt; 'CLOSED'
                  AND o.observed_at IS NOT NULL
                UNION ALL
                /* 样本：按送检时刻归月，归属站沿 样本 -> 上报 -> 观测 -> 任务 取；
                   阳性是送检样本里结果为 POSITIVE 的，故 positive_cnt 恒不大于 sample_cnt */
                SELECT tk.station_id AS station_id,
                       DATE_FORMAT(sm.sent_at, '%Y-%m') AS month,
                       0 AS task_cnt, 0 AS obs_cnt, 0 AS abnormal_cnt,
                       1 AS sample_cnt,
                       CASE WHEN sm.result = 'POSITIVE' THEN 1 ELSE 0 END AS positive_cnt,
                       0 AS alert_cnt
                FROM t_sample_test sm
                INNER JOIN t_abnormal_report r ON r.id = sm.report_id AND r.del_flag = 0
                INNER JOIN t_wildlife_obs o ON o.id = r.obs_id AND o.del_flag = 0
                INNER JOIN t_patrol_task tk ON tk.id = o.task_id AND tk.del_flag = 0
                INNER JOIN t_monitor_station s ON s.id = tk.station_id AND s.del_flag = 0
                WHERE sm.del_flag = 0 AND s.status &lt;&gt; 'CLOSED'
                  AND sm.sent_at IS NOT NULL
                UNION ALL
                /* 预警：按发布时刻归月，归属站沿 预警 -> 样本 -> 上报 -> 观测 -> 任务 取 */
                SELECT tk.station_id AS station_id,
                       DATE_FORMAT(a.raised_at, '%Y-%m') AS month,
                       0 AS task_cnt, 0 AS obs_cnt, 0 AS abnormal_cnt,
                       0 AS sample_cnt, 0 AS positive_cnt, 1 AS alert_cnt
                FROM t_epi_alert a
                INNER JOIN t_sample_test sm ON sm.id = a.sample_id AND sm.del_flag = 0
                INNER JOIN t_abnormal_report r ON r.id = sm.report_id AND r.del_flag = 0
                INNER JOIN t_wildlife_obs o ON o.id = r.obs_id AND o.del_flag = 0
                INNER JOIN t_patrol_task tk ON tk.id = o.task_id AND tk.del_flag = 0
                INNER JOIN t_monitor_station s ON s.id = tk.station_id AND s.del_flag = 0
                WHERE a.del_flag = 0 AND s.status &lt;&gt; 'CLOSED'
                  AND a.raised_at IS NOT NULL
            ) t
            INNER JOIN t_monitor_station s ON s.id = t.station_id AND s.del_flag = 0
            WHERE s.status &lt;&gt; 'CLOSED'
            <if test="stationId != null">
                AND t.station_id = #{stationId}
            </if>
            <if test="monthFrom != null and monthFrom != ''">
                AND t.month &gt;= #{monthFrom}
            </if>
            <if test="monthTo != null and monthTo != ''">
                AND t.month &lt;= #{monthTo}
            </if>
            GROUP BY t.station_id, s.station_no, t.month
            ORDER BY t.month ASC, t.station_id ASC
            </script>
            """)
    List<MonthlyLedger> selectLedger(@Param("stationId") Long stationId,
                                     @Param("monthFrom") String monthFrom,
                                     @Param("monthTo") String monthTo);
}
