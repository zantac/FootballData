import json
from pathlib import Path

src = Path(r"c:\Users\waell\Documents\GitHub\FootballData\2005\A\A2005Data.json")
targets = [
    Path(r"c:\Users\waell\Documents\GitHub\FootballData\2009\A\A2009Data.json"),
    Path(r"c:\Users\waell\Documents\GitHub\FootballData\2011\A\A2011Data.json"),
]

if not src.exists():
    print('Source not found:', src)
    raise SystemExit(1)

sdata = json.loads(src.read_text(encoding='utf-8'))
smap = {t.get('team_id'): t for t in sdata.get('teams', []) if isinstance(t, dict)}

for dst in targets:
    if not dst.exists():
        print(f"Skipping (not found): {dst}")
        continue
    print(f"Processing: {dst}")
    ddata = json.loads(dst.read_text(encoding='utf-8'))
    dteams = ddata.get('teams', [])
    changed = []
    for t in dteams:
        tid = t.get('team_id')
        if not tid: continue
        src_team = smap.get(tid)
        if not src_team: continue
        src_name = src_team.get('name')
        src_city = src_team.get('city')
        src_name_en = None
        src_city_en = None
        if isinstance(src_name, dict): src_name_en = src_name.get('en')
        elif isinstance(src_name, str): src_name_en = src_name
        if isinstance(src_city, dict): src_city_en = src_city.get('en')
        elif isinstance(src_city, str): src_city_en = src_city

        updated = False
        # name
        cur_name = t.get('name')
        if isinstance(cur_name, dict):
            ar_val = cur_name.get('ar')
        elif isinstance(cur_name, str):
            ar_val = cur_name
        else:
            ar_val = None
        if src_name_en:
            new_name = {}
            if ar_val: new_name['ar'] = ar_val
            else:
                if isinstance(cur_name, dict) and cur_name.get('en'):
                    new_name['ar'] = cur_name.get('en')
                elif isinstance(cur_name, str):
                    new_name['ar'] = cur_name
            new_name['en'] = src_name_en
            if t.get('name') != new_name:
                t['name'] = new_name
                updated = True

        # city
        cur_city = t.get('city')
        if isinstance(cur_city, dict):
            car = cur_city.get('ar')
        elif isinstance(cur_city, str):
            car = cur_city
        else:
            car = None
        if src_city_en:
            new_city = {}
            if car: new_city['ar'] = car
            else:
                if isinstance(cur_city, dict) and cur_city.get('en'):
                    new_city['ar'] = cur_city.get('en')
                elif isinstance(cur_city, str):
                    new_city['ar'] = cur_city
            new_city['en'] = src_city_en
            if t.get('city') != new_city:
                t['city'] = new_city
                updated = True

        if updated:
            changed.append(tid)

    backup = dst.with_suffix(dst.suffix + '.pre2005en.bak')
    backup.write_text(dst.read_text(encoding='utf-8'), encoding='utf-8')
    dst.write_text(json.dumps(ddata, indent=2, ensure_ascii=False), encoding='utf-8')
    print(f"Updated {len(changed)} teams: {', '.join(changed) if changed else '(none)'}")
    print(f"Backup: {backup}\n")
