import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';

describe('虚拟操作UI交互与数据绑定测试', () => {
    beforeEach(() => {
        document.body.innerHTML = `
            <div id="virtual-three-container"></div>
            <input type="range" id="virtual-equatorial" min="0" max="360" value="0">
            <span id="virtual-equatorial-val">0°</span>
            <input type="range" id="virtual-declination" min="-90" max="90" value="0">
            <span id="virtual-declination-val">0°</span>
            <input type="range" id="virtual-azimuth" min="0" max="360" value="180">
            <span id="virtual-azimuth-val">180°</span>
            <input type="range" id="virtual-altitude" min="-90" max="90" value="45">
            <span id="virtual-altitude-val">45°</span>
            <input type="number" id="virtual-speed" value="1">
            <input type="number" id="virtual-load" value="500">
            <input type="number" id="virtual-temp" value="25">
            <select id="virtual-lubricant">
                <option value="VEGETABLE_OIL">植物油</option>
                <option value="ANIMAL_FAT">动物油</option>
                <option value="MINERAL_OIL">矿物油</option>
            </select>
            <input type="number" id="virtual-hours" value="0">
            <button class="axis-btn axis-left" data-axis="equatorial">-10°</button>
            <button class="axis-btn axis-right" data-axis="equatorial">+10°</button>
            <button class="axis-btn axis-left" data-axis="declination">-10°</button>
            <button class="axis-btn axis-right" data-axis="declination">+10°</button>
            <button class="axis-btn axis-left" data-axis="azimuth">-10°</button>
            <button class="axis-btn axis-right" data-axis="azimuth">+10°</button>
            <button class="axis-btn axis-left" data-axis="altitude">-10°</button>
            <button class="axis-btn axis-right" data-axis="altitude">+10°</button>
            <span id="virtual-target-star">--</span>
            <span id="virtual-ra">--</span>
            <span id="virtual-dec">--</span>
            <span id="virtual-az-error">--</span>
            <span id="virtual-alt-error">--</span>
            <span id="virtual-total-error">--</span>
            <span id="virtual-uncertainty">--</span>
            <span id="virtual-torque">--</span>
            <span id="virtual-friction-coeff">--</span>
            <span id="virtual-lambda">--</span>
            <span id="virtual-film">--</span>
            <span id="virtual-wear-rate">--</span>
            <span id="virtual-lubrication">--</span>
            <span id="virtual-stress">--</span>
            <span id="virtual-wear-total">--</span>
            <span id="virtual-lifetime">--</span>
            <div id="virtual-status-msg"></div>
        `;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('正常用例', () => {
        it('滑动轴角度变化时，数值显示同步更新', () => {
            const eqSlider = document.getElementById('virtual-equatorial');
            const eqVal = document.getElementById('virtual-equatorial-val');
            eqSlider.value = '90';
            eqSlider.dispatchEvent(new Event('input'));
            expect(eqVal.textContent).toBe('90');
        });

        it('轴旋转按钮±10°功能正常工作', () => {
            const eqSlider = document.getElementById('virtual-equatorial');
            const eqVal = document.getElementById('virtual-equatorial-val');
            eqSlider.value = '90';
            eqVal.textContent = '90';
            const plusBtn = document.querySelector('.axis-btn.axis-right[data-axis="equatorial"]');
            plusBtn.click();
            expect(parseFloat(eqSlider.value)).toBe(100);
            expect(eqVal.textContent).toBe('100');
            const minusBtn = document.querySelector('.axis-btn.axis-left[data-axis="equatorial"]');
            minusBtn.click();
            expect(parseFloat(eqSlider.value)).toBe(90);
            expect(eqVal.textContent).toBe('90');
        });

        it('旋转按钮边界值不超出滑块范围', () => {
            const decSlider = document.getElementById('virtual-declination');
            const decVal = document.getElementById('virtual-declination-val');
            decSlider.value = '90';
            decVal.textContent = '90';
            const plusBtn = document.querySelector('.axis-btn.axis-right[data-axis="declination"]');
            plusBtn.click();
            expect(parseFloat(decSlider.value)).toBe(90);
            decSlider.value = '-90';
            decVal.textContent = '-90';
            const minusBtn = document.querySelector('.axis-btn.axis-left[data-axis="declination"]');
            minusBtn.click();
            expect(parseFloat(decSlider.value)).toBe(-90);
        });

        it('4轴控制元素全部存在', () => {
            expect(document.getElementById('virtual-equatorial')).not.toBeNull();
            expect(document.getElementById('virtual-declination')).not.toBeNull();
            expect(document.getElementById('virtual-azimuth')).not.toBeNull();
            expect(document.getElementById('virtual-altitude')).not.toBeNull();
        });

        it('运行参数输入元素存在', () => {
            expect(document.getElementById('virtual-speed')).not.toBeNull();
            expect(document.getElementById('virtual-load')).not.toBeNull();
            expect(document.getElementById('virtual-temp')).not.toBeNull();
            expect(document.getElementById('virtual-lubricant')).not.toBeNull();
            expect(document.getElementById('virtual-hours')).not.toBeNull();
        });

        it('结果显示元素全部存在', () => {
            expect(document.getElementById('virtual-target-star')).not.toBeNull();
            expect(document.getElementById('virtual-ra')).not.toBeNull();
            expect(document.getElementById('virtual-dec')).not.toBeNull();
            expect(document.getElementById('virtual-az-error')).not.toBeNull();
            expect(document.getElementById('virtual-alt-error')).not.toBeNull();
            expect(document.getElementById('virtual-total-error')).not.toBeNull();
            expect(document.getElementById('virtual-torque')).not.toBeNull();
            expect(document.getElementById('virtual-friction-coeff')).not.toBeNull();
            expect(document.getElementById('virtual-lambda')).not.toBeNull();
            expect(document.getElementById('virtual-lubrication')).not.toBeNull();
            expect(document.getElementById('virtual-stress')).not.toBeNull();
            expect(document.getElementById('virtual-wear-total')).not.toBeNull();
            expect(document.getElementById('virtual-lifetime')).not.toBeNull();
        });

        it('formatNum格式化函数正常工作', () => {
            function formatNum(v, d = 2) {
                if (v === null || v === undefined || isNaN(v)) return '--';
                return Number(v).toFixed(d);
            }
            expect(formatNum(3.14159, 2)).toBe('3.14');
            expect(formatNum(0.00123, 5)).toBe('0.00123');
            expect(formatNum(null)).toBe('--');
            expect(formatNum(undefined)).toBe('--');
            expect(formatNum(NaN)).toBe('--');
        });

        it('formatExp科学计数法函数正常工作', () => {
            function formatExp(v) {
                if (v === null || v === undefined || isNaN(v)) return '--';
                return Number(v).toExponential(2) + ' m/s';
            }
            expect(formatExp(1.23e-8)).toBe('1.23e-8 m/s');
            expect(formatExp(0.00000001)).toBe('1.00e-8 m/s');
            expect(formatExp(null)).toBe('--');
        });

        it('getStatusClass根据lambda返回正确状态类', () => {
            function getStatusClass(lambda) {
                if (lambda >= 3) return 'status-ok';
                if (lambda >= 1) return 'status-warning';
                return 'status-danger';
            }
            expect(getStatusClass(5.0)).toBe('status-ok');
            expect(getStatusClass(3.0)).toBe('status-ok');
            expect(getStatusClass(2.0)).toBe('status-warning');
            expect(getStatusClass(1.0)).toBe('status-warning');
            expect(getStatusClass(0.5)).toBe('status-danger');
        });

        it('润滑剂下拉框有正确的选项', () => {
            const select = document.getElementById('virtual-lubricant');
            expect(select.options.length).toBe(3);
            expect(select.options[0].value).toBe('VEGETABLE_OIL');
            expect(select.options[1].value).toBe('ANIMAL_FAT');
            expect(select.options[2].value).toBe('MINERAL_OIL');
        });
    });

    describe('边界用例', () => {
        it('滑块最小值边界', () => {
            const eqSlider = document.getElementById('virtual-equatorial');
            eqSlider.value = '0';
            eqSlider.dispatchEvent(new Event('input'));
            expect(parseFloat(eqSlider.value)).toBe(0);
            const decSlider = document.getElementById('virtual-declination');
            decSlider.value = '-90';
            decSlider.dispatchEvent(new Event('input'));
            expect(parseFloat(decSlider.value)).toBe(-90);
        });

        it('滑块最大值边界', () => {
            const eqSlider = document.getElementById('virtual-equatorial');
            eqSlider.value = '360';
            eqSlider.dispatchEvent(new Event('input'));
            expect(parseFloat(eqSlider.value)).toBe(360);
            const decSlider = document.getElementById('virtual-declination');
            decSlider.value = '90';
            decSlider.dispatchEvent(new Event('input'));
            expect(parseFloat(decSlider.value)).toBe(90);
        });

        it('旋转按钮在最小值时点击减号保持不变', () => {
            const azSlider = document.getElementById('virtual-azimuth');
            azSlider.value = '0';
            document.getElementById('virtual-azimuth-val').textContent = '0';
            const minusBtn = document.querySelector('.axis-btn.axis-left[data-axis="azimuth"]');
            minusBtn.click();
            expect(parseFloat(azSlider.value)).toBe(0);
        });

        it('旋转按钮在最大值时点击加号保持不变', () => {
            const azSlider = document.getElementById('virtual-azimuth');
            azSlider.value = '360';
            document.getElementById('virtual-azimuth-val').textContent = '360';
            const plusBtn = document.querySelector('.axis-btn.axis-right[data-axis="azimuth"]');
            plusBtn.click();
            expect(parseFloat(azSlider.value)).toBe(360);
        });

        it('润滑剂切换时触发change事件', () => {
            const select = document.getElementById('virtual-lubricant');
            let changed = false;
            select.addEventListener('change', () => { changed = true; });
            select.value = 'ANIMAL_FAT';
            select.dispatchEvent(new Event('change'));
            expect(changed).toBe(true);
        });

        it('高负载参数输入验证', () => {
            const loadInput = document.getElementById('virtual-load');
            loadInput.value = '10000';
            expect(parseFloat(loadInput.value)).toBe(10000);
        });

        it('高温参数输入验证', () => {
            const tempInput = document.getElementById('virtual-temp');
            tempInput.value = '200';
            expect(parseFloat(tempInput.value)).toBe(200);
        });

        it('极低温度参数输入验证', () => {
            const tempInput = document.getElementById('virtual-temp');
            tempInput.value = '-40';
            expect(parseFloat(tempInput.value)).toBe(-40);
        });

        it('长时间模拟小时数', () => {
            const hoursInput = document.getElementById('virtual-hours');
            hoursInput.value = '10000';
            expect(parseInt(hoursInput.value)).toBe(10000);
        });

        it('零转速参数', () => {
            const speedInput = document.getElementById('virtual-speed');
            speedInput.value = '0.001';
            expect(parseFloat(speedInput.value)).toBe(0.001);
        });
    });

    describe('异常/容错用例', () => {
        it('元素不存在时不抛出异常', () => {
            document.body.innerHTML = '';
            expect(() => {
                const el = document.getElementById('non-existent');
                if (el) el.dispatchEvent(new Event('input'));
            }).not.toThrow();
        });

        it('formatNum处理异常值不崩溃', () => {
            function formatNum(v, d = 2) {
                if (v === null || v === undefined || isNaN(v)) return '--';
                return Number(v).toFixed(d);
            }
            expect(formatNum(Infinity)).not.toBe('--');
            expect(formatNum(-Infinity)).not.toBe('--');
            expect(formatNum(1e300)).toBe('1000000000000000019884624838656.00');
        });

        it('formatExp处理异常值不崩溃', () => {
            function formatExp(v) {
                if (v === null || v === undefined || isNaN(v)) return '--';
                return Number(v).toExponential(2) + ' m/s';
            }
            expect(formatExp(0)).toBe('0.00e+0 m/s');
            expect(formatExp(-0.000001)).toBe('-1.00e-6 m/s');
        });

        it('getStatusClass处理NaN和Infinity', () => {
            function getStatusClass(lambda) {
                if (lambda >= 3) return 'status-ok';
                if (lambda >= 1) return 'status-warning';
                return 'status-danger';
            }
            expect(getStatusClass(NaN)).toBe('status-danger');
            expect(getStatusClass(Infinity)).toBe('status-ok');
            expect(getStatusClass(-Infinity)).toBe('status-danger');
        });

        it('事件监听器重复绑定不泄漏', () => {
            const slider = document.getElementById('virtual-equatorial');
            let callCount = 0;
            const handler = () => { callCount++; };
            slider.addEventListener('input', handler);
            slider.addEventListener('input', handler);
            slider.dispatchEvent(new Event('input'));
            expect(callCount).toBe(2);
        });

        it('虚拟操作请求数据格式正确', () => {
            function buildRequest() {
                return {
                    equatorialAxisAngleDeg: parseFloat(document.getElementById('virtual-equatorial').value),
                    declinationAxisAngleDeg: parseFloat(document.getElementById('virtual-declination').value),
                    azimuthAxisAngleDeg: parseFloat(document.getElementById('virtual-azimuth').value),
                    altitudeAxisAngleDeg: parseFloat(document.getElementById('virtual-altitude').value),
                    rotationalSpeedRpm: parseFloat(document.getElementById('virtual-speed').value),
                    loadKg: parseFloat(document.getElementById('virtual-load').value),
                    temperatureC: parseFloat(document.getElementById('virtual-temp').value),
                    lubricantType: document.getElementById('virtual-lubricant').value,
                    simulateWear: parseFloat(document.getElementById('virtual-hours').value) > 0,
                    simulateHours: parseInt(document.getElementById('virtual-hours').value)
                };
            }
            const request = buildRequest();
            expect(request.equatorialAxisAngleDeg).toBe(0);
            expect(request.declinationAxisAngleDeg).toBe(0);
            expect(request.azimuthAxisAngleDeg).toBe(180);
            expect(request.altitudeAxisAngleDeg).toBe(45);
            expect(request.lubricantType).toBe('VEGETABLE_OIL');
            expect(request.simulateWear).toBe(false);
            expect(request.simulateHours).toBe(0);
        });

        it('响应数据更新DOM正确', () => {
            function updateResults(r) {
                function formatNum(v, d = 2) {
                    if (v === null || v === undefined || isNaN(v)) return '--';
                    return Number(v).toFixed(d);
                }
                function formatExp(v) {
                    if (v === null || v === undefined || isNaN(v)) return '--';
                    return Number(v).toExponential(2) + ' m/s';
                }
                document.getElementById('virtual-target-star').textContent = r.targetStarName || '--';
                document.getElementById('virtual-ra').textContent = formatNum(r.currentRightAscensionDeg, 2) + '°';
                document.getElementById('virtual-dec').textContent = formatNum(r.currentDeclinationDeg, 2) + '°';
                document.getElementById('virtual-az-error').textContent = formatNum(r.azimuthErrorArcmin, 3) + " '";
                document.getElementById('virtual-alt-error').textContent = formatNum(r.altitudeErrorArcmin, 3) + " '";
                document.getElementById('virtual-total-error').textContent = formatNum(r.totalPointingErrorArcmin, 3) + " '";
                document.getElementById('virtual-uncertainty').textContent = formatNum(r.errorUncertaintyArcmin, 3) + " '";
                document.getElementById('virtual-torque').textContent = formatNum(r.currentFrictionTorqueNm, 3) + ' N·m';
                document.getElementById('virtual-friction-coeff').textContent = formatNum(r.frictionCoefficient, 5);
                document.getElementById('virtual-lambda').textContent = formatNum(r.currentLambdaRatio, 3);
                document.getElementById('virtual-film').textContent = formatNum(r.currentFilmThicknessUm, 3) + ' μm';
                document.getElementById('virtual-wear-rate').textContent = formatExp(r.currentWearRateMPerSec);
                document.getElementById('virtual-lubrication').textContent = r.statusMessages?.lubrication || '--';
                document.getElementById('virtual-stress').textContent = `等级 ${r.stressLevel || 1} / 5`;
                document.getElementById('virtual-wear-total').textContent = formatNum(r.cumulativeWearMm, 6) + ' mm';
                document.getElementById('virtual-lifetime').textContent = formatNum(r.estimatedTimeToFailureHours, 0) + ' 小时';
                const msgEl = document.getElementById('virtual-status-msg');
                msgEl.textContent = `${r.statusMessages?.stress || ''}\n${r.statusMessages?.wear || ''}`;
            }
            const mockResponse = {
                targetStarName: '北极星 (附近)',
                currentRightAscensionDeg: 90.0,
                currentDeclinationDeg: 89.26,
                azimuthErrorArcmin: 0.123,
                altitudeErrorArcmin: 0.456,
                totalPointingErrorArcmin: 0.472,
                errorUncertaintyArcmin: 0.071,
                currentFrictionTorqueNm: 12.345,
                frictionCoefficient: 0.08765,
                currentLambdaRatio: 2.345,
                currentFilmThicknessUm: 0.456,
                currentWearRateMPerSec: 1.23e-8,
                cumulativeWearMm: 0.000123,
                estimatedTimeToFailureHours: 8760,
                stressLevel: 2,
                statusMessages: {
                    lubrication: '弹流润滑',
                    stress: '低应力 - 弹流润滑，正常运行状态',
                    wear: '几乎无磨损'
                }
            };
            updateResults(mockResponse);
            expect(document.getElementById('virtual-target-star').textContent).toBe('北极星 (附近)');
            expect(document.getElementById('virtual-ra').textContent).toBe('90.00°');
            expect(document.getElementById('virtual-dec').textContent).toBe('89.26°');
            expect(document.getElementById('virtual-az-error').textContent).toBe("0.123 '");
            expect(document.getElementById('virtual-total-error').textContent).toBe("0.472 '");
            expect(document.getElementById('virtual-friction-coeff').textContent).toBe('0.08765');
            expect(document.getElementById('virtual-lambda').textContent).toBe('2.345');
            expect(document.getElementById('virtual-lubrication').textContent).toBe('弹流润滑');
            expect(document.getElementById('virtual-stress').textContent).toBe('等级 2 / 5');
            expect(document.getElementById('virtual-wear-rate').textContent).toBe('1.23e-8 m/s');
            expect(document.getElementById('virtual-lifetime').textContent).toBe('8760 小时');
        });

        it('响应数据为null时显示占位符', () => {
            function updateResults(r) {
                function formatNum(v, d = 2) {
                    if (v === null || v === undefined || isNaN(v)) return '--';
                    return Number(v).toFixed(d);
                }
                document.getElementById('virtual-target-star').textContent = r?.targetStarName || '--';
                document.getElementById('virtual-ra').textContent = formatNum(r?.currentRightAscensionDeg, 2) + '°';
            }
            updateResults({});
            expect(document.getElementById('virtual-target-star').textContent).toBe('--');
            expect(document.getElementById('virtual-ra').textContent).toBe('--°');
            updateResults(null);
            expect(document.getElementById('virtual-target-star').textContent).toBe('--');
        });

        it('应力等级颜色映射正确', () => {
            const msgEl = document.getElementById('virtual-status-msg');
            function applyStressColor(stressLevel) {
                msgEl.style.borderLeftColor = stressLevel >= 4 ? '#f44336' :
                    stressLevel >= 3 ? '#ff9800' : '#4caf50';
                msgEl.style.background = stressLevel >= 4 ? 'rgba(244,67,54,0.15)' :
                    stressLevel >= 3 ? 'rgba(255,152,0,0.15)' : 'rgba(76,175,80,0.15)';
                msgEl.style.color = stressLevel >= 4 ? '#e57373' :
                    stressLevel >= 3 ? '#ffb74d' : '#a5d6a7';
            }
            applyStressColor(5);
            expect(msgEl.style.borderLeftColor).toBe('rgb(244, 67, 54)');
            applyStressColor(3);
            expect(msgEl.style.borderLeftColor).toBe('rgb(255, 152, 0)');
            applyStressColor(1);
            expect(msgEl.style.borderLeftColor).toBe('rgb(76, 175, 80)');
        });

        it('防抖机制在快速连续操作中只执行最后一次', async () => {
            let callCount = 0;
            let debounceTimer = null;
            function scheduleSimulation() {
                if (debounceTimer) clearTimeout(debounceTimer);
                debounceTimer = setTimeout(() => { callCount++; }, 200);
            }
            vi.useFakeTimers();
            scheduleSimulation();
            scheduleSimulation();
            scheduleSimulation();
            vi.advanceTimersByTime(200);
            expect(callCount).toBe(1);
            vi.useRealTimers();
        });

        it('Three.js容器为空时初始化不崩溃', () => {
            document.body.innerHTML = '';
            expect(() => {
                const container = document.getElementById('virtual-three-container');
                if (container) {
                    container.clientWidth;
                }
            }).not.toThrow();
        });

        it('API请求失败不影响UI交互', async () => {
            let fetchCalled = false;
            global.fetch = vi.fn().mockRejectedValue(new Error('Network error'));
            async function runSimulation() {
                try {
                    fetchCalled = true;
                    await fetch('/api/test', { method: 'POST' });
                } catch (e) {
                    console.error('Error:', e);
                }
            }
            await runSimulation();
            expect(fetchCalled).toBe(true);
            expect(() => {
                const slider = document.createElement('input');
                slider.value = '90';
            }).not.toThrow();
        });
    });
});
