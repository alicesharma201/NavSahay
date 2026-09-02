"""
NavSahay Vehicle Dead Reckoning / Extended Kalman Filter (EKF) Engine.

Estimates vehicle trajectory using vehicle kinematic odometry (speed + yaw rate)
and GNSS position observations, with strict GNSS outage masking.
"""

import numpy as np


class VehicleKinematicEKF:
    """
    Extended Kalman Filter for 2D Vehicle Dead Reckoning.

    State representation:
      x = [x, y, v, psi]^T
      - x: East coordinate (meters)
      - y: North coordinate (meters)
      - v: Vehicle longitudinal speed (m/s)
      - psi: Heading angle (radians, counter-clockwise from East)

    Measurements used:
      - Continuous: Vehicle speed v_ecu (m/s), Yaw rate omega (rad/s)
      - Intermittent: GNSS position [z_x, z_y]^T (meters)
    """

    def __init__(self, x0=0.0, y0=0.0, v0=0.0, psi0=0.0,
                 pos_std=2.0, vel_std=1.0, yaw_std=0.1,
                 q_pos=0.02, q_vel=0.1, q_yaw=0.01, r_gnss=2.5):
        # State vector [x, y, v, psi]
        self.x = np.array([x0, y0, v0, psi0], dtype=float)

        # State covariance matrix P (4x4)
        self.P = np.diag([pos_std**2, pos_std**2, vel_std**2, yaw_std**2]).astype(float)

        # Process noise parameters
        self.q_pos = q_pos
        self.q_vel = q_vel
        self.q_yaw = q_yaw

        # Measurement noise covariance R (2x2)
        self.R_gnss = np.diag([r_gnss**2, r_gnss**2]).astype(float)

        # Measurement matrix H for GNSS position (2x4)
        self.H = np.array([
            [1.0, 0.0, 0.0, 0.0],
            [0.0, 1.0, 0.0, 0.0]
        ], dtype=float)

    def predict(self, speed_mps, yaw_rate_radps, dt=0.1):
        """
        Vehicle kinematic Dead Reckoning prediction step.
        Uses vehicle forward speed and yaw rate to propagate state forward.
        """
        # Propagate heading
        psi_prev = self.x[3]
        psi_new = psi_prev + yaw_rate_radps * dt
        # Normalize heading to [-pi, pi]
        psi_new = (psi_new + np.pi) % (2.0 * np.pi) - np.pi

        # Update forward speed
        v_new = speed_mps

        # Propagate position using unicycle kinematics
        x_prev = self.x[0]
        y_prev = self.x[1]
        x_new = x_prev + v_new * np.cos(psi_new) * dt
        y_new = y_prev + v_new * np.sin(psi_new) * dt

        self.x = np.array([x_new, y_new, v_new, psi_new], dtype=float)

        # State transition Jacobian F = df/dx
        F = np.array([
            [1.0, 0.0, np.cos(psi_new) * dt, -v_new * np.sin(psi_new) * dt],
            [0.0, 1.0, np.sin(psi_new) * dt,  v_new * np.cos(psi_new) * dt],
            [0.0, 0.0, 0.0,                  0.0],
            [0.0, 0.0, 0.0,                  1.0]
        ], dtype=float)

        # Process noise covariance Q
        Q = np.diag([
            self.q_pos * dt,
            self.q_pos * dt,
            self.q_vel * dt,
            self.q_yaw * dt
        ])

        # Propagate covariance: P = F * P * F^T + Q
        self.P = F @ self.P @ F.T + Q

    def update_gnss(self, gnss_x, gnss_y):
        """
        GNSS position measurement update step.
        Updates state and contracts covariance matrix P.
        """
        z = np.array([gnss_x, gnss_y], dtype=float)
        
        # Innovation / residual: y = z - H * x
        z_pred = self.H @ self.x
        y = z - z_pred

        # Innovation covariance: S = H * P * H^T + R
        S = self.H @ self.P @ self.H.T + self.R_gnss

        # Kalman gain: K = P * H^T * S^(-1)
        K = self.P @ self.H.T @ np.linalg.inv(S)

        # Update state: x = x + K * y
        self.x = self.x + K @ y
        # Re-normalize heading after correction
        self.x[3] = (self.x[3] + np.pi) % (2.0 * np.pi) - np.pi

        # Update covariance: P = (I - K * H) * P
        I = np.eye(4)
        self.P = (I - K @ self.H) @ self.P

    def get_position_uncertainty(self):
        """
        Computes 1-sigma positional uncertainty (meters):
        sigma_pos = sqrt(P_xx + P_yy)
        """
        var_pos = max(0.0, self.P[0, 0] + self.P[1, 1])
        return np.sqrt(var_pos)


def run_filter_simulation(df_data, outage_start_sec=5.0, outage_end_sec=20.0,
                          config=None):
    """
    Runs the complete filter simulation across the input dataset.

    Args:
      df_data: processed DataFrame from data_loader
      outage_start_sec: relative time (seconds) where GNSS outage begins
      outage_end_sec: relative time (seconds) where GNSS outage ends
      config: optional dictionary with filter noise parameters

    Returns:
      results: dictionary containing:
        - estimated_x, estimated_y
        - estimated_v, estimated_psi
        - uncertainty (std in meters)
        - gnss_status list ('AVAILABLE' or 'DENIED')
        - gnss_measured_x, gnss_measured_y
    """
    n_samples = len(df_data)
    
    # Initialize EKF at the first ground truth measurement
    x0 = df_data['gt_x'].iloc[0]
    y0 = df_data['gt_y'].iloc[0]
    v0 = df_data['speed_mps'].iloc[0]
    psi0 = df_data['math_heading_rad'].iloc[0]

    filter_cfg = config.get('filter', {}) if config else {}
    ekf = VehicleKinematicEKF(
        x0=x0, y0=y0, v0=v0, psi0=psi0,
        pos_std=filter_cfg.get('pos_init_std_m', 2.0),
        vel_std=filter_cfg.get('vel_init_std_mps', 1.0),
        yaw_std=filter_cfg.get('yaw_init_std_rad', 0.1),
        q_pos=filter_cfg.get('q_pos', 0.02),
        q_vel=filter_cfg.get('q_vel', 0.1),
        q_yaw=filter_cfg.get('q_yaw', 0.01),
        r_gnss=filter_cfg.get('r_gnss_m', 2.5)
    )

    est_x = np.zeros(n_samples)
    est_y = np.zeros(n_samples)
    est_v = np.zeros(n_samples)
    est_psi = np.zeros(n_samples)
    uncertainty = np.zeros(n_samples)
    gnss_status = []
    gnss_x = np.zeros(n_samples)
    gnss_y = np.zeros(n_samples)

    # First point is initial state
    est_x[0] = x0
    est_y[0] = y0
    est_v[0] = v0
    est_psi[0] = psi0
    uncertainty[0] = ekf.get_position_uncertainty()
    gnss_status.append('AVAILABLE')
    gnss_x[0] = x0
    gnss_y[0] = y0

    times = df_data['rel_time'].values
    speeds = df_data['speed_mps'].values
    yaw_rates = df_data['yaw_rate_radps'].values
    meas_x = df_data['gt_x'].values
    meas_y = df_data['gt_y'].values

    for i in range(1, n_samples):
        dt = times[i] - times[i - 1]
        t = times[i]

        # 1. PREDICT step (Dead Reckoning using onboard vehicle speed and gyro)
        ekf.predict(speed_mps=speeds[i], yaw_rate_radps=yaw_rates[i], dt=dt)

        # 2. Check GNSS Availability
        is_outage = (t >= outage_start_sec) and (t <= outage_end_sec)

        if is_outage:
            # OUTAGE: GNSS measurement is strictly NOT provided to estimator
            status = 'DENIED'
            gnss_x[i] = np.nan
            gnss_y[i] = np.nan
        else:
            # AVAILABLE: Apply GNSS measurement update
            status = 'AVAILABLE'
            gnss_x[i] = meas_x[i]
            gnss_y[i] = meas_y[i]
            ekf.update_gnss(gnss_x=meas_x[i], gnss_y=meas_y[i])

        # Record estimates
        est_x[i] = ekf.x[0]
        est_y[i] = ekf.x[1]
        est_v[i] = ekf.x[2]
        est_psi[i] = ekf.x[3]
        uncertainty[i] = ekf.get_position_uncertainty()
        gnss_status.append(status)

    return {
        'estimated_x': est_x,
        'estimated_y': est_y,
        'estimated_v': est_v,
        'estimated_psi': est_psi,
        'uncertainty_m': uncertainty,
        'gnss_status': gnss_status,
        'gnss_x': gnss_x,
        'gnss_y': gnss_y
    }
