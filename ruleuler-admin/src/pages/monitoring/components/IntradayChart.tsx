import React, { useEffect, useState, useMemo } from 'react';
import { Card, Spin, Row, Col } from 'antd';
import { Line } from '@ant-design/charts';
import { useTranslation } from 'react-i18next';
import { fetchIntradayTrend, type IntradayTrendPoint } from '../../../api/monitoring';
import { transformToChartData, type ChartDataPoint } from './intradayChartUtils';

interface IntradayChartProps {
  project: string;
  packageId: string;
  autoRefresh?: boolean;
  refreshInterval?: number;
}

const IntradayChart: React.FC<IntradayChartProps> = ({
  project,
  packageId,
  autoRefresh = false,
  refreshInterval = 60000,
}) => {
  const { t, i18n } = useTranslation();
  const [data, setData] = useState<IntradayTrendPoint[]>([]);
  const [loading, setLoading] = useState(false);

  const loadData = async () => {
    if (!project || !packageId) return;
    setLoading(true);
    try {
      const result = await fetchIntradayTrend({ project, packageId });
      setData(result || []);
    } catch (e) {
      console.error('fetchIntradayTrend failed', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [project, packageId]);

  useEffect(() => {
    if (!autoRefresh) return;
    const timer = setInterval(loadData, refreshInterval);
    return () => clearInterval(timer);
  }, [autoRefresh, refreshInterval, project, packageId]);

  const chartData = useMemo(() => transformToChartData(data), [data]);

  const execVolumeData = useMemo(
    () => chartData.filter(d => d.metric === t('monitoring.execVolume')),
    [chartData, i18n.language]
  );

  const rateData = useMemo(
    () => chartData.filter(d => d.metric === t('monitoring.outlierRate') || d.metric === t('monitoring.errorRate')),
    [chartData, i18n.language]
  );

  const commonConfig = {
    xField: 'time',
    yField: 'value',
    colorField: 'type',
    height: 180,
    legend: { position: 'top' as const },
    lineStyle: (d: ChartDataPoint) => ({
      lineDash: d.type === 'previous' ? [4, 4] : undefined,
    }),
    smooth: false,
  };

  if (data.length === 0 && !loading) return null;

  return (
    <Spin spinning={loading}>
      <Row gutter={16}>
        <Col span={12}>
          <Card size="small" title={t('monitoring.intradayVolumeChart')}>
            <Line data={execVolumeData} {...commonConfig} />
          </Card>
        </Col>
        <Col span={12}>
          <Card size="small" title={t('monitoring.intradayAnomalyChart')}>
            <Line
              data={rateData}
              {...commonConfig}
              colorField="metric"
              seriesField="type"
            />
          </Card>
        </Col>
      </Row>
    </Spin>
  );
};

export default IntradayChart;
