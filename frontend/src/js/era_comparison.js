import { Chart, registerables } from 'chart.js';

Chart.register(...registerables);

export function initEraComparison(state, CONFIG) {
    const axisSelect = document.getElementById('era-axis-select');
    const refreshBtn = document.getElementById('refresh-era');
    const summaryEl = document.getElementById('era-summary');
    const timelineEl = document.getElementById('era-timeline');
    const insightsEl = document.getElementById('era-insights');
    let frictionChart = null;
    let wearChart = null;
    let precisionChart = null;

    async function loadData() {
        try {
            const axisName = axisSelect.value;
            const response = await fetch(
                `${CONFIG.API_BASE}/instruments/${state.currentInstrumentId}/comparison/cross-era?axisName=${encodeURIComponent(axisName)}`
            );
            if (!response.ok) throw new Error('Failed');

            const data = await response.json();
            renderSummary(data.summary);
            renderTimeline(data.eraData);
            renderCharts(data.eraData);
            renderInsights(data.insights);
        } catch (e) {
            console.error('Failed:', e);
        }
    }

    function renderSummary(summary) {
        summaryEl.innerHTML = `
            <div class="era-stat-card">
                <div class="era-stat-label">摩擦系数降低</div>
                <div class="era-stat-value">${formatNum(summary.frictionCoefficientImprovementRatio, 0)}</div>
                <div class="era-stat-unit">倍</div>
            </div>
            <div class="era-stat-card">
                <div class="era-stat-label">磨损率降低</div>
                <div class="era-stat-value">${formatNum(Math.log10(summary.wearRateImprovementRatio || 1), 1)}</div>
                <div class="era-stat-unit">数量级</div>
            </div>
            <div class="era-stat-card">
                <div class="era-stat-label">精度提升</div>
                <div class="era-stat-value">${formatNum(summary.precisionImprovementRatio, 0)}</div>
                <div class="era-stat-unit">倍</div>
            </div>
            <div class="era-stat-card">
                <div class="era-stat-label">寿命提升</div>
                <div class="era-stat-value">${formatNum(summary.lifetimeImprovementRatio, 0)}</div>
                <div class="era-stat-unit">倍</div>
            </div>
        `;
    }

    function renderTimeline(eraData) {
        timelineEl.innerHTML = eraData.map(era => `
            <div class="era-timeline-item">
                <div class="era-timeline-dot"></div>
                <div class="era-timeline-year">${era.eraYearStart > 0 ? era.eraYearStart : '公元前' + Math.abs(era.eraYearStart)}</div>
                <div class="era-timeline-name">${era.eraName.replace('轴承', '').replace('古代', '')}</div>
            </div>
        `).join('');
    }

    function renderCharts(eraData) {
        const labels = eraData.map(e => e.eraName.replace('轴承', '').replace('古代', ''));
        const colors = ['#ffc107', '#ff9800', '#f44336', '#2196f3', '#4caf50', '#8bc34a'];

        if (frictionChart) frictionChart.destroy();
        frictionChart = new Chart(document.getElementById('era-friction-chart'), {
            type: 'line',
            data: {
                labels,
                datasets: [{
                    label: '摩擦系数',
                    data: eraData.map(e => e.typicalFrictionCoefficient),
                    borderColor: '#f44336',
                    backgroundColor: 'rgba(244,67,54,0.1)',
                    fill: true,
                    tension: 0.4,
                    borderWidth: 3,
                    pointRadius: 6
                }]
            },
            options: chartOptions('摩擦系数')
        });

        if (wearChart) wearChart.destroy();
        wearChart = new Chart(document.getElementById('era-wear-chart'), {
            type: 'line',
            data: {
                labels,
                datasets: [{
                    label: '磨损率 (相对)',
                    data: eraData.map(e => Math.log10(e.typicalWearRate * 1e8 + 1)),
                    borderColor: '#ff9800',
                    backgroundColor: 'rgba(255,152,0,0.1)',
                    fill: true,
                    tension: 0.4,
                    borderWidth: 3,
                    pointRadius: 6
                }]
            },
            options: chartOptions('log(磨损率)')
        });

        if (precisionChart) precisionChart.destroy();
        precisionChart = new Chart(document.getElementById('era-precision-chart'), {
            type: 'bar',
            data: {
                labels,
                datasets: [{
                    label: '跳动量 (μm)',
                    data: eraData.map(e => e.typicalRunoutMicrometers),
                    backgroundColor: colors.map(c => c + '80'),
                    borderColor: colors,
                    borderWidth: 2
                }]
            },
            options: {
                ...chartOptions('跳动量 μm'),
                scales: {
                    y: { type: 'logarithmic', grid: { color: 'rgba(33,150,243,0.1)' }, ticks: { color: '#bbdefb' } },
                    x: { grid: { color: 'rgba(33,150,243,0.1)' }, ticks: { color: '#e3f2fd' } }
                }
            }
        });
    }

    function chartOptions(yLabel) {
        return {
            responsive: true,
            plugins: {
                legend: { labels: { color: '#e3f2fd' } }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    grid: { color: 'rgba(33,150,243,0.1)' },
                    ticks: { color: '#bbdefb' },
                    title: { display: true, text: yLabel, color: '#81d4fa' }
                },
                x: {
                    grid: { color: 'rgba(33,150,243,0.1)' },
                    ticks: { color: '#e3f2fd' }
                }
            }
        };
    }

    function renderInsights(insights) {
        insightsEl.innerHTML = `<h4>💡 关键发现</h4><ul>${
            insights.map(i => `<li>${i}</li>`).join('')
        }</ul>`;
    }

    function formatNum(v, d = 2) {
        if (v === null || v === undefined || isNaN(v) || !isFinite(v)) return '--';
        return Number(v).toFixed(d);
    }

    refreshBtn.addEventListener('click', loadData);
    axisSelect.addEventListener('change', loadData);
    loadData();
}
