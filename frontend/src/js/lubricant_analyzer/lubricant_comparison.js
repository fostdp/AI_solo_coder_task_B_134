import { Chart, registerables } from 'chart.js';

Chart.register(...registerables);

export function initLubricantComparison(state, CONFIG) {
    const axisSelect = document.getElementById('lubricant-axis-select');
    const refreshBtn = document.getElementById('refresh-lubricant');
    const conditionsEl = document.getElementById('lubricant-conditions');
    const tableContainer = document.getElementById('lubricant-table-container');
    const historyEl = document.getElementById('lubricant-history');
    let wearChart = null;
    let frictionChart = null;
    let lifetimeChart = null;

    async function loadData() {
        try {
            const axisName = axisSelect.value;
            const response = await fetch(
                `${CONFIG.API_BASE}/instruments/${state.currentInstrumentId}/comparison/lubricants?axisName=${encodeURIComponent(axisName)}`
            );
            if (!response.ok) throw new Error('Failed');

            const data = await response.json();
            renderConditions(data.conditions);
            renderTable(data.lubricantData);
            renderCharts(data.lubricantData);
            renderHistory(data.historicalNotes);
        } catch (e) {
            console.error('Failed:', e);
        }
    }

    function renderConditions(c) {
        conditionsEl.innerHTML = `
            <span><strong>载荷:</strong> ${c.loadKg} kg</span>
            <span><strong>转速:</strong> ${c.rotationalSpeedRpm} rpm</span>
            <span><strong>温度:</strong> ${c.temperatureC} °C</span>
            <span><strong>材料副:</strong> ${c.materialPair}</span>
            <span><strong>表面粗糙度:</strong> Ra ${c.surfaceRoughnessRa} μm</span>
        `;
    }

    function renderTable(data) {
        tableContainer.innerHTML = `
            <table class="data-table">
                <thead>
                    <tr>
                        <th>润滑剂</th>
                        <th>历史可用</th>
                        <th>摩擦系数</th>
                        <th>λ比</th>
                        <th>油膜厚度(μm)</th>
                        <th>磨损率(m/s)</th>
                        <th>1000h磨损(mm)</th>
                        <th>预计寿命(h)</th>
                    </tr>
                </thead>
                <tbody>
                    ${data.map(d => `
                        <tr>
                            <td><strong>${d.displayName}</strong></td>
                            <td>
                                <span class="historical-badge ${d.historicallyAvailable ? 'badge-available' : 'badge-unavailable'}">
                                    ${d.historicallyAvailable ? '✓ 古代可用' : '✗ 近现代'}
                                </span>
                            </td>
                            <td>${formatNum(d.frictionCoefficient, 4)}</td>
                            <td>${formatNum(d.lambdaRatio, 2)}</td>
                            <td>${formatNum(d.filmThicknessUm, 3)}</td>
                            <td>${formatExp(d.wearRateMPerSec)}</td>
                            <td>${formatNum(d.wearAfter1000HoursMm, 4)}</td>
                            <td>${formatNum(d.estimatedLifetimeHours, 0)}</td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        `;
    }

    function renderCharts(data) {
        const labels = data.map(d => d.displayName);
        const colors = data.map(d => d.historicallyAvailable ?
            ['#2196f3', '#4caf50', '#ff9800', '#9c27b0'][Math.floor(Math.random() * 4)] : '#78909c');

        if (wearChart) wearChart.destroy();
        wearChart = new Chart(document.getElementById('lubricant-wear-chart'), {
            type: 'bar',
            data: {
                labels,
                datasets: [{
                    label: '磨损率 (m/s)',
                    data: data.map(d => d.wearRateMPerSec),
                    backgroundColor: colors.map(c => c + '80'),
                    borderColor: colors,
                    borderWidth: 2
                }]
            },
            options: barChartOptions()
        });

        if (frictionChart) frictionChart.destroy();
        frictionChart = new Chart(document.getElementById('lubricant-friction-chart'), {
            type: 'bar',
            data: {
                labels,
                datasets: [{
                    label: '摩擦系数',
                    data: data.map(d => d.frictionCoefficient),
                    backgroundColor: colors.map(c => c + '80'),
                    borderColor: colors,
                    borderWidth: 2
                }]
            },
            options: barChartOptions()
        });

        if (lifetimeChart) lifetimeChart.destroy();
        lifetimeChart = new Chart(document.getElementById('lubricant-lifetime-chart'), {
            type: 'bar',
            data: {
                labels,
                datasets: [{
                    label: '预计使用寿命 (小时)',
                    data: data.map(d => Math.min(d.estimatedLifetimeHours, 100000)),
                    backgroundColor: colors.map(c => c + '80'),
                    borderColor: colors,
                    borderWidth: 2
                }]
            },
            options: {
                ...barChartOptions(),
                scales: {
                    y: {
                        type: 'logarithmic',
                        grid: { color: 'rgba(33,150,243,0.1)' },
                        ticks: { color: '#bbdefb' }
                    },
                    x: { grid: { color: 'rgba(33,150,243,0.1)' }, ticks: { color: '#e3f2fd' } }
                }
            }
        });
    }

    function barChartOptions() {
        return {
            responsive: true,
            plugins: { legend: { display: false } },
            scales: {
                y: { beginAtZero: true, grid: { color: 'rgba(33,150,243,0.1)' }, ticks: { color: '#bbdefb' } },
                x: { grid: { color: 'rgba(33,150,243,0.1)' }, ticks: { color: '#e3f2fd' } }
            }
        };
    }

    function renderHistory(notes) {
        historyEl.innerHTML = `<h4>📜 润滑剂历史沿革</h4><ul>${
            notes.map(n => `<li>${n}</li>`).join('')
        }</ul>`;
    }

    function formatNum(v, d = 2) {
        if (v === null || v === undefined || isNaN(v)) return '--';
        return Number(v).toFixed(d);
    }

    function formatExp(v) {
        if (v === null || v === undefined || isNaN(v)) return '--';
        return Number(v).toExponential(2);
    }

    refreshBtn.addEventListener('click', loadData);
    axisSelect.addEventListener('change', loadData);
    loadData();
}
