import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

export function initVirtualOperation(state, CONFIG) {
    const stateLocal = {
        scene: null,
        camera: null,
        renderer: null,
        controls: null,
        armillaryModel: null,
        animating: false,
        debounceTimer: null
    };

    initThreeScene();
    bindControls();
    runSimulation();

    function initThreeScene() {
        const container = document.getElementById('virtual-three-container');
        if (!container) return;

        const width = container.clientWidth;
        const height = container.clientHeight;

        const scene = new THREE.Scene();
        scene.background = new THREE.Color(0x0c1445);
        scene.fog = new THREE.Fog(0x0c1445, 10, 50);

        const camera = new THREE.PerspectiveCamera(55, width / height, 0.1, 1000);
        camera.position.set(7, 5, 9);

        const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
        renderer.setSize(width, height);
        renderer.setPixelRatio(window.devicePixelRatio);
        renderer.shadowMap.enabled = true;
        container.appendChild(renderer.domElement);

        const controls = new OrbitControls(camera, renderer.domElement);
        controls.enableDamping = true;
        controls.dampingFactor = 0.05;
        controls.minDistance = 3;
        controls.maxDistance = 25;

        scene.add(new THREE.AmbientLight(0xffffff, 0.5));
        const dirLight = new THREE.DirectionalLight(0xffffff, 0.8);
        dirLight.position.set(10, 15, 10);
        dirLight.castShadow = true;
        scene.add(dirLight);
        scene.add(new THREE.DirectionalLight(0x4a90d9, 0.3).position.set(-10, 5, -10));

        stateLocal.scene = scene;
        stateLocal.camera = camera;
        stateLocal.renderer = renderer;
        stateLocal.controls = controls;

        stateLocal.armillaryModel = createVirtualArmillary(scene);
        addStars(scene);

        function animate() {
            requestAnimationFrame(animate);
            controls.update();
            renderer.render(scene, camera);
        }
        animate();

        window.addEventListener('resize', () => {
            const w = container.clientWidth;
            const h = container.clientHeight;
            camera.aspect = w / h;
            camera.updateProjectionMatrix();
            renderer.setSize(w, h);
        });
    }

    function createVirtualArmillary(scene) {
        const group = new THREE.Group();

        const bronze = new THREE.MeshStandardMaterial({
            color: 0xcd7f32, metalness: 0.8, roughness: 0.3
        });
        const gold = new THREE.MeshStandardMaterial({
            color: 0xffd700, metalness: 0.9, roughness: 0.2
        });
        const darkBronze = new THREE.MeshStandardMaterial({
            color: 0x8b4513, metalness: 0.7, roughness: 0.4
        });

        const base = new THREE.Mesh(new THREE.CylinderGeometry(2, 2.2, 0.6, 32), darkBronze);
        base.position.y = -1.8;
        base.castShadow = true;
        group.add(base);

        const pillarMat = darkBronze;
        for (let i = 0; i < 4; i++) {
            const angle = (i / 4) * Math.PI * 2;
            const pillar = new THREE.Mesh(
                new THREE.CylinderGeometry(0.12, 0.12, 2.5, 16), pillarMat);
            pillar.position.set(Math.cos(angle) * 1.4, -0.2, Math.sin(angle) * 1.4);
            group.add(pillar);
        }

        const equatorialRing = new THREE.Mesh(new THREE.TorusGeometry(3.0, 0.08, 16, 64), bronze);
        equatorialRing.rotation.x = Math.PI / 2 - Math.PI * 39.9 / 180;
        group.add(equatorialRing);

        const declinationRing = new THREE.Mesh(new THREE.TorusGeometry(2.2, 0.07, 16, 64), gold);
        group.add(declinationRing);

        const azimuthRing = new THREE.Mesh(new THREE.TorusGeometry(3.2, 0.1, 16, 64), darkBronze);
        azimuthRing.position.y = -1.2;
        group.add(azimuthRing);

        const sightTube = new THREE.Mesh(
            new THREE.CylinderGeometry(0.06, 0.06, 4.5, 16), bronze);
        sightTube.rotation.z = Math.PI / 2;
        group.add(sightTube);

        const rightAscensionRing = new THREE.Mesh(new THREE.TorusGeometry(2.6, 0.06, 16, 64), bronze);
        rightAscensionRing.rotation.y = Math.PI / 2;
        group.add(rightAscensionRing);

        scene.add(group);

        return {
            group,
            equatorialRing,
            declinationRing,
            azimuthRing,
            sightTube,
            rightAscensionRing,
            errorArrow: createErrorArrow(scene)
        };
    }

    function createErrorArrow(scene) {
        const arrowGroup = new THREE.Group();
        const azArrow = new THREE.ArrowHelper(
            new THREE.Vector3(1, 0, 0), new THREE.Vector3(0, 0, 0),
            1, 0xff6b6b, 0.3, 0.15);
        const altArrow = new THREE.ArrowHelper(
            new THREE.Vector3(0, 1, 0), new THREE.Vector3(0, 0, 0),
            1, 0x4ecdc4, 0.3, 0.15);
        arrowGroup.add(azArrow, altArrow);
        arrowGroup.position.set(0, 2.5, 0);
        scene.add(arrowGroup);
        return arrowGroup;
    }

    function addStars(scene) {
        const geom = new THREE.BufferGeometry();
        const positions = [];
        for (let i = 0; i < 1500; i++) {
            const r = 40 + Math.random() * 40;
            const theta = Math.random() * Math.PI * 2;
            const phi = Math.acos(2 * Math.random() - 1);
            positions.push(
                r * Math.sin(phi) * Math.cos(theta),
                r * Math.cos(phi),
                r * Math.sin(phi) * Math.sin(theta)
            );
        }
        geom.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
        const stars = new THREE.Points(geom, new THREE.PointsMaterial({
            color: 0xffffff, size: 0.1, sizeAttenuation: true
        }));
        scene.add(stars);
    }

    function bindControls() {
        const axisMap = {
            equatorial: { slider: 'virtual-equatorial', val: 'virtual-equatorial-val', delta: 10 },
            declination: { slider: 'virtual-declination', val: 'virtual-declination-val', delta: 10 },
            azimuth: { slider: 'virtual-azimuth', val: 'virtual-azimuth-val', delta: 10 },
            altitude: { slider: 'virtual-altitude', val: 'virtual-altitude-val', delta: 10 }
        };

        Object.keys(axisMap).forEach(axis => {
            const config = axisMap[axis];
            const slider = document.getElementById(config.slider);
            const valDisplay = document.getElementById(config.val);

            slider.addEventListener('input', () => {
                valDisplay.textContent = slider.value;
                updateModelRotation();
                scheduleSimulation();
            });

            document.querySelectorAll(`.axis-btn[data-axis="${axis}"]`).forEach(btn => {
                btn.addEventListener('click', () => {
                    const delta = btn.classList.contains('axis-left') ? -config.delta : config.delta;
                    let newVal = parseFloat(slider.value) + delta;
                    const min = parseFloat(slider.min);
                    const max = parseFloat(slider.max);
                    newVal = Math.max(min, Math.min(max, newVal));
                    slider.value = newVal;
                    valDisplay.textContent = newVal;
                    updateModelRotation();
                    scheduleSimulation();
                });
            });
        });

        ['virtual-speed', 'virtual-load', 'virtual-temp',
            'virtual-lubricant', 'virtual-hours'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.addEventListener('change', scheduleSimulation);
        });
    }

    function updateModelRotation() {
        if (!stateLocal.armillaryModel) return;
        const eq = parseFloat(document.getElementById('virtual-equatorial').value);
        const dec = parseFloat(document.getElementById('virtual-declination').value);
        const az = parseFloat(document.getElementById('virtual-azimuth').value);
        const alt = parseFloat(document.getElementById('virtual-altitude').value);

        stateLocal.armillaryModel.equatorialRing.rotation.y = THREE.MathUtils.degToRad(eq);
        stateLocal.armillaryModel.declinationRing.rotation.x = THREE.MathUtils.degToRad(dec);
        stateLocal.armillaryModel.azimuthRing.rotation.y = THREE.MathUtils.degToRad(az);
        stateLocal.armillaryModel.sightTube.rotation.x = THREE.MathUtils.degToRad(-alt);
        stateLocal.armillaryModel.rightAscensionRing.rotation.y = THREE.MathUtils.degToRad(eq);
    }

    function scheduleSimulation() {
        if (stateLocal.debounceTimer) clearTimeout(stateLocal.debounceTimer);
        stateLocal.debounceTimer = setTimeout(runSimulation, 200);
    }

    async function runSimulation() {
        try {
            const request = {
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

            const response = await fetch(
                `${CONFIG.API_BASE}/instruments/${state.currentInstrumentId}/comparison/virtual-operation`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(request)
                }
            );

            if (!response.ok) throw new Error('Simulation failed');
            const result = await response.json();
            updateResults(result);
        } catch (e) {
            console.error('Virtual operation error:', e);
        }
    }

    function updateResults(r) {
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

        const lubEl = document.getElementById('virtual-lubrication');
        lubEl.textContent = r.statusMessages?.lubrication || '--';
        lubEl.className = 'value ' + getStatusClass(r.currentLambdaRatio);

        document.getElementById('virtual-stress').textContent =
            `等级 ${r.stressLevel || 1} / 5`;
        document.getElementById('virtual-wear-total').textContent =
            formatNum(r.cumulativeWearMm, 6) + ' mm';
        document.getElementById('virtual-lifetime').textContent =
            formatNum(r.estimatedTimeToFailureHours, 0) + ' 小时';

        const msgEl = document.getElementById('virtual-status-msg');
        msgEl.textContent =
            `${r.statusMessages?.stress || ''}\n${r.statusMessages?.wear || ''}`;
        msgEl.style.borderLeftColor = r.stressLevel >= 4 ? '#f44336' :
            r.stressLevel >= 3 ? '#ff9800' : '#4caf50';
        msgEl.style.background = r.stressLevel >= 4 ? 'rgba(244,67,54,0.15)' :
            r.stressLevel >= 3 ? 'rgba(255,152,0,0.15)' : 'rgba(76,175,80,0.15)';
        msgEl.style.color = r.stressLevel >= 4 ? '#e57373' :
            r.stressLevel >= 3 ? '#ffb74d' : '#a5d6a7';

        if (stateLocal.armillaryModel?.errorArrow) {
            const scale = Math.min(r.totalPointingErrorArcmin * 30, 3);
            stateLocal.armillaryModel.errorArrow.scale.setScalar(scale || 0.1);
        }
    }

    function getStatusClass(lambda) {
        if (lambda >= 3) return 'status-ok';
        if (lambda >= 1) return 'status-warning';
        return 'status-danger';
    }

    function formatNum(v, d = 2) {
        if (v === null || v === undefined || isNaN(v)) return '--';
        return Number(v).toFixed(d);
    }

    function formatExp(v) {
        if (v === null || v === undefined || isNaN(v)) return '--';
        return Number(v).toExponential(2) + ' m/s';
    }
}
