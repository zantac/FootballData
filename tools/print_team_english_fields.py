import json
from pathlib import Path
p = Path(r"c:\Users\waell\Documents\GitHub\FootballData\2007\A\A2007Data.json")
if not p.exists():
    print('File not found:', p)
    raise SystemExit(1)

data = json.loads(p.read_text(encoding='utf-8'))
teams = data.get('teams', [])
for t in teams:
    tid = t.get('team_id') or '<no-id>'
    name = t.get('name')
    city = t.get('city')
    name_en = ''
    city_en = ''
    if isinstance(name, dict):
        name_en = name.get('en') or ''
    elif isinstance(name, str):
        name_en = name
    if isinstance(city, dict):
        city_en = city.get('en') or ''
    elif isinstance(city, str):
        city_en = city
    print(f"{tid}: name.en=" + (name_en or '<missing>') + ", city.en=" + (city_en or '<missing>'))
