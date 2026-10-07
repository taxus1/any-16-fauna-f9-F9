package com.somepro.infrastructure.persistence.ledger;

import com.somepro.infrastructure.persistence.ledger.po.StationMonthLedgerRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 监测台账的只读 Mapper（基础设施层）。
 *
 * 台账不建新表：四档账（巡护任务 / 观测 / 样本 / 预警）各自一条 SELECT，
 * 用 UNION ALL 摊成「站点 + 月份 + 各档 0/1 计数」的细流，再按站点、月份 SUM 归拢，
 * 一次查询铺出整张台账。
 *
 * 归月各看各的业务时刻（别串月）：任务看 planned_date、观测看 observed_at、
 * 样本看 sent_at、预警看 raised_at，统一 DATE_FORMAT(...,'%Y-%m') 落自然月。
 *
 * 销账口径：自定义 SQL 不享受 @TableLogic 自动拼接，这里手写每档事实表自己的 del_flag = 0
 * （销掉的任务/观测/样本/预警都当没发生过）。中间的关联表（任务/上报/观测）只用来把
 * 站点归属摸出来，不加 del_flag 过滤 —— 几档数各算各的，任务销不销不影响它名下观测成账，
 * 观测作不作废也不影响已送检样本成账，不互相串。
 *
 * 站点归属统一走 观测 → 巡护任务(station_id) 这条链，不绕监测点：监测点支持撤点（逻辑删除），
 * 但历史观测/样本/预警都已发生，站点归属不该因子点撤掉而丢行；任务行只逻辑删除不物理消失，
 * 关联永不落空。关闭站（status='CLOSED'）在外层 JOIN 处整站剔除。
 */
@Mapper
public interface StationMonthLedgerMapper {

    /**
     * 按站点 × 自然月聚合台账行。
     *
     * @param stationNo 站点编号精确匹配，可空（空则不筛站）
     * @param monthFrom 月份下界（含，yyyy-MM 字符串按字典序即时间序），可空
     * @param monthTo   月份上界（不含），可空
     */
    @Select("<script>"
            + "SELECT s.station_no AS station_no, s.name AS station_name, u.month AS month, "
            + "       SUM(u.task_count)     AS task_count, "
            + "       SUM(u.obs_count)      AS obs_count, "
            + "       SUM(u.abnormal_count) AS abnormal_count, "
            + "       SUM(u.sample_count)   AS sample_count, "
            + "       SUM(u.positive_count) AS positive_count, "
            + "       SUM(u.alert_count)    AS alert_count "
            + "FROM ( "
            // ① 巡护任务：按计划日期归月，一条计 1 条任务
            + "  SELECT t.station_id AS station_id, DATE_FORMAT(t.planned_date, '%Y-%m') AS month, "
            + "         1 AS task_count, 0 AS obs_count, 0 AS abnormal_count, "
            + "         0 AS sample_count, 0 AS positive_count, 0 AS alert_count "
            + "  FROM t_patrol_task t "
            + "  WHERE t.del_flag = 0 "
            + "  UNION ALL "
            // ② 观测：按观测时刻归月，一条计 1 条观测；健康状态为受伤/死亡/疑似疫病的同时计 1 条异常
            //    （健康状态码与 ObsSummary.ABNORMAL_HEALTH 同口径：INJURED / DEAD / SUSPECT）
            + "  SELECT pt.station_id AS station_id, DATE_FORMAT(o.observed_at, '%Y-%m') AS month, "
            + "         0 AS task_count, 1 AS obs_count, "
            + "         CASE WHEN o.health_status IN ('INJURED','DEAD','SUSPECT') THEN 1 ELSE 0 END AS abnormal_count, "
            + "         0 AS sample_count, 0 AS positive_count, 0 AS alert_count "
            + "  FROM t_wildlife_obs o "
            + "  INNER JOIN t_patrol_task pt ON pt.id = o.task_id "
            + "  WHERE o.del_flag = 0 AND o.observed_at IS NOT NULL "
            + "  UNION ALL "
            // ③ 样本：按送检时刻归月，一份计 1 份送检；结果阳性的同时计 1 份阳性（阳性必是送检子集）
            + "  SELECT pt.station_id AS station_id, DATE_FORMAT(sa.sent_at, '%Y-%m') AS month, "
            + "         0 AS task_count, 0 AS obs_count, 0 AS abnormal_count, "
            + "         1 AS sample_count, "
            + "         CASE WHEN sa.result = 'POSITIVE' THEN 1 ELSE 0 END AS positive_count, "
            + "         0 AS alert_count "
            + "  FROM t_sample_test sa "
            + "  INNER JOIN t_abnormal_report ar ON ar.id = sa.report_id "
            + "  INNER JOIN t_wildlife_obs ob   ON ob.id = ar.obs_id "
            + "  INNER JOIN t_patrol_task pt    ON pt.id = ob.task_id "
            + "  WHERE sa.del_flag = 0 AND sa.sent_at IS NOT NULL "
            + "  UNION ALL "
            // ④ 预警：按发布时刻归月，一条计 1 条预警
            + "  SELECT pt.station_id AS station_id, DATE_FORMAT(al.raised_at, '%Y-%m') AS month, "
            + "         0 AS task_count, 0 AS obs_count, 0 AS abnormal_count, "
            + "         0 AS sample_count, 0 AS positive_count, 1 AS alert_count "
            + "  FROM t_epi_alert al "
            + "  INNER JOIN t_abnormal_report ar ON ar.id = al.report_id "
            + "  INNER JOIN t_wildlife_obs ob   ON ob.id = ar.obs_id "
            + "  INNER JOIN t_patrol_task pt    ON pt.id = ob.task_id "
            + "  WHERE al.del_flag = 0 AND al.raised_at IS NOT NULL "
            + ") u "
            + "INNER JOIN t_monitor_station s "
            + "        ON s.id = u.station_id AND s.del_flag = 0 AND s.status &lt;&gt; 'CLOSED' "
            + "<where>"
            + "  <if test='stationNo != null and stationNo != \"\"'>"
            + "    AND s.station_no = #{stationNo} "
            + "  </if>"
            // yyyy-MM 零填充，字符串比较与时间先后一致；月份筛在归拢前，只留本月细流
            + "  <if test='monthFrom != null'> AND u.month &gt;= #{monthFrom} </if>"
            + "  <if test='monthTo != null'>   AND u.month &lt;  #{monthTo}   </if>"
            + "</where>"
            + "GROUP BY s.station_no, s.name, u.month "
            + "ORDER BY u.month DESC, s.station_no ASC"
            + "</script>")
    List<StationMonthLedgerRow> selectLedgerRows(@Param("stationNo") String stationNo,
                                                 @Param("monthFrom") String monthFrom,
                                                 @Param("monthTo") String monthTo);
}
