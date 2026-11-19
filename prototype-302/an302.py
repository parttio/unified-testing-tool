import csv,statistics as st,sys,random
from collections import defaultdict
random.seed(1)
path,D,U,method=sys.argv[1:5]
exclude=set(sys.argv[5].split(',')) if len(sys.argv)>5 else set()
t={}; ph={}
for row in csv.reader(open(path)):
    _,r,c,m,s,ms=row[:6]; phases=row[6] if len(row)>6 else ''
    if m!=method or s!='SUCCESSFUL': continue
    d={k:float(v) for k,v in (p.split('=') for p in phases.split(';') if p)}
    t[(int(r),c)]=float(ms)-sum(v for k,v in d.items() if k in exclude)
    ph[(int(r),c)]={k:v for k,v in d.items() if k not in exclude}
rounds=sorted({r for r,c in t if r>=5 and (r,D) in t and (r,U) in t})
pr=[t[(r,U)]/t[(r,D)] for r in rounds]
bs=sorted(st.median(random.choices(pr,k=len(pr))) for _ in range(4000))
print(f"{method}: {len(rounds)} rounds; Direct median {st.median(t[(r,D)] for r in rounds):.1f} ms, Unified median {st.median(t[(r,U)] for r in rounds):.1f} ms")
print(f"paired ratio median {st.median(pr):.3f}, IQR {st.quantiles(pr,n=4)[0]:.3f}-{st.quantiles(pr,n=4)[2]:.3f}, bootstrap 95% CI {bs[100]:.3f}-{bs[3900]:.3f}")
keys=sorted({k for r in rounds for k in ph[(r,U)]})
tot=st.median(t[(r,U)] for r in rounds)
print("Unified phases, median ms:")
for k in keys: print(f"  {k:14s} {st.median(ph[(r,U)].get(k,0) for r in rounds):8.2f}")
print(f"  {'other':14s} {st.median(t[(r,U)]-sum(ph[(r,U)].values()) for r in rounds):8.2f}  (test code, Wrapper proxies, User Steps, the tool's own work not inside a phase)")
