"""
NavSahay Data Loader for IO-VNBD Dataset.
Loads and preprocesses real vehicle ECU and GPS records.
"""

import pandas as pd
import numpy as np


def load_iovnbd_dataset(csv_path, start_row=1600, end_row=1850):
    """
    Loads a specific slice of the IO-VNBD V-S1.csv vehicle dataset,
    cleans column names, performs unit conversions, and projects
    GPS coordinates to a local metric Cartesian coordinate frame.

    Coordinate Convention:
      - Origin (0, 0): Initial GPS latitude and longitude at start_row
      - +X axis: East (meters)
      - +Y axis: North (meters)
      - Heading psi: Mathematical angle measured counter-clockwise from +X (East).
        Converted from GPS Heading (0 deg = North, clockwise):
        psi = pi/2 - heading_gps_rad

    Returns:
      df_segment: pandas DataFrame with processed columns:
        - timestamp: seconds from start of day
        - rel_time: seconds from segment start (0.0 to T)
        - lat, lon: WGS-84 coordinates
        - gt_x, gt_y: Ground truth local metric position (meters)
        - speed_mps: Vehicle indicated speed (m/s)
        - yaw_rate_radps: Vehicle yaw rate (rad/s, CCW positive)
        - gps_heading_deg: GPS heading (degrees, CW from North)
        - math_heading_rad: Mathematical heading (rad, CCW from East)
      metadata: dictionary with dataset and origin information
    """
    # Read CSV
    df = pd.read_csv(csv_path)
    
    # Strip whitespace from column headers
    df.columns = [c.strip() for c in df.columns]

    # Verify essential columns exist
    required_cols = [
        'Time Since Start of Day (seconds)',
        'Latitude (degrees)',
        'Longitude (degrees)',
        'Indicated Vehicle Speed (km/hr)',
        'Yaw Rate (deg/sec)',
        'Heading (degrees)'
    ]
    for col in required_cols:
        if col not in df.columns:
            raise KeyError(f"Required column '{col}' not found in dataset!")

    # Slice segment
    if end_row > len(df):
        end_row = len(df)
    sub = df.iloc[start_row:end_row].copy().reset_index(drop=True)

    # Reference origin (first point of the segment)
    lat0 = sub['Latitude (degrees)'].iloc[0]
    lon0 = sub['Longitude (degrees)'].iloc[0]
    t0 = sub['Time Since Start of Day (seconds)'].iloc[0]

    # Metric projection constants (WGS-84 Earth radius)
    R_E = 6378137.0
    meters_per_deg_lat = R_E * (np.pi / 180.0)
    meters_per_deg_lon = R_E * (np.pi / 180.0) * np.cos(np.radians(lat0))

    # Compute metric coordinates (meters relative to origin)
    gt_x = (sub['Longitude (degrees)'] - lon0) * meters_per_deg_lon
    gt_y = (sub['Latitude (degrees)'] - lat0) * meters_per_deg_lat

    # Unit conversions
    speed_mps = sub['Indicated Vehicle Speed (km/hr)'] / 3.6
    yaw_rate_radps = sub['Yaw Rate (deg/sec)'] * (np.pi / 180.0)
    
    # Heading conversion: GPS Heading is 0 deg = North, 90 deg = East, clockwise
    # Mathematical angle psi is 0 rad = East, pi/2 rad = North, counter-clockwise
    gps_heading_deg = sub['Heading (degrees)']
    gps_heading_rad = np.radians(gps_heading_deg)
    math_heading_rad = (np.pi / 2.0) - gps_heading_rad
    # Normalize to [-pi, pi]
    math_heading_rad = (math_heading_rad + np.pi) % (2.0 * np.pi) - np.pi

    df_segment = pd.DataFrame({
        'timestamp': sub['Time Since Start of Day (seconds)'],
        'rel_time': sub['Time Since Start of Day (seconds)'] - t0,
        'lat': sub['Latitude (degrees)'],
        'lon': sub['Longitude (degrees)'],
        'gt_x': gt_x,
        'gt_y': gt_y,
        'speed_mps': speed_mps,
        'speed_kmh': sub['Indicated Vehicle Speed (km/hr)'],
        'yaw_rate_radps': yaw_rate_radps,
        'yaw_rate_degps': sub['Yaw Rate (deg/sec)'],
        'gps_heading_deg': gps_heading_deg,
        'math_heading_rad': math_heading_rad
    })

    metadata = {
        'source_file': csv_path,
        'start_row': start_row,
        'end_row': end_row,
        'num_samples': len(df_segment),
        'start_time_sec': float(t0),
        'end_time_sec': float(sub['Time Since Start of Day (seconds)'].iloc[-1]),
        'origin_lat': float(lat0),
        'origin_lon': float(lon0),
        'meters_per_deg_lat': float(meters_per_deg_lat),
        'meters_per_deg_lon': float(meters_per_deg_lon)
    }

    return df_segment, metadata
