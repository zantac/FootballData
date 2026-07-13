import json
import re
from pathlib import Path

ARABIC_RE = re.compile(r'[\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF]')
LATIN_RE = re.compile(r'[A-Za-z]')

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
    def classify(s):
        if not s:
            return '<missing>'
        arabic = bool(ARABIC_RE.search(s))
        latin = bool(LATIN_RE.search(s))
        if arabic and not latin:
            return 'ARABIC'
        if latin and not arabic:
            return 'ENGLISH'
        if arabic and latin:
            return 'MIXED'
        return 'UNKNOWN'

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

    print(f"{tid}: name.en=" + (name_en or '<missing>') + f" [{classify(name_en)}], city.en=" + (city_en or '<missing>') + f" [{classify(city_en)}]")
