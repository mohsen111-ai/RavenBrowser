"""Reads the speed test's results.

With a pid and a site: reads `logcat -v epoch` and prints the seconds from the test's "go" mark until the page
started loading and until it finished (exit 1 if it hasn't finished yet).
With --summary: reads speed.txt and prints, for each round, both apps' times side by side and their medians.
"""
import re
import statistics
import sys


def page_times(pid, host):
    t0 = start = None
    line_re = re.compile(r"\s*(\d+\.\d+)\s+(\d+)\s+\d+\s+\w\s+(\S+?)\s*:\s(.*)")
    for line in sys.stdin:
        m = line_re.match(line)
        if not m:
            continue
        t, p, tag, msg = float(m[1]), m[2], m[3], m[4]
        if tag == "RavenSpeed" and t0 is None:
            t0 = t
            continue
        if t0 is None or p != pid:
            continue
        if start is None and "GeckoView:PageStart" in msg and host in msg:
            start = t
        elif start is not None and "GeckoView:PageStop" in msg:
            print(f"{start - t0:.1f} {t - t0:.1f}")
            return 0
    return 1


def summary():
    rounds = {}
    opens = {}
    for line in sys.stdin:
        parts = line.split()
        if len(parts) < 4:
            continue
        if parts[0] == "open":
            if parts[3].isdigit():
                opens.setdefault(parts[1], []).append(int(parts[3]))
            continue
        label, app, url = parts[0], parts[1], parts[2]
        try:
            value = (float(parts[3]), float(parts[4]))
        except (ValueError, IndexError):
            value = None  # "- timeout": not finished in about two minutes
        rounds.setdefault(label, {}).setdefault(url, {})[app] = value
    out = []
    # The browsers in the order they first appear (Kepler and Raven, or Raven and its test copies).
    names = []
    for sites in rounds.values():
        for apps in sites.values():
            names += [a for a in apps if a not in names]
    for label, sites in rounds.items():
        out.append(f"== {label}   (seconds until the page finished loading; in brackets: until it started)")
        done = {a: [] for a in names}
        for url, apps in sites.items():
            cells = []
            for app in names:
                if app not in apps:
                    cells.append(f"{app} -")
                    continue
                v = apps[app]
                if v is None:
                    cells.append(f"{app} timeout")
                else:
                    cells.append(f"{app} {v[1]:5.1f} ({v[0]:.1f})")
                    done[app].append(v[1])
            out.append(f"  {url:45} " + "   ".join(cells))
        out.append("  median: " + "  ".join(f"{a} {statistics.median(v):.1f}" if v else f"{a} -" for a, v in done.items()))
    for app, ms in opens.items():
        out.append(f"== opening {app} from scratch: {', '.join(str(m) for m in ms)} ms (median {statistics.median(ms):.0f})")
    print("\n".join(out))


if __name__ == "__main__":
    if sys.argv[1] == "--summary":
        summary()
    else:
        sys.exit(page_times(sys.argv[1], sys.argv[2]))
