import json
from pathlib import Path

file_path = Path(r"c:\Users\waell\Documents\GitHub\FootballData\2005\A\A2005Data.json")
if not file_path.exists():
    print(f"ERROR: file not found: {file_path}")
    raise SystemExit(1)

with open(file_path, 'r', encoding='utf-8') as f:
    data = json.load(f)

teams = data.get('teams', []) if isinstance(data, dict) else []

total = len(teams)
name_en_count = 0
city_en_count = 0
missing_name = []
missing_city = []
missing_both = []

for t in teams:
    tid = t.get('team_id') or '<no-id>'
    name = t.get('name')
    city = t.get('city')

    name_en = None
    if isinstance(name, dict):
        name_en = name.get('en')
    elif isinstance(name, str):
        name_en = name

    city_en = None
    if isinstance(city, dict):
        city_en = city.get('en')
    elif isinstance(city, str):
        city_en = city

    has_name_en = bool(name_en and str(name_en).strip())
    has_city_en = bool(city_en and str(city_en).strip())

    if has_name_en:
        name_en_count += 1
    else:
        missing_name.append(tid)

    if has_city_en:
        city_en_count += 1
    else:
        missing_city.append(tid)

    if (not has_name_en) and (not has_city_en):
        missing_both.append(tid)

print(f"A2005Data teams: {total}")
print(f"Teams with name.en: {name_en_count} ({(name_en_count/total*100) if total else 0:.1f}%)")
print(f"Teams with city.en: {city_en_count} ({(city_en_count/total*100) if total else 0:.1f}%)")
print()
if missing_name:
    print(f"Teams missing name.en ({len(missing_name)}): {', '.join(missing_name[:50])}{'...' if len(missing_name)>50 else ''}")
if missing_city:
    print(f"Teams missing city.en ({len(missing_city)}): {', '.join(missing_city[:50])}{'...' if len(missing_city)>50 else ''}")
if missing_both:
    print(f"Teams missing both name.en and city.en ({len(missing_both)}): {', '.join(missing_both[:50])}{'...' if len(missing_both)>50 else ''}")

# Exit code 0
