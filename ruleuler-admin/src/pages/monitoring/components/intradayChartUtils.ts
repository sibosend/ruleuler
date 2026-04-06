import type { IntradayTrendPoint } from '../../../api/monitoring';

export interface ChartDataPoint {
  time: string;
  value: number;
  type: 'target' | 'previous';
  metric: '执行量' | '异常率' | '错误率';
}

/**
 * 将 API 返回数据转换为图表数据格式
 * Feature: monitoring-execution-trend, Requirements 5.1, 5.4
 */
export function transformToChartData(data: IntradayTrendPoint[]): ChartDataPoint[] {
  return data.flatMap(d => [
    { time: d.window_start, value: d.sample_count, type: d.day_type, metric: '执行量' as const },
    { time: d.window_start, value: +(d.anomaly_rate * 100).toFixed(2), type: d.day_type, metric: '异常率' as const },
    { time: d.window_start, value: +(d.error_rate * 100).toFixed(2), type: d.day_type, metric: '错误率' as const },
  ]);
}

/**
 * 从图表数据恢复原始结构（round-trip）
 */
export function restoreFromChartData(chartData: ChartDataPoint[]): IntradayTrendPoint[] {
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
    if (point.metric === '执行量') {
      item.sample_count = point.value;
    } else if (point.metric === '异常率') {
      item.anomaly_rate = point.value / 100;
      item.missing_count = Math.round(item.sample_count * item.anomaly_rate);
    } else if (point.metric === '错误率') {
      item.error_rate = point.value / 100;
      item.error_count = Math.round(item.sample_count * item.error_rate);
    }
  }
  
  return Array.from(grouped.values());
}