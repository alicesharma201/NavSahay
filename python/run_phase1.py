"""
NavSahay Phase 1: Python Experiment Runner.
Executes vehicle dead-reckoning simulation on IO-VNBD dataset,
simulates GNSS outage, evaluates metrics against ground truth,
and exports demo_route.json.
"""

import os
import sys
import yaml
import json

# Add current dir to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from src.data_loader import load_iovnbd_dataset
from src.ekf_dr import run_filter_simulation
from src.evaluator import evaluate_results


def main():
    print("=" * 65)
    print("       NAVSAHAY PHASE 1 - VEHICLE DEAD RECKONING EXPERIMENT")
    print("=" * 65)

    config_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'config.yaml')
    with open(config_path, 'r') as f:
        config = yaml.safe_load(f)

    data_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), config['dataset']['path'])
    start_row = config['segment']['start_row']
    end_row = config['segment']['end_row']
    outage_start = config['outage']['start_time_offset_sec']
    outage_end = config['outage']['end_time_offset_sec']

    print(f"[*] Loading dataset: {data_path}")
    print(f"[*] Segment rows: {start_row} to {end_row}")
    df_data, metadata = load_iovnbd_dataset(data_path, start_row=start_row, end_row=end_row)
    print(f"[+] Loaded {len(df_data)} samples ({metadata.get('num_samples', 0)*0.1:.1f}s) at {config['dataset']['sampling_rate_hz']} Hz")
    print(f"[+] Origin: Lat={metadata['origin_lat']:.7f}, Lon={metadata['origin_lon']:.7f}")

    print(f"[*] Simulating GNSS Outage between t = {outage_start:.1f}s and t = {outage_end:.1f}s...")
    filter_results = run_filter_simulation(
        df_data=df_data,
        outage_start_sec=outage_start,
        outage_end_sec=outage_end,
        config=config
    )
    print("[+] Filter simulation completed successfully.")

    print("[*] Evaluating results against ground truth...")
    metrics = evaluate_results(
        df_data=df_data,
        filter_results=filter_results,
        metadata=metadata,
        config=config
    )

    print("")
    print("=" * 65)
    print("                       EXPERIMENT METRICS")
    print("=" * 65)
    for k, v in metrics.items():
        print(f"  {k:<35}: {v}")
    print("=" * 65)

    # Verify all output files exist
    output_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), config['output']['dir'])
    expected_files = ['trajectory.png', 'error.png', 'results.csv', 'metrics.json', 'demo_route.json']
    print("")
    print("[*] Verifying output artifacts:")
    all_ok = True
    for fname in expected_files:
        fpath = os.path.join(output_dir, fname)
        if os.path.exists(fpath):
            sz = os.path.getsize(fpath)
            print(f"  [OK] {fname:<18} ({sz:,} bytes)")
        else:
            print(f"  [FAILED] {fname} not found!")
            all_ok = False

    if all_ok:
        print("")
        print("[SUCCESS] Phase 1 Python experiment finished cleanly.")
    else:
        print("")
        print("[ERROR] One or more output files were not generated.")
        sys.exit(1)


if __name__ == '__main__':
    main()
