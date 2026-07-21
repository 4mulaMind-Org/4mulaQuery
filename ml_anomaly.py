"""
============================================================
4mulaQuery — ML Anomaly Detection Engine
============================================================
Purpose:
    - Query logs analyze karo
    - Slow queries detect karo (Isolation Forest)
    - Health Score calculate karo
    - Predictions + Alerts generate karo
    - Graphs banao
============================================================
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import LabelEncoder
import os
import json
from datetime import datetime

# ── Config ──────────────────────────────────────────────
LOG_FILE    = "data/query_logs.csv"
OUTPUT_DIR  = "assets/ml_analytics"
REPORT_FILE = "data/ml_report.json"

GOLD  = "#d4af37"
DARK  = "#0f1525"
NAVY  = "#1b2a4a"
GREEN = "#2ecc71"
RED   = "#e74c3c"
BLUE  = "#6a9fd8"
TEXT  = "#c8d8f0"

os.makedirs(OUTPUT_DIR, exist_ok=True)

print("=" * 55)
print("   4mulaQuery — ML Anomaly Detection Engine")
print("=" * 55)

# ── Load Data ────────────────────────────────────────────
df = pd.read_csv(LOG_FILE)
df["timestamp"] = pd.to_datetime(df["timestamp"])
df["success"]   = df["success"].map({"true": True, "false": False})
print(f"\n[OK] {len(df)} queries loaded\n")

# ── Feature Engineering ──────────────────────────────────
le = LabelEncoder()
df["type_encoded"] = le.fit_transform(df["type"])
df["success_int"]  = df["success"].fillna(False).astype(int)
df["hour"]         = df["timestamp"].dt.hour

# Features for ML model
features = df[["execution_ms", "type_encoded", "success_int", "hour"]]

# ── Isolation Forest — Anomaly Detection ─────────────────
model = IsolationForest(
    contamination=0.2,   # 20% anomalies expect karo
    random_state=42,
    n_estimators=100
)
df["anomaly_score"] = model.fit_predict(features)
df["is_anomaly"]    = df["anomaly_score"] == -1

# Anomaly score (0-100, higher = more anomalous)
raw_scores = model.score_samples(features)
df["risk_score"] = ((raw_scores - raw_scores.min()) /
                    (raw_scores.max() - raw_scores.min()) * 100)
df["risk_score"] = 100 - df["risk_score"]  # invert: high = risky

# ── Health Score ─────────────────────────────────────────
success_rate = df["success"].fillna(False).mean() * 100
anomaly_rate  = df["is_anomaly"].mean() * 100
avg_exec      = df["execution_ms"].mean()
max_exec      = df["execution_ms"].max()

# Health = 100 - penalties
health = 100
health -= anomaly_rate * 0.5          # anomaly penalty
health -= min(avg_exec / 10, 20)      # speed penalty
health -= (1 - success_rate/100) * 30 # failure penalty
health  = max(0, min(100, health))

# ── Summary ──────────────────────────────────────────────
anomalies  = df[df["is_anomaly"]]
slow_q     = df[df["execution_ms"] > df["execution_ms"].mean() + df["execution_ms"].std()]

print(f"  Total Queries      : {len(df)}")
print(f"  Success Rate       : {success_rate:.1f}%")
print(f"  Avg Exec Time      : {avg_exec:.1f} ms")
print(f"  Max Exec Time      : {max_exec} ms")
print(f"  Anomalies Detected : {len(anomalies)}")
print(f"  Slow Queries       : {len(slow_q)}")
print(f"  Engine Health Score: {health:.1f}/100")
print()

if len(anomalies) > 0:
    print("  [ANOMALIES DETECTED]")
    for _, row in anomalies.iterrows():
        print(f"    ⚠  {row['type']:8} | {row['execution_ms']}ms | risk={row['risk_score']:.0f}")
else:
    print("  [OK] No anomalies detected!")

print()

# ── Plot 1: Execution Timeline with Anomalies ─────────────
fig, ax = plt.subplots(figsize=(12, 5))
fig.patch.set_facecolor(DARK)
ax.set_facecolor(DARK)

normal   = df[~df["is_anomaly"]]
anomaly  = df[df["is_anomaly"]]

ax.plot(df.index, df["execution_ms"],
        color=TEXT, alpha=0.3, linewidth=1, zorder=1)

colors = {t: c for t, c in zip(df["type"].unique(), [GOLD, GREEN, BLUE, RED])}
for t, grp in normal.groupby("type"):
    ax.scatter(grp.index, grp["execution_ms"],
               color=colors.get(t, GOLD), label=t, s=60, zorder=3)

if len(anomaly) > 0:
    ax.scatter(anomaly.index, anomaly["execution_ms"],
               color=RED, marker="X", s=150, zorder=5,
               label="⚠ Anomaly", linewidths=1.5, edgecolors="white")

ax.set_title("Query Execution Timeline — Anomaly Detection",
             color=GOLD, fontsize=14, fontweight="bold", pad=12)
ax.set_xlabel("Query #", color=TEXT)
ax.set_ylabel("Execution Time (ms)", color=TEXT)
ax.tick_params(colors=TEXT)
for s in ax.spines.values(): s.set_edgecolor(NAVY)
ax.legend(facecolor=DARK, edgecolor=GOLD, labelcolor=TEXT, fontsize=9)
ax.axhline(df["execution_ms"].mean(), color=GOLD, linestyle="--",
           alpha=0.5, linewidth=1, label="Mean")

plt.tight_layout()
plt.savefig(f"{OUTPUT_DIR}/1_anomaly_timeline.png", dpi=150, facecolor=DARK)
plt.close()
print("[SAVED] 1_anomaly_timeline.png")

# ── Plot 2: Risk Score per Query ──────────────────────────
fig, ax = plt.subplots(figsize=(10, 4))
fig.patch.set_facecolor(DARK)
ax.set_facecolor(DARK)

bar_colors = [RED if r > 60 else GOLD if r > 30 else GREEN
              for r in df["risk_score"]]
bars = ax.bar(df.index, df["risk_score"], color=bar_colors, width=0.6)

ax.axhline(60, color=RED,  linestyle="--", alpha=0.6, linewidth=1)
ax.axhline(30, color=GOLD, linestyle="--", alpha=0.6, linewidth=1)

ax.set_title("Query Risk Score (ML-based)",
             color=GOLD, fontsize=14, fontweight="bold", pad=12)
ax.set_xlabel("Query #", color=TEXT)
ax.set_ylabel("Risk Score (0-100)", color=TEXT)
ax.tick_params(colors=TEXT)
for s in ax.spines.values(): s.set_edgecolor(NAVY)

patches = [
    mpatches.Patch(color=GREEN, label="Low Risk (<30)"),
    mpatches.Patch(color=GOLD,  label="Medium Risk (30-60)"),
    mpatches.Patch(color=RED,   label="High Risk (>60)"),
]
ax.legend(handles=patches, facecolor=DARK, edgecolor=GOLD,
          labelcolor=TEXT, fontsize=9)

plt.tight_layout()
plt.savefig(f"{OUTPUT_DIR}/2_risk_scores.png", dpi=150, facecolor=DARK)
plt.close()
print("[SAVED] 2_risk_scores.png")

# ── Plot 3: Health Score Gauge ────────────────────────────
fig, ax = plt.subplots(figsize=(6, 6))
fig.patch.set_facecolor(DARK)
ax.set_facecolor(DARK)
ax.set_xlim(-1.5, 1.5)
ax.set_ylim(-0.2, 1.5)
ax.axis("off")

# Background arc
theta_bg = np.linspace(np.pi, 0, 100)
ax.plot(np.cos(theta_bg), np.sin(theta_bg),
        color=NAVY, linewidth=20, solid_capstyle="round")

# Health arc
theta_h = np.linspace(np.pi, np.pi - (np.pi * health / 100), 100)
color_h = GREEN if health >= 70 else GOLD if health >= 40 else RED
ax.plot(np.cos(theta_h), np.sin(theta_h),
        color=color_h, linewidth=20, solid_capstyle="round")

ax.text(0, 0.3, f"{health:.0f}", ha="center", va="center",
        fontsize=52, fontweight="bold", color=color_h)
ax.text(0, 0.05, "/ 100", ha="center", va="center",
        fontsize=18, color=TEXT)
ax.text(0, -0.1, "Engine Health Score", ha="center", va="center",
        fontsize=13, color=GOLD, fontweight="bold")

status = "EXCELLENT" if health >= 80 else "GOOD" if health >= 60 else "WARNING" if health >= 40 else "CRITICAL"
ax.text(0, -0.18, status, ha="center", va="center",
        fontsize=11, color=color_h)

ax.set_title("4mulaQuery — Engine Health",
             color=GOLD, fontsize=14, fontweight="bold", pad=12)

plt.tight_layout()
plt.savefig(f"{OUTPUT_DIR}/3_health_gauge.png", dpi=150, facecolor=DARK)
plt.close()
print("[SAVED] 3_health_gauge.png")

# ── Plot 4: Query Type vs Avg Time (Bar) ─────────────────
fig, ax = plt.subplots(figsize=(8, 5))
fig.patch.set_facecolor(DARK)
ax.set_facecolor(DARK)

type_stats = df.groupby("type")["execution_ms"].agg(["mean", "max", "count"])
x = np.arange(len(type_stats))
w = 0.35

b1 = ax.bar(x - w/2, type_stats["mean"], w,
            label="Avg Time", color=GOLD, alpha=0.9)
b2 = ax.bar(x + w/2, type_stats["max"],  w,
            label="Max Time", color=BLUE, alpha=0.9)

ax.set_title("Query Performance by Type",
             color=GOLD, fontsize=14, fontweight="bold", pad=12)
ax.set_xlabel("Query Type", color=TEXT)
ax.set_ylabel("Execution Time (ms)", color=TEXT)
ax.set_xticks(x)
ax.set_xticklabels(type_stats.index)
ax.tick_params(colors=TEXT)
for s in ax.spines.values(): s.set_edgecolor(NAVY)
ax.legend(facecolor=DARK, edgecolor=GOLD, labelcolor=TEXT)

for bar in b1:
    ax.text(bar.get_x() + bar.get_width()/2,
            bar.get_height() + 0.05,
            f"{bar.get_height():.1f}",
            ha="center", color=TEXT, fontsize=9)

plt.tight_layout()
plt.savefig(f"{OUTPUT_DIR}/4_type_performance.png", dpi=150, facecolor=DARK)
plt.close()
print("[SAVED] 4_type_performance.png")

# ── Save JSON Report ─────────────────────────────────────
report = {
    "generated_at"      : datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    "total_queries"     : int(len(df)),
    "success_rate"      : round(float(success_rate), 2),
    "avg_exec_ms"       : round(float(avg_exec), 2),
    "max_exec_ms"       : int(max_exec),
    "anomalies_detected": int(len(anomalies)),
    "slow_queries"      : int(len(slow_q)),
    "health_score"      : round(float(health), 2),
    "status"            : status,
    "anomaly_details"   : [
        {
            "type"      : row["type"],
            "exec_ms"   : int(row["execution_ms"]),
            "risk_score": round(float(row["risk_score"]), 1),
            "command"   : row["command"]
        }
        for _, row in anomalies.iterrows()
    ]
}

with open(REPORT_FILE, "w") as f:
    json.dump(report, f, indent=2)

print(f"\n[SAVED] ML Report → {REPORT_FILE}")
print(f"\n[DONE] Graphs → {OUTPUT_DIR}/")
print("\n" + "=" * 55)
print(f"  Health Score : {health:.0f}/100 — {status}")
print(f"  Anomalies    : {len(anomalies)} detected")
print(f"  Slow Queries : {len(slow_q)} found")
print("=" * 55)