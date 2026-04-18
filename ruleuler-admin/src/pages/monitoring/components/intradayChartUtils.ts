import i18n from '@/i18n';
import type { IntradayTrendPoint } from '../../../api/monitoring';

export interface ChartDataPoint {
  time: string;
  value: number;
  type: 'target' | 'previous';
  metric: string;
}

/**
 * 将 API 返回数据转换为图表数据格式
 * Feature: monitoring-execution-trend, Requirements 5.1, 5.4
 */
export function transformToChartData(data: IntradayTrendPoint[]): ChartDataPoint[] {
  const execVolume = i18n.t('monitoring.execVolume');
  const outlierRate = i18n.t('monitoring.outlierRate');
  const errorRate = i18n.t('monitoring.errorRate');
  return data.flatMap(d => [
    { time: d.window_start, value: d.sample_count, type: d.day_type, metric: execVolume },
    { time: d.window_start, value: +(d.anomaly_rate * 100).toFixed(2), type: d.day_type, metric: outlierRate },
    { time: d.window_start, value: +(d.error_rate * 100).toFixed(2), type: d.day_type, metric: errorRate },
  ]);
}

/**
 * 从图表数据恢复原始结构（round-trip）
 */
export function restoreFromChartData(chartData: ChartDataPoint[]): IntradayTrendPoint[] {
  const execVolume = i18n.t('monitoring.execVolume');
  const outlierRate = i18n.t('monitoring.outlierRate');
  const errorRate = i18n.t('monitoring.errorRate');
  const grouped = new Map<string, IntradayTrendPoint>();

  for (const point of chartData) {
    const key = `${point.time}-${point.type}`;
    if (!grouped.has(key)) {
      grouped.set(key, {
        window_start: point.time,
        sample_count: 0,
        missing_count: 0,
        error_count: 0,
        anomaly_rate: 0,
        error_rate: 0,
        day_type: point.type,
      });
    }
    const item = grouped.get(key)!;
    if (point.metric === execVolume) {
      item.sample_count = point.value;
    } else if (point.metric === outlierRate) {
      item.anomaly_rate = point.value / 100;
      item.missing_count = Math.round(item.sample_count * item.anomaly_rate);
    } else if (point.metric === errorRate) {
      item.error_rate = point.value / 100;
      item.error_count = Math.round(item.sample_count * item.error_rate);
    }
  }

  return Array.from(grouped.values());
}
