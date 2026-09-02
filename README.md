# NavSahay

**NavSahay** is a vehicle-focused dead-reckoning navigation system designed to provide continuous, reliable positioning during Global Navigation Satellite System (GNSS) signal outages.

---

## Problem

Modern navigation systems rely fundamentally on satellite-based positioning (GNSS/GPS). However, when a vehicle enters GNSS-denied or degraded environments—such as urban canyons with tall buildings, tunnels, underpasses, dense foliage, or areas subject to signal jamming and spoofing—satellite visibility is compromised or completely lost. Without an auxiliary dead-reckoning engine, position tracking fails or freezes, disrupting vehicle guidance, fleet monitoring, and autonomous systems.

---

## SIH Problem Statement

* **ID**: `SIH26168`
* **Title**: *AI-ML based Intelligent Dead Reckoning system for seamless navigation*
* **Organization**: *ISRO / Department of Space*
* **Scope Note**: NavSahay is currently an active multi-phase engineering project. Phase 1 establishes the validated kinematic dead-reckoning baseline and offline demonstration prototype. It does not yet claim to satisfy all requirements of the complete SIH problem statement.

---

## Current Status

* **Phase 1 — COMPLETE & VALIDATED**: Vehicle kinematic dead-reckoning pipeline validated on real vehicle dataset (`IO-VNBD`), audited to verify that ground-truth position is not used during outage propagation, and demonstrated via an offline native Android replay application running on a physical Android phone.
* **Phase 2 — NEXT**: Smartphone IMU integration, dataset collection, and AI/ML forward velocity estimation.
* **Later Phases (3–7) — PLANNED**: Real-time smartphone sensor fusion, vehicle constraints, map matching, GNSS+INS outage recovery, and edge engine hardening.

---

## What We Have Built (Phase 1 Architecture)

The Phase 1 implementation comprises two decoupled, validated components:

```
[ IO-VNBD Vehicle Dataset (V-S1.csv) ]
                  │
                  ▼
   [ Data Loading & Metric Projection ]
                  │
                  ▼
[ 4-State Kinematic EKF Dead-Reckoning ]  ◄─── [ Indicated Speed (v) + Yaw Rate (ω) ]
                  │
                  ▼
       [ Simulated GNSS Outage ]         ─── (GNSS position update masked t=5.0s → 20.0s)
                  │
                  ▼
      [ Trajectory & Error Evaluation ]  ◄─── [ Ground-Truth Position (evaluation only) ]
                  │
                  ▼
        [ demo_route.json ]              ─── (250 validated timesteps exported)
                  │
                  ▼
  [ Android Deterministic Replay Engine ] ─── (Native Kotlin Canvas App on Physical Phone)
```

### Estimator Inputs & Propagation:
* **State Vector**: $\mathbf{x}_k = [x_k, y_k, v_k, \psi_k]^T$ (Local East $x$, North $y$, forward speed $v$, mathematical heading $\psi$).
* **Propagation Inputs**:
  * Indicated Vehicle Speed ($v$ in $\text{m/s}$, converted from $\text{km/h}$)
  * Yaw Gyro Rate ($\omega$ in $\text{rad/s}$, converted from $\text{deg/s}$)
  * Timestep ($\Delta t = 0.1\text{ s}$, $10\text{ Hz}$)
  * Initial Heading ($\psi_0$, initialized from pre-outage GPS heading)
* **Measurement Update**: GNSS $(x, y)$ positions are incorporated **only** when GNSS is available ($t < 5.0\text{ s}$ and $t > 20.0\text{ s}$).
* **Outage Behavior**: During the simulated 15.0 s outage ($t = 5.0\text{ s} \to 20.0\text{ s}$), GNSS position measurements are completely excluded; state propagation is driven solely by vehicle speed and gyro yaw rate.
* **Ground-Truth Data**: Used exclusively for post-hoc error computation and verification; zero ground-truth data is accessible to the estimator during outage propagation.

> **Important Technical Distinction**: The current Phase 1 estimator uses vehicle wheel/ECU indicated speed and vehicle yaw rate from the IO-VNBD dataset. It is **not** a smartphone-IMU dead-reckoning engine yet. The Android app is a **deterministic replay** of this validated experiment, not a live sensor navigation engine.

---

## Phase 1 Dataset & Attribution

Phase 1 of NavSahay uses trial data from the open **IO-VNBD** (Input-Output Vehicle Navigation Benchmark Dataset):

> Onyekpe, U., Palade, V., Kanarachos, S., & Szkolnik, A. (2021).  
> "IO-VNBD: Inertial and Odometry benchmark dataset for ground vehicle positioning."  
> *Data in Brief*, 35, 106885. [https://doi.org/10.1016/j.dib.2021.106885](https://doi.org/10.1016/j.dib.2021.106885)

* **Upstream Repository**: [https://github.com/onyekpeu/IO-VNBD](https://github.com/onyekpeu/IO-VNBD)
* **License**: [Creative Commons Attribution 4.0 International (CC BY 4.0)](https://creativecommons.org/licenses/by/4.0/)
* **Included Sequence**: `python/data/V-S1.csv` (10.0 MB, 51,747 rows, sampled at verified $10.0\text{ Hz}$) is an unedited trial sequence from the upstream dataset recorded from a research vehicle's CAN bus and dual-frequency reference GPS in Coventry, UK.
* **Disclaimer**: NavSahay does not claim ownership of the dataset.

---

## Phase 1 Experiment & Results

The validated experiment evaluates a continuous 25.0-second turning maneuver containing a controlled 15.0-second GNSS outage:

* **Selected Route Window**: Rows 1600–1850 (250 samples, $t = 33029.0\text{ s} \to 33053.9\text{ s}$)
* **Total Route Distance**: $268.89\text{ m}$
* **Simulated GNSS Outage**: $t = 5.0\text{ s} \to 20.0\text{ s}$ ($15.0\text{ s}$ elapsed duration)
* **Distance Travelled During Outage**: $164.0\text{ m}$
* **Outage RMSE**: **$3.361\text{ m}$**
* **Maximum Outage Error**: **$5.277\text{ m}$**
* **Final Outage Error**: **$5.277\text{ m}$**
* **Overall Route RMSE**: **$2.614\text{ m}$**
* **Measured Outage Drift**: **$3.22\%$** (calculated as $\frac{\text{Final Error}}{\text{Outage Distance}} = \frac{5.277\text{ m}}{164.0\text{ m}}$)
* **Naive Dead-Reckoning Baseline**: Final Error $\approx 9.12\text{ m}$ (Outage RMSE: $5.482\text{ m}$)

> **Note on Generalization**: On this specific validated IO-VNBD experiment, the measured drift was $3.22\%$ over the $164\text{ m}$ simulated GNSS outage. This is a single controlled experiment and does not establish generalized performance across all vehicle dynamics, environmental conditions, outage durations, or sensor configurations.

---

## Phase 1 Algorithm Audit & Ablation Tests

To verify that the estimator genuinely tracks vehicle motion and that ground-truth position is not used during outage propagation, five controlled ablation tests were conducted:

| Test ID | Condition | Outage RMSE | Final Error | Key Observation |
| :--- | :--- | :--- | :--- | :--- |
| **Test A** | Normal EKF DR (Validated) | **$3.361\text{ m}$** | **$5.277\text{ m}$** | Accurate tracking through curved maneuver |
| **Test B** | Zero Yaw Rate ($\omega = 0$ during outage) | **$43.910\text{ m}$** | **$89.727\text{ m}$** | Estimator projects straight line; proves strong yaw dependence |
| **Test C** | Zero Speed ($v = 0$ during outage) | **$86.189\text{ m}$** | **$156.311\text{ m}$** | Estimator remains stationary; proves speed propagation dependence |
| **Test D** | Perturbed Yaw Rate ($\pm 0.05\text{ rad/s}$ noise) | **$13.650\text{ m}$** | **$27.689\text{ m}$** | Systematic angular drift; confirms realistic sensitivity to gyro bias |
| **Baseline** | Naive Kinematic DR (No EKF) | **$5.482\text{ m}$** | **$9.120\text{ m}$** | Higher drift than EKF |

### What These Tests Confirm:
1. Position tracking depends directly on vehicle speed and gyro yaw rate inputs.
2. Trajectory degrades predictably when heading dynamics are corrupted or removed.
3. EKF covariance propagation outperforms naive forward integration.
4. Ground-truth position coordinates are strictly not used during the GNSS outage.

---

## Android Replay Prototype

The Android application in `android/NavSahay/` provides a native, judge-ready visual demonstration of the Phase 1 experiment:

* **Platform & Stack**: Native Kotlin, Android SDK 34 (Android 14), Min SDK 26 (Android 8.0).
* **Data Source**: Bundled offline asset `assets/demo_route.json` (250 timesteps).
* **Visualization Engine**: Custom 2D Canvas vector renderer (`TrajectoryMapView`) plotting:
  * Full reference trajectory (dashed line).
  * Color-coded estimated trajectory trail (Green = GNSS Available, Vivid Red = GNSS Denied).
  * Uncertainty halo scaled dynamically to filter covariance $\pm 1\sigma$ uncertainty.
  * Directional vehicle marker with animated pulse.
* **Telemetry Dashboard**: Live display of vehicle speed ($\text{km/h}$), local position $(X, Y\text{ m})$, instantaneous error ($\text{m}$), filter uncertainty ($\pm\text{m}$), and GNSS availability banner.
* **Interactive Controls**: START, PAUSE, and RESET state machine.
* **Physical Hardware Tested**: Successfully installed and validated on a physical Android device.

> **Clarification**: The current Android demonstration is a deterministic replay of a validated vehicle experiment. It is not yet a live smartphone-IMU navigation system.

---

## Current Limitations

The following capabilities are deliberately not yet implemented in Phase 1:

1. **No Live Smartphone IMU Pipeline**: Android accelerometer/gyroscope sensors are not yet polled or fused in real time.
2. **No AI/ML Velocity Estimation**: Speed is sourced from vehicle dataset telemetry, not an AI forward-speed estimation network.
3. **No Learned IMU Bias Correction**: Deep-learning/statistical IMU bias and noise compensators are not yet integrated.
4. **No Phone-to-Vehicle Frame Alignment**: Automatic transformation from arbitrary phone orientation to vehicle body frame is not implemented.
5. **No Road Vibration / High-Frequency Disturbance Filtering**: Pothole and engine vibration rejection filters are not yet implemented.
6. **No Map Matching**: Road network topology and centerline constraints are not integrated.
7. **No Non-Holonomic Constraints**: Vehicle side-slip and vertical velocity constraints are not yet explicitly enforced.
8. **No Live GNSS Outage Detection**: Real-time signal degradation detection from physical NMEA/GNSS SNR metrics is not yet present.
9. **No Live GNSS Resynchronization**: Real-time phase/frequency relocking is not implemented.
10. **No External High-Rate IMU Interface**: Serial/Bluetooth external $200\text{ Hz}$ IMU ingestion is not implemented.
11. **No Production Edge Inference Engine**: TFLite / ONNX runtime engines are not yet integrated.
12. **Deterministic Replay Scope**: Current Android app replays pre-computed experiment outputs rather than live physical sensor data.

---

## Repository Structure

```
NavSahay/
├── .gitignore                          # Excludes build caches, ephemeral outputs, and temp files
├── README.md                           # Project documentation & Phase 1 status
├── docs/
│   └── ROADMAP.md                      # Development roadmap (Phase 1 through Phase 7) & SIH matrix
├── results/
│   └── phase1/                         # Canonical preserved Phase 1 experimental artifacts
│       ├── demo_route.json             # 250 validated replay timesteps
│       ├── error.png                   # Position error & uncertainty vs time plot
│       ├── metrics.json                # Validated quantitative experiment metrics
│       ├── results.csv                 # Timestep evaluation table
│       └── trajectory.png              # Ground truth vs estimated trajectory plot
├── python/
│   ├── config.yaml                     # Experiment segment, noise, and outage configurations
│   ├── requirements.txt                # Python environment dependencies
│   ├── run_phase1.py                   # Single-command experiment entrypoint
│   ├── data/
│   │   └── V-S1.csv                    # IO-VNBD synchronized vehicle dataset (10.0 MB)
│   ├── src/
│   │   ├── __init__.py
│   │   ├── data_loader.py              # CSV parser, validation & local metric projection
│   │   ├── ekf_dr.py                   # 4-state vehicle kinematic EKF Dead-Reckoning estimator
│   │   └── evaluator.py                # Metrics computation, plotting, and demo_route exporter
│   ├── output/                         # Local execution output directory (untracked / gitignored)
│   └── output_audit/                   # Controlled ablation & algorithmic audit artifacts
│       ├── audit_summary.json          # Metrics across all 5 audit tests
│       └── audit_trajectories.png      # Trajectory visual comparison plot
└── android/
    └── NavSahay/                       # Native Android application
        ├── build.gradle.kts            # Project Gradle build script
        ├── settings.gradle.kts         # Repository settings
        ├── gradle.properties           # AndroidX properties
        ├── gradlew & gradlew.bat       # Gradle wrapper executable
        └── app/
            ├── build.gradle.kts        # Application module build script (SDK 34)
            └── src/main/
                ├── AndroidManifest.xml # App manifest with NavSahay launcher icon
                ├── assets/
                │   └── demo_route.json # Bundled offline replay payload
                ├── java/com/navsahay/app/
                │   ├── data/           # NavigationSample model & ReplayRepository
                │   ├── engine/         # ReplayController state machine
                │   └── ui/             # MainActivity & TrajectoryMapView
                └── res/                # Layouts, themes, colors, and adaptive launcher mipmaps
```

---

## Reproducibility Guide

### 1. Python Experiment Execution
To reproduce the Phase 1 Python experiment and generate all evaluation artifacts:

```bash
cd python
pip install -r requirements.txt
python3 run_phase1.py
```
*Outputs are saved to `python/output/` (and verified identical to `results/phase1/`).*

### 2. Android APK Build
To build the debug APK from source:

```bash
cd android/NavSahay
export JAVA_HOME="/usr/local/opt/openjdk@17"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug
```
*The compiled APK will be generated at `android/NavSahay/app/build/outputs/apk/debug/app-debug.apk`.*

### 3. Install on Physical Android Phone
```bash
adb install -r android/NavSahay/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.navsahay.app/.ui.MainActivity
```

---

## Git Checkpoints

* **`v0.1.0-phase1`**: Validated Phase 1 vehicle dead-reckoning experiment, algorithmic audit, canonical results, and runnable Android replay prototype.
* **`v0.1.1-app-icon`**: Integrated custom NavSahay adaptive and legacy launcher icons across all standard Android mipmap densities.
