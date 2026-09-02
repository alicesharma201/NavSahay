"""
NavSahay Evaluation and Export Module.
Computes evaluation metrics (RMSE, max error, drift %), generates plots,
and exports demo_route.json for Android replay.
"""

import os
import json
import numpy as np
import pandas as pd
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt


def evaluate_results(df_data, filter_results, metadata, config):
    """
    Computes trajectory error metrics, plots results, and exports files.
    """
    output_dir = config.get('output', {}).get('dir', 'output')
    os.makedirs(output_dir, exist_ok=True)

    gt_x = df_data['gt_x'].values
    gt_y = df_data['gt_y'].values
    est_x = filter_results['estimated_x']
    est_y = filter_results['estimated_y']
    uncertainty = filter_results['uncertainty_m']
    gnss_status = filter_results['gnss_status']
    times = df_data['rel_time'].values
    speeds_kmh = df_data['speed_kmh'].values

    # Compute Euclidean position error at each sample
    pos_error = np.sqrt((est_x - gt_x)**2 + (est_y - gt_y)**2)

    # Identify outage mask
    outage_mask = np.array([s == 'DENIED' for s in gnss_status])
    outage_indices = np.where(outage_mask)[0]

    if len(outage_indices) > 0:
        outage_start_idx = outage_indices[0]
        outage_end_idx = outage_indices[-1]
        outage_errors = pos_error[outage_mask]

        outage_rmse = float(np.sqrt(np.mean(outage_errors**2)))
        max_outage_error = float(np.max(outage_errors))
        final_outage_error = float(pos_error[outage_end_idx])

        # Distance travelled during outage
        outage_dx = np.diff(gt_x[outage_indices])
        outage_dy = np.diff(gt_y[outage_indices])
        distance_outage_m = float(np.sum(np.sqrt(outage_dx**2 + outage_dy**2)))

        drift_percentage = float((final_outage_error / distance_outage_m) * 100.0) if distance_outage_m > 0 else 0.0
        outage_duration_s = float(times[outage_end_idx] - times[outage_start_idx])
    else:
        outage_rmse = 0.0
        max_outage_error = 0.0
        final_outage_error = 0.0
        distance_outage_m = 0.0
        drift_percentage = 0.0
        outage_duration_s = 0.0
        outage_start_idx = 0
        outage_end_idx = 0

    # Total distance
    total_dx = np.diff(gt_x)
    total_dy = np.diff(gt_y)
    total_distance_m = float(np.sum(np.sqrt(total_dx**2 + total_dy**2)))

    # Overall RMSE
    overall_rmse = float(np.sqrt(np.mean(pos_error**2)))

    metrics = {
        "source_recording": metadata.get('source_file', 'V-S1.csv'),
        "source_row_range": f"{metadata.get('start_row', 0)} - {metadata.get('end_row', 0)}",
        "source_time_range_sec": f"{metadata.get('start_time_sec', 0.0):.1f} - {metadata.get('end_time_sec', 0.0):.1f}",
        "sampling_rate_hz": 10.0,
        "total_duration_sec": round(float(times[-1] - times[0]), 1),
        "total_distance_travelled_m": round(total_distance_m, 2),
        "outage_start_sec": float(config['outage']['start_time_offset_sec']),
        "outage_end_sec": float(config['outage']['end_time_offset_sec']),
        "outage_duration_sec": round(outage_duration_s, 2),
        "distance_travelled_during_outage_m": round(distance_outage_m, 2),
        "outage_rmse_m": round(outage_rmse, 3),
        "maximum_outage_error_m": round(max_outage_error, 3),
        "final_outage_error_m": round(final_outage_error, 3),
        "drift_percentage": round(drift_percentage, 2),
        "overall_rmse_m": round(overall_rmse, 3)
    }

    # 1. Save metrics.json
    metrics_path = os.path.join(output_dir, 'metrics.json')
    with open(metrics_path, 'w') as f:
        json.dump(metrics, f, indent=2)

    # 2. Save results.csv
    results_df = pd.DataFrame({
        'rel_time_sec': times,
        'timestamp_sec': df_data['timestamp'].values,
        'ground_truth_x_m': gt_x,
        'ground_truth_y_m': gt_y,
        'estimated_x_m': est_x,
        'estimated_y_m': est_y,
        'gnss_x_m': filter_results['gnss_x'],
        'gnss_y_m': filter_results['gnss_y'],
        'speed_kmh': speeds_kmh,
        'position_error_m': pos_error,
        'uncertainty_m': uncertainty,
        'gnss_status': gnss_status
    })
    results_csv_path = os.path.join(output_dir, 'results.csv')
    results_df.to_csv(results_csv_path, index=False)

    # 3. Save demo_route.json for Android Replay
    demo_samples = []
    for i in range(len(times)):
        demo_samples.append({
            'timestamp': round(float(times[i]), 2),
            'estimated_x': round(float(est_x[i]), 3),
            'estimated_y': round(float(est_y[i]), 3),
            'ground_truth_x': round(float(gt_x[i]), 3),
            'ground_truth_y': round(float(gt_y[i]), 3),
            'gnss_x': None if np.isnan(filter_results['gnss_x'][i]) else round(float(filter_results['gnss_x'][i]), 3),
            'gnss_y': None if np.isnan(filter_results['gnss_y'][i]) else round(float(filter_results['gnss_y'][i]), 3),
            'speed': round(float(speeds_kmh[i]), 1),
            'error': round(float(pos_error[i]), 3),
            'uncertainty': round(float(uncertainty[i]), 2),
            'gnss_status': gnss_status[i]
        })
    demo_route_path = os.path.join(output_dir, 'demo_route.json')
    with open(demo_route_path, 'w') as f:
        json.dump(demo_samples, f, indent=2)

    # 4. Generate trajectory.png
    plt.figure(figsize=(9, 7), dpi=150)
    plt.plot(gt_x, gt_y, 'k--', label='Ground Truth (GNSS Ref)', linewidth=2.0, alpha=0.8)
    
    # Plot estimated trajectory split into available vs denied
    avail_mask = np.array([s == 'AVAILABLE' for s in gnss_status])
    plt.plot(est_x[avail_mask], est_y[avail_mask], 'g.', label='Estimated (GNSS Available)', markersize=5)
    plt.plot(est_x[outage_mask], est_y[outage_mask], 'r-', label='Estimated (Dead Reckoning - GNSS Denied)', linewidth=2.5)

    # Outage start and end markers
    if len(outage_indices) > 0:
        plt.scatter([est_x[outage_start_idx]], [est_y[outage_start_idx]], color='blue', s=80, zorder=5, label='Outage Start (t=5s)')
        plt.scatter([est_x[outage_end_idx]], [est_y[outage_end_idx]], color='purple', s=80, zorder=5, label='Outage End / GNSS Recovery (t=20s)')

    plt.title('NavSahay Phase 1: Vehicle Dead-Reckoning Trajectory (IO-VNBD)', fontsize=13, fontweight='bold', pad=12)
    plt.xlabel('East Local Coordinate (meters)', fontsize=11)
    plt.ylabel('North Local Coordinate (meters)', fontsize=11)
    plt.grid(True, linestyle=':', alpha=0.6)
    plt.legend(loc='best', fontsize=9.5)
    plt.axis('equal')
    plt.tight_layout()
    traj_path = os.path.join(output_dir, 'trajectory.png')
    plt.savefig(traj_path)
    plt.close()

    # 5. Generate error.png
    plt.figure(figsize=(10, 5), dpi=150)
    plt.plot(times, pos_error, 'b-', label='Position Error (m)', linewidth=2)
    plt.plot(times, uncertainty, 'm--', label='Estimated Uncertainty (±1σ)', linewidth=1.5)
    
    if len(outage_indices) > 0:
        t_out_start = times[outage_start_idx]
        t_out_end = times[outage_end_idx]
        plt.axvspan(t_out_start, t_out_end, color='red', alpha=0.15, label=f'GNSS Outage Window ({outage_duration_s:.1f}s)')

    plt.title('Position Error and Filter Uncertainty vs Time', fontsize=13, fontweight='bold', pad=12)
    plt.xlabel('Time (seconds)', fontsize=11)
    plt.ylabel('Error (meters)', fontsize=11)
    plt.grid(True, linestyle=':', alpha=0.6)
    plt.legend(loc='upper left', fontsize=10)
    plt.tight_layout()
    error_path = os.path.join(output_dir, 'error.png')
    plt.savefig(error_path)
    plt.close()

    return metrics
