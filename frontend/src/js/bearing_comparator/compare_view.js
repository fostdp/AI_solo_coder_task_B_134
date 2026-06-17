import { Chart, registerables } from 'chart.js';

Chart.register(...registerables);

export function initCompareView(state, CONFIG) {
    const container = document.getElementById('instrument-compare-grid');
    const axisSelect = document.getElementById('compare-axis-select');
    const refreshBtn = document.getElementById('refresh-compare');
    let frictionChart = null;
    let pointingChart = null;

    async function loadComparison() {
        try {
            const axisName = axisSelect.value;
            const response = await fetch(
                `${CONFIG.API_BASE}/instruments/${state.currentInstrumentId}/comparison/instruments?axisName=${encodeURIComponent(axisName)}`
            );
            if (!response.ok) throw new Error('Failed to load comparison data');

            const data = await response.json();
            renderInstrumentCards(data);
            renderComparisonCharts(data);
        } catch (e) {
            console.error('Failed to load comparison:', e);
            container.innerHTML = '<p style="color:#e57373;padding:20px;">加载对比数据失败，请检查后端服务</p>';
        }
    }

    function renderInstrumentCards(data) {
        container.innerHTML = data.map(item => {
            const wearPct = item.wearMetrics.wearPercentage;
            const wearColor = wearPct < 20 ? '#81c784' : wearPct < 60 ? '#ffb74d' : '#e57373';

            return `
                <div class="instrument-card">
                    <div class="instrument-card-header">
                        <div>
                            <div class="instrument-card-title">${item.instrumentName}</div>
                            <div style="color:#81d4fa;font-size:12px;margin-top:4px;">
                                ${item.instrumentType?.displayName || item.instrumentType}
                            </div>
                        </div>
                        <span class="instrument-card-era">${item.metadata?.originYear || '---'}年</span>
                    </div>
                    <div class="instrument-card-desc">${item.keyFeatures?.slice(0, 2).join(' | ') || ''}</div>
                    <div class="instrument-card-metrics">
                        <div class="instrument-metric">
                            <span class="instrument-metric-label">摩擦系数</span>
                            <span class="instrument-metric-value">${formatNum(item.frictionMetrics.frictionCoefficient, 4)}</span>
                        </div>
                        <div class="instrument-metric">
                            <span class="instrument-metric-label">λ比</span>
                            <span class="instrument-metric-value">${formatNum(item.frictionMetrics.lambdaRatio, 2)}</span>
                        </div>
                        <div class="instrument-metric">
                            <span class="instrument-metric-label">磨损率</span>
                            <span class="instrument-metric-value">${formatExp(item.wearMetrics.wearRateMPerSec)}</span>
                        </div>
                        <div class="instrument-metric">
                            <span class="instrument-metric-label">磨损占比</span>
                            <span class="instrument-metric-value" style="color:${wearColor}">${formatNum(wearPct, 1)}%</span>
                        </div>
                        <div class="instrument-metric">
                            <span class="instrument-metric-label">总指向误差</span>
                            <span class="instrument-metric-value">${formatNum(item.pointingMetrics.totalPointingErrorArcmin, 3)}'</span>
                        </div>
                        <div class="instrument-metric">
                            <span class="instrument-metric-label">几何误差</span>
                            <span class="instrument-metric-value">${formatNum(item.geometryMetrics.totalGeometricErrorArcsec, 2)}"</span>
                        </div>
                    </div>
                    <ul class="instrument-features">
                        ${item.keyFeatures?.map(f => `<li>${f}</li>`).join('') || ''}
                    </ul>
                </div>
            `;
        }).join('');
    }

    function renderComparisonCharts(data) {
        const labels = data.map(d => d.instrumentName);
        const frictionData = data.map(d => d.frictionMetrics.frictionCoefficient);
        const pointingData = data.map(d => d.pointingMetrics.totalPointingErrorArcmin);
        const colors = ['#2196f3', '#ff9800', '#4caf50', '#f44336', '#9c27b0'];

        if (frictionChart) frictionChart.destroy();
        if (pointingChart) pointingChart.destroy();

        const frictionCtx = document.getElementById('compare-friction-chart').getContext('2d');
        frictionChart = new Chart(frictionCtx, {
            type: 'bar',
            data: {
                labels,
                datasets: [{
                    label: '摩擦系数',
                    data: frictionData,
                    backgroundColor: colors.map(c => c + '80'),
                    borderColor: colors,
                    borderWidth: 2
                }]
            },
            options: {
                responsive: true,
                plugins: { legend: { display: false } },
                scales: {
                    y: { beginAtZero: true, grid: { color: 'rgba(33,150,243,0.1)' }, ticks: { color: '#bbdefb' } },
                    x: { grid: { color: 'rgba(33,150,243,0.1)' }, ticks: { color: '#e3f2fd' } }
                }
            }
        });

        const pointingCtx = document.getElementById('compare-pointing-chart').getContext('2d');
        pointingChart = new Chart(pointingCtx, {
            type: 'bar',
            data: {
                labels,
                datasets: [{
                    label: '总指向误差 (角分)',
                    data: pointingData,
                    backgroundColor: colors.map(c => c + '80'),
                    borderColor: colors,
                    borderWidth: 2
                }]
            },
            options: {
                responsive: true,
                plugins: { legend: { display: false } },
                scales: {
                    y: { beginAtZero: true, grid: { color: 'rgba(33,150,243,0.1)' }, ticks: { color: '#bbdefb' } },
                    x: { grid: { color: 'rgba(33,150,243,0.1)' }, ticks: { color: '#e3f2fd' } }
                }
            }
        });
    }

    function formatNum(v, d = 2) {
        if (v === null || v === undefined || isNaN(v)) return '--';
        return Number(v).toFixed(d);
    }

    function formatExp(v) {
        if (v === null || v === undefined || isNaN(v)) return '--';
        return Number(v).toExponential(2) + ' m/s';
    }

    refreshBtn.addEventListener('click', loadComparison);
    axisSelect.addEventListener('change', loadComparison);

    loadComparison();
}
