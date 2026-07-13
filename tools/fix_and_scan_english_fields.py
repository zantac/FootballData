import json
from pathlib import Path

def fix_file(path: Path, make_backup=True):
    if not path.exists():
        print(f"ERROR: file not found: {path}")
        return
    with open(path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    teams = data.get('teams', []) if isinstance(data, dict) else []
    total = len(teams)
    name_en_before = 0
    city_en_before = 0
    name_filled = 0
    city_filled = 0

    for t in teams:
        name = t.get('name')
        city = t.get('city')

        # name
        has_name_en = False
        if isinstance(name, dict):
            if name.get('en'):
                has_name_en = True
        elif isinstance(name, str) and name.strip():
            # treat string as having 'en'
            has_name_en = True
        if has_name_en:
            name_en_before += 1
        else:
            # fill en from ar if possible
            if isinstance(name, dict) and name.get('ar'):
                name['en'] = name.get('ar')
                t['name'] = name
                name_filled += 1
            elif isinstance(name, str) and name.strip():
                t['name'] = {'ar': name, 'en': name}
                name_filled += 1
            else:
                # nothing to copy
                pass

        # city
        has_city_en = False
        if isinstance(city, dict):
            if city.get('en'):
                has_city_en = True
        elif isinstance(city, str) and city.strip():
            has_city_en = True
        if has_city_en:
            city_en_before += 1
        else:
            if isinstance(city, dict) and city.get('ar'):
                city['en'] = city.get('ar')
                t['city'] = city
                city_filled += 1
            elif isinstance(city, str) and city.strip():
                t['city'] = {'ar': city, 'en': city}
                city_filled += 1
            else:
                pass

    print(f"Teams: {total}")
    print(f"name.en before: {name_en_before}")
    print(f"city.en before: {city_en_before}")
    print(f"name.en filled: {name_filled}")
    print(f"city.en filled: {city_filled}")

    if make_backup:
        backup = path.with_suffix(path.suffix + '.bak')
        try:
            backup.write_text(path.read_text(encoding='utf-8'), encoding='utf-8')
            print(f"Backup created: {backup}")
        except Exception as e:
            print(f"Could not create backup: {e}")

    # write file back
    try:
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        print(f"File updated: {path}")
    except Exception as e:
        print(f"Failed to write file: {e}")

if __name__ == '__main__':
    p = Path(r"c:\Users\waell\Documents\GitHub\FootballData\2007\A\A2007Data.json")
    fix_file(p)
