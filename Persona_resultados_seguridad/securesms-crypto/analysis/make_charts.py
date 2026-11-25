#!/usr/bin/env python3
"""Builds the report tables (Markdown + CSV) and charts (PNG) from benchmark CSVs.

Usage:  python3 analysis/make_charts.py results/jvm [results/android-<model> ...]
Output: results/report/  (tables.md, figures *.png)
Requires: matplotlib
"""
import csv, math, os, sys
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

PROFILES = ["RSA-2048", "RSA-3072", "ECC-P256", "ECC-P384"]
SEC = {"RSA-2048": 112, "RSA-3072": 128, "ECC-P256": 128, "ECC-P384": 192}
# Validated categorical palette, fixed order (slot 1..4): blue, orange, aqua, yellow.
COLOR = {"RSA-2048": "#2a78d6", "RSA-3072": "#eb6834", "ECC-P256": "#1baf7a", "ECC-P384": "#eda100"}
MARK = {"RSA-2048": "o", "RSA-3072": "s", "ECC-P256": "^", "ECC-P384": "D"}
INK, INK2, GRID, SURF = "#0b0b0b", "#52514e", "#e6e5e0", "#fcfcfb"

plt.rcParams.update({
    "figure.facecolor": SURF, "axes.facecolor": SURF, "axes.edgecolor": GRID,
    "axes.labelcolor": INK2, "xtick.color": INK2, "ytick.color": INK2, "text.color": INK,
    "axes.grid": True, "grid.color": GRID, "grid.linewidth": 0.8, "axes.axisbelow": True,
    "axes.spines.top": False, "axes.spines.right": False, "font.size": 10, "axes.titlesize": 12,
    "axes.titleweight": "bold", "legend.frameon": False,
})

def read(path):
    with open(path, newline="") as f:
        return list(csv.DictReader(f))

def env_of(d):
    p = os.path.join(d, "environment.txt")
    out = {}
    if os.path.exists(p):
        for line in open(p):
            if "=" in line:
                k, v = line.rstrip("\n").split("=", 1); out[k] = v
    return out

def fmt_ms(us):
    ms = float(us) / 1000
    return f"{ms:,.3f}" if ms < 10 else f"{ms:,.1f}"

def ms(x): return float(x) / 1000

def main():
    dirs = sys.argv[1:] or ["results/jvm"]
    out = os.path.join(os.path.dirname(dirs[0].rstrip("/")), "report")
    os.makedirs(out, exist_ok=True)
    runs = []
    for d in dirs:
        t = read(os.path.join(d, "timings.csv")); s = read(os.path.join(d, "sizes.csv")); e = env_of(d)
        label = e.get("device.label") or os.path.basename(d.rstrip("/"))
        runs.append((label, t, s, e))
    md = []
    for d, (label, t, s, e) in zip(dirs, runs):
        md.append(f"## Results: {label}\n")
        md.append(f"Platform: {e.get('platform','?')} · JVM: {e.get('java.vm.name','?')} {e.get('java.version','')} · "
                  f"OS: {e.get('os.name','')} {e.get('os.version','')} {e.get('os.arch','')} · CPU: {e.get('cpu.model','?')} "
                  f"({e.get('cpu.cores','?')} cores)" + (f" · Android SDK {e['android.sdk']}" if 'android.sdk' in e else "") + "\n")
        def row(p, op, chars=""):
            return next(r for r in t if r["profile"] == p and r["operation"] == op and r["message_chars"] == str(chars))
        md.append("**Table A. Key generation and asymmetric operations (ms)**\n")
        md.append("| Profile | Security (bits) | Keygen mean | Keygen median | Keygen SD | Asym. encrypt mean | Asym. encrypt median | Asym. decrypt mean | Asym. decrypt median | n (keygen / ops) |")
        md.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---|")
        for p in PROFILES:
            k, a, b = row(p, "keygen"), row(p, "asym_encrypt"), row(p, "asym_decrypt")
            md.append(f"| {p} | {SEC[p]} | {fmt_ms(k['mean_us'])} | {fmt_ms(k['median_us'])} | {fmt_ms(k['stddev_us'])} | "
                      f"{fmt_ms(a['mean_us'])} | {fmt_ms(a['median_us'])} | {fmt_ms(b['mean_us'])} | {fmt_ms(b['median_us'])} | {k['n']} / {a['n']} |")
        sizes = sorted({int(r["message_chars"]) for r in t if r["message_chars"]})
        for op, title in (("encrypt_full", "B. Full encryption"), ("decrypt_full", "C. Full decryption")):
            md.append(f"\n**Table {title} time, mean ± SD (median) in ms, by message length**\n")
            md.append("| Profile | " + " | ".join(f"{c} chars" for c in sizes) + " |")
            md.append("|---|" + "---:|" * len(sizes))
            for p in PROFILES:
                cells = []
                for c in sizes:
                    r = row(p, op, c)
                    cells.append(f"{fmt_ms(r['mean_us'])} ± {fmt_ms(r['stddev_us'])} ({fmt_ms(r['median_us'])})")
                md.append(f"| {p} | " + " | ".join(cells) + " |")
        md.append("\n**Table D. Ciphertext size and SMS cost** (envelope = header 8 B + key block + plaintext + 16 B tag; SMS sent as Base64 over GSM-7)\n")
        md.append("| Profile | Message (chars) | Envelope (B) | Overhead (B) | Expansion | Base64 chars | SMS plain | SMS encrypted (Base64) | SMS encrypted (8-bit binary) |")
        md.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|")
        for p in PROFILES:
            for r in s:
                if r["profile"] != p: continue
                exp = "n/a" if r["expansion"] in ("NaN", "nan") else f"{float(r['expansion']):.2f}×"
                md.append(f"| {p} | {r['message_chars']} | {r['envelope_bytes']} | {r['overhead_bytes']} | {exp} | {r['base64_chars']} | "
                          f"{r['sms_plain']} | {r['sms_envelope_base64']} | {r['sms_envelope_binary']} |")
        md.append("")
        tag = os.path.basename(d.rstrip("/"))
        charts(t, s, out, tag, label)
    if len(runs) > 1:
        compare(runs, out)
    with open(os.path.join(out, "tables.md"), "w") as f:
        f.write("\n".join(md) + "\n")
    print("Wrote", out)

def direct_label(ax, x, y, text, color_ink=INK2):
    ax.annotate(text, (x, y), xytext=(6, 0), textcoords="offset points", va="center", fontsize=9, color=color_ink)

def charts(t, s, out, tag, label):
    get = lambda p, op, c="": next(r for r in t if r["profile"] == p and r["operation"] == op and r["message_chars"] == str(c))
    # Fig 1: key generation (log scale) with SD error bars
    fig, ax = plt.subplots(figsize=(7, 3.6))
    vals = [ms(get(p, "keygen")["mean_us"]) for p in PROFILES]
    sds = [ms(get(p, "keygen")["stddev_us"]) for p in PROFILES]
    bars = ax.bar(PROFILES, vals, color=[COLOR[p] for p in PROFILES], width=0.55, edgecolor=SURF, linewidth=2,
                  yerr=sds, error_kw=dict(ecolor=INK2, capsize=4, lw=1))
    ax.set_yscale("log"); ax.set_ylabel("Mean time (ms, log scale)"); ax.set_ylim(top=max(v + d for v, d in zip(vals, sds)) * 3)
    ax.set_title(f"Key-pair generation · {label}", loc="left")
    for b, v, sd in zip(bars, vals, sds):
        ax.annotate(f"{v:,.2f} ms" if v < 10 else f"{v:,.0f} ms", (b.get_x() + b.get_width() / 2, v + sd), xytext=(0, 4),
                    textcoords="offset points", ha="center", va="bottom", fontsize=9, color=INK)
    ax.text(0, -0.2, "Error bars: ±1 standard deviation (RSA key generation varies with the random prime search).",
            transform=ax.transAxes, fontsize=8, color=INK2)
    fig.tight_layout(); fig.savefig(os.path.join(out, f"fig1_keygen_{tag}.png"), dpi=200); plt.close(fig)

    # Fig 2: asymmetric operation only, encrypt vs decrypt (two panels, same scale)
    fig, axes = plt.subplots(1, 2, figsize=(9, 3.6), sharey=True)
    for ax, op, title in ((axes[0], "asym_encrypt", "Asymmetric part of encryption"), (axes[1], "asym_decrypt", "Asymmetric part of decryption")):
        v = [ms(get(p, op)["median_us"]) for p in PROFILES]
        bars = ax.bar(PROFILES, v, color=[COLOR[p] for p in PROFILES], width=0.55, edgecolor=SURF, linewidth=2)
        ax.set_title(title, loc="left", fontsize=11)
        for b, x in zip(bars, v):
            ax.annotate(f"{x:.3f}", (b.get_x() + b.get_width() / 2, x), xytext=(0, 3), textcoords="offset points", ha="center", fontsize=9)
        ax.tick_params(axis="x", labelsize=9)
    axes[0].set_ylabel("Median time (ms)")
    fig.suptitle(f"RSA-OAEP vs ECDH (ephemeral) · {label}", x=0.01, ha="left", fontweight="bold")
    fig.tight_layout(); fig.savefig(os.path.join(out, f"fig2_asymmetric_{tag}.png"), dpi=200); plt.close(fig)

    # Fig 3: full encrypt/decrypt vs message length (small multiples, shared y)
    sizes = sorted({int(r["message_chars"]) for r in t if r["message_chars"]})
    fig, axes = plt.subplots(1, 2, figsize=(10, 4), sharey=True)
    for ax, op, title in ((axes[0], "encrypt_full", "Full encryption"), (axes[1], "decrypt_full", "Full decryption")):
        ends = {}
        for p in PROFILES:
            ys = [ms(get(p, op, c)["median_us"]) for c in sizes]
            ax.plot(sizes, ys, color=COLOR[p], lw=2, marker=MARK[p], ms=6, label=p, markeredgecolor=SURF, markeredgewidth=1.5)
            ends[p] = ys[-1]
        # Nudge end labels apart (log axis) so close series do not overlap.
        order = sorted(ends, key=lambda k: ends[k]); pos = {}
        prev = None
        for p in order:
            y = ends[p]
            if prev is not None and y / prev < 1.22: y = prev * 1.22
            pos[p] = y; prev = y
        for p in PROFILES:
            ax.annotate(p, (sizes[-1], pos[p]), xytext=(8, 0), textcoords="offset points", va="center", fontsize=9, color=INK2)
        ax.set_yscale("log"); ax.set_xlabel("Message length (characters)")
        ax.set_title(title, loc="left", fontsize=11); ax.set_xlim(right=sizes[-1] * 1.28)
    axes[0].set_ylabel("Median time (ms, log scale)")
    h, l = axes[0].get_legend_handles_labels()
    fig.legend(h, l, loc="lower center", ncol=4, fontsize=9, bbox_to_anchor=(0.5, 0.0))
    fig.suptitle(f"End-to-end time vs message length · {label}", x=0.01, ha="left", fontweight="bold")
    fig.tight_layout(rect=(0, 0.07, 1, 1)); fig.savefig(os.path.join(out, f"fig3_full_vs_length_{tag}.png"), dpi=200); plt.close(fig)

    # Fig 4: envelope size and SMS segments (two panels)
    fig, axes = plt.subplots(1, 2, figsize=(10, 4))
    chars = sorted({int(r["message_chars"]) for r in s})
    ax = axes[0]
    ax.plot(chars, chars, color=INK2, lw=1.5, ls="--", label="Plaintext")
    direct_label(ax, chars[-1], chars[-1], "Plaintext")
    for p in PROFILES:
        ys = [int(next(r for r in s if r["profile"] == p and r["message_chars"] == str(c))["envelope_bytes"]) for c in chars]
        ax.plot(chars, ys, color=COLOR[p], lw=2, marker=MARK[p], ms=6, label=p, markeredgecolor=SURF, markeredgewidth=1.5)
    ax.set_xlabel("Message length (characters)"); ax.set_ylabel("Bytes"); ax.set_title("Encrypted size (bytes)", loc="left", fontsize=11)
    ax = axes[1]
    focus = [c for c in chars if c in (16, 70, 140, 160, 320)] or chars
    width = 0.8 / (len(PROFILES) + 1)
    xs = range(len(focus))
    plain = [int(next(r for r in s if r["profile"] == PROFILES[0] and r["message_chars"] == str(c))["sms_plain"]) for c in focus]
    ax.bar([x - 0.4 + width / 2 for x in xs], plain, width=width * 0.9, color="#c3c2b7", label="Plain SMS", edgecolor=SURF)
    for i, p in enumerate(PROFILES):
        ys = [int(next(r for r in s if r["profile"] == p and r["message_chars"] == str(c))["sms_envelope_base64"]) for c in focus]
        ax.bar([x - 0.4 + width * (i + 1.5) for x in xs], ys, width=width * 0.9, color=COLOR[p], label=p, edgecolor=SURF)
    ax.set_xticks(list(xs)); ax.set_xticklabels([f"{c}" for c in focus]); ax.set_xlabel("Message length (characters)")
    ax.set_ylabel("SMS segments"); ax.set_title("SMS segments (Base64 over GSM-7)", loc="left", fontsize=11)
    h, l = ax.get_legend_handles_labels()
    fig.legend(h, l, loc="lower center", ncol=5, fontsize=9, bbox_to_anchor=(0.5, 0.0))
    fig.suptitle("Ciphertext size and SMS cost", x=0.01, ha="left", fontweight="bold")
    fig.tight_layout(rect=(0, 0.07, 1, 1)); fig.savefig(os.path.join(out, f"fig4_size_sms_{tag}.png"), dpi=200); plt.close(fig)

    # Fig 5: security level vs cost (decrypt_full 160 chars + keygen)
    fig, ax = plt.subplots(figsize=(7, 4))
    for p in PROFILES:
        x = SEC[p]; y = ms(get(p, "decrypt_full", 160)["median_us"])
        ax.scatter([x], [y], color=COLOR[p], marker=MARK[p], s=80, edgecolor=SURF, linewidth=2, zorder=3)
        direct_label(ax, x, y, f"{p}  ({y:.2f} ms)", INK)
    ax.set_ylim(0, max(ms(get(p, "decrypt_full", 160)["median_us"]) for p in PROFILES) * 1.25)
    ax.set_xlabel("Approximate security strength (bits, NIST SP 800-57)")
    ax.set_ylabel("Median decryption of 160 chars (ms)")
    ax.set_xticks([112, 128, 192]); ax.set_xlim(100, 235)
    ax.set_title("Security strength vs decryption cost", loc="left")
    fig.tight_layout(); fig.savefig(os.path.join(out, f"fig5_security_vs_cost_{tag}.png"), dpi=200); plt.close(fig)

def compare(runs, out):
    """Same operation on PC JVM vs phone(s): median decrypt of 160 chars and keygen."""
    fig, axes = plt.subplots(1, 2, figsize=(10, 4))
    w = 0.8 / len(runs)
    for ax, op, chars, title in ((axes[0], "keygen", "", "Key generation"), (axes[1], "decrypt_full", "160", "Decrypt 160 chars")):
        for i, (label, t, _, _) in enumerate(runs):
            ys = [ms(next(r for r in t if r["profile"] == p and r["operation"] == op and r["message_chars"] == chars)["median_us"]) for p in PROFILES]
            ax.bar([x - 0.4 + w * (i + 0.5) for x in range(4)], ys, width=w * 0.92, label=label,
                   color=["#2a78d6", "#eb6834", "#1baf7a", "#eda100"][i % 4], edgecolor=SURF)
        ax.set_xticks(range(4)); ax.set_xticklabels(PROFILES, fontsize=9); ax.set_yscale("log")
        ax.set_ylabel("Median (ms, log)"); ax.set_title(title, loc="left", fontsize=11)
    axes[0].legend(fontsize=8)
    fig.suptitle("PC JVM vs Android device", x=0.01, ha="left", fontweight="bold")
    fig.tight_layout(); fig.savefig(os.path.join(out, "fig6_platform_comparison.png"), dpi=200); plt.close(fig)

if __name__ == "__main__":
    main()
