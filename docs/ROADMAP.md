# NavSahay Development Roadmap

This document outlines the multi-phase engineering roadmap for **NavSahay** (SIH Problem Statement `SIH26168`: *AI-ML based Intelligent Dead Reckoning system for seamless navigation*), bridging the validated Phase 1 vehicle baseline to the intended final production navigation engine.

---

## Status Legend
* `[COMPLETE]` — Fully implemented, tested, and validated in the current codebase.
* `[NEXT]` — Active next engineering milestone currently being designed/implemented.
* `[PLANNED]` — Formulated architectural milestone with defined technical objectives.
* `[FUTURE]` — Long-term enhancement for post-hackathon / field deployment.

---

## Phase Breakdown

### Phase 1 — Validated Vehicle Kinematic Foundation `[COMPLETE]`
* **Objective**: Establish the software, algorithmic, and evaluation baseline for vehicle dead reckoning under simulated GNSS denial.
* **Implemented Deliverables**:
  * `[COMPLETE]` IO-VNBD benchmark vehicle dataset integration (`V-S1.csv`).
  * `[COMPLETE]` 4-state vehicle kinematic Extended Kalman Filter (EKF) propagating position from indicated speed ($v$) and yaw rate ($\omega$).
  * `[COMPLETE]` Controlled 15.0-second GNSS outage simulation ($t = 5.0\text{ s} \to 20.0\text{ s}$) over a 164.0-meter turning maneuver.
  * `[COMPLETE]` Full evaluation metrics ($3.361\text{ m}$ outage RMSE, $5.277\text{ m}$ max error, $3.22\%$ drift on test segment).
  * `[COMPLETE]` Algorithmic audit and 5 ablation tests verifying that ground-truth position is not used during outage propagation.
  * `[COMPLETE]` Deterministic `demo_route.json` exporter.
  * `[COMPLETE]` Native Kotlin Android application with custom hardware-accelerated 2D Canvas vector rendering (`TrajectoryMapView`).
  * `[COMPLETE]` Successful physical Android phone installation and interactive testing.
  * `[COMPLETE]` Git checkpoints `v0.1.0-phase1` and `v0.1.1-app-icon`.

---

### Phase 2 — Smartphone IMU + AI/ML Velocity Estimation `[NEXT]`
* **Primary Objective**: Investigate whether smartphone inertial sensors (accelerometer/gyroscope) can provide an accurate AI/ML-driven forward vehicle speed estimate that can replace or complement vehicle wheel-speed/ECU telemetry during GNSS outages.
* **Planned Deliverables**:
  * `[NEXT]` Ingest and preprocess smartphone IMU recordings from vehicle driving datasets (e.g., IO-VNBD smartphone sequences / Oxford Inertial Odometry).
  * `[NEXT]` Implement sensor coordinate frame transformations and gravity removal.
  * `[NEXT]` Develop deep learning architectures (e.g., 1D-CNN, Temporal Convolutional Network (TCN), or Bidirectional LSTM) to regress forward vehicle speed from windowed inertial measurements.
  * `[NEXT]` Train and validate models on desktop/cloud environments using cross-validation on unseen driver sequences.
  * `[NEXT]` Quantify velocity error (MAE/RMSE) against vehicle ground truth.
  * `[NEXT]` Convert and quantize trained velocity model to a lightweight edge format (e.g., TensorFlow Lite / ONNX).
  * `[NEXT]` Benchmark learned velocity dead-reckoning accuracy against the Phase 1 vehicle-speed baseline.

---

### Phase 3 — Live Android Sensor Pipeline + Phone-to-Vehicle Alignment `[PLANNED]`
* **Primary Objective**: Transition the Android application from deterministic replay to a live, real-time sensing system capable of continuous orientation and motion tracking.
* **Planned Deliverables**:
  * `[PLANNED]` Implement Android `SensorEventListener` pipeline for high-rate ($100\text{ Hz}$) accelerometer and gyroscope data acquisition.
  * `[PLANNED]` Implement dynamic phone-to-vehicle attitude estimation and frame alignment (Euler/quaternion transformation from arbitrary mount to vehicle forward/lateral/vertical axes).
  * `[PLANNED]` Develop real-time static/zero-velocity detection (ZUPT) and online gyro bias calibration.
  * `[PLANNED]` Implement road vibration, pothole, and high-frequency disturbance rejection filters.
  * `[PLANNED]` Replace replay engine in `MainActivity` with live IMU-driven dead-reckoning updates.

---

### Phase 4 — Advanced Vehicle Kinematic Constraints + Map Matching `[PLANNED]`
* **Primary Objective**: Exploit domain-specific ground vehicle motion properties and road network geometry to constrain unbounded dead-reckoning drift.
* **Planned Deliverables**:
  * `[PLANNED]` Integrate Non-Holonomic Constraints (NHC) enforcing zero lateral side-slip ($v_y \approx 0$) and zero vertical velocity ($v_z \approx 0$) in the vehicle body frame during normal adhesion.
  * `[PLANNED]` Incorporate road network vector topologies (e.g., OpenStreetMap / local road centerline graph).
  * `[PLANNED]` Implement Hidden Markov Model (HMM) or orthogonal projection map-matching filter.
  * `[PLANNED]` Constrain position heading and coordinate drift along valid road segments during extended outages ($> 60\text{ s}$).

---

### Phase 5 — GNSS + INS Multi-Sensor Fusion & Seamless Outage Recovery `[PLANNED]`
* **Primary Objective**: Implement intelligent, automated mode switching between GNSS-aided navigation, autonomous dead reckoning, and smooth state recovery.
* **Planned Deliverables**:
  * `[PLANNED]` Ingest live Android GNSS location, Dilution of Precision (DOP), carrier-to-noise density ($C/N_0$), and satellite constellation metrics.
  * `[PLANNED]` Build real-time GNSS signal anomaly / outage detector (detecting multipath, jamming, tunnel entry).
  * `[PLANNED]` Seamless mode transition:
    * **GNSS Nominal**: Loosely/Tightly-coupled GNSS+INS EKF estimation with online sensor error calibration.
    * **GNSS Degraded/Denied**: Immediate autonomous switch to AI-aided dead reckoning with expanding covariance halos.
    * **GNSS Restored**: Controlled state re-alignment and position convergence without abrupt trajectory jumps.

---

### Phase 6 — Edge Engine Architecture + External IMU Support `[PLANNED]`
* **Primary Objective**: Decouple the core navigation engine from the Android UI into a standalone, modular edge library supporting high-rate external sensors.
* **Planned Deliverables**:
  * `[PLANNED]` Abstract navigation engine into a platform-agnostic library (C++ / Rust / Kotlin multiplatform).
  * `[PLANNED]` Define clean sensor ingestion API supporting external $200\text{ Hz}$ automotive-grade IMUs via Bluetooth Low Energy (BLE), USB-Serial, or CAN bus.
  * `[PLANNED]` Maintain dual-rate processing: high-rate ($100\text{–}200\text{ Hz}$) inertial integration with $10\text{ Hz}$ navigation state outputs.
  * `[PLANNED]` Optimize memory and CPU footprint for low-power edge microcontrollers and Android devices.

---

### Phase 7 — Comprehensive Validation, Benchmarking & Demo Hardening `[PLANNED]`
* **Primary Objective**: Rigorously benchmark NavSahay against SIH26168 evaluation criteria across diverse routes, outage durations, speeds, and driving styles.
* **Planned Deliverables**:
  * `[PLANNED]` Evaluate on extensive benchmark datasets (IO-VNBD full routes, KITTI, KAIST Urban, custom road tests).
  * `[PLANNED]` Benchmark multi-scenario outages: $15\text{ s}$, $30\text{ s}$, $60\text{ s}$, and $120\text{ s}$ durations across city, highway, and underground routes.
  * `[PLANNED]` Measure compute latency, battery consumption, and peak memory overhead.
  * `[PLANNED]` Create judge-ready live physical demonstration and comparison analytics dashboard.

---

## SIH Requirement Traceability Matrix

| SIH26168 Requirement | Status | Planned Phase | Evidence / Artifact |
| :--- | :--- | :--- | :--- |
| **Vehicle GNSS-Denied Dead Reckoning** | `[PARTIAL]` | Phase 1 & 5 | Validated Phase 1 EKF DR (`src/ekf_dr.py`, `results/phase1/`) |
| **Smartphone Accelerometer & Gyroscope Fusion** | `[PLANNED]` | Phase 2 & 3 | Roadmap Phase 2/3 specification |
| **AI/ML Forward Velocity Estimation** | `[NEXT]` | Phase 2 | Planned deep learning velocity model (`docs/ROADMAP.md`) |
| **Learned IMU Error & Bias Correction** | `[PLANNED]` | Phase 2 & 3 | Zero-velocity detection & bias filters |
| **Automatic Phone-to-Vehicle Frame Alignment** | `[PLANNED]` | Phase 3 | Coordinate transformation & attitude estimation |
| **Vibration & Pothole Disturbance Rejection** | `[PLANNED]` | Phase 3 | Digital low-pass & anomaly rejection filter |
| **Non-Holonomic Vehicle Constraints (NHC)** | `[PLANNED]` | Phase 4 | Vehicle zero-sideslip kinematic model |
| **Map Matching & Road Network Constraints** | `[PLANNED]` | Phase 4 | Road topology constraint module |
| **GNSS + INS Fusion & Outage Detection** | `[PLANNED]` | Phase 5 | Real-time SNR/DOP outage detector & EKF mode switcher |
| **Seamless GNSS Recovery & Resynchronization** | `[PLANNED]` | Phase 5 | Controlled covariance re-convergence |
| **Edge-Deployable Navigation Engine** | `[PLANNED]` | Phase 6 | Modular platform-independent navigation core |
| **External High-Rate IMU Support (200 Hz)** | `[PLANNED]` | Phase 6 | Dual-rate serial/BLE sensor ingestion interface |
| **Multi-Scenario Benchmarking & Validation** | `[PLANNED]` | Phase 7 | Multi-dataset benchmark suite |

---

## Final Target Architecture

The long-term target architecture for **NavSahay** is depicted below:

```
┌───────────────────────────────────────────────────────────────────────────┐
│                           RAW SENSOR LAYER                                │
│   ┌──────────────────┐   ┌───────────────────────┐   ┌────────────────┐   │
│   │   Physical GNSS  │   │     Smartphone IMU    │   │  External IMU  │   │
│   │   (NMEA / SNR)   │   │  (Accel / Gyro / Mag) │   │ (CAN/BLE 200Hz)│   │
│   └────────┬─────────┘   └───────────┬───────────┘   └────────┬───────┘   │
└────────────┼─────────────────────────┼────────────────────────┼───────────┘
             │                         ▼                        │
             │             ┌───────────────────────┐            │
             │             │ Sensor Preprocessing  │            │
             │             │ - Calibration & ZUPT  │            │
             │             │ - Vibration Filter    │            │
             │             │ - Frame Alignment     │            │
             │             └───────────┬───────────┘            │
             │                         ▼                        │
             │             ┌───────────────────────┐            │
             │             │  AI/ML Velocity Model │            │
             │             │ (TFLite/Edge Inference)            │
             │             └───────────┬───────────┘            │
             ▼                         ▼                        ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                       CORE NAVIGATION ENGINE                              │
│   ┌───────────────────────────────────────────────────────────────────┐   │
│   │            Intelligent Mode Switcher & Outage Detector            │   │
│   │            (GNSS Nominal ──► Dead Reckoning ──► Recovery)         │   │
│   └──────────────────────────────────┬────────────────────────────────┘   │
│                                      ▼                                    │
│   ┌───────────────────────────────────────────────────────────────────┐   │
│   │           Multi-State Extended Kalman Filter (EKF)                │   │
│   │   - Kinematic Inertial Propagation                                │   │
│   │   - Non-Holonomic Constraints (NHC)                               │   │
│   │   - Map Matching & Road Centerline Constraints                    │   │
│   │   - Dynamic Covariance & Uncertainty Propagation                  │   │
│   └──────────────────────────────────┬────────────────────────────────┘   │
└──────────────────────────────────────┼────────────────────────────────────┘
                                       ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                    CONFIDENCE-AWARE NAVIGATION STATE                      │
│        [ Position (Lat, Lon) | Heading | Speed | Uncertainty (±1σ) ]      │
└──────────────────────────────────────┬────────────────────────────────────┘
                                       ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                APPLICATION / DASHBOARD PRESENTATION LAYER                 │
│         - Native Android UI / Vehicle In-Dash Infotainment Display        │
│         - Real-Time Map Trail, Status Alerts & Telemetry Cards            │
└───────────────────────────────────────────────────────────────────────────┘
```
