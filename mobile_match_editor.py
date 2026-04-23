# --- START OF FILE: football_editor.py (IMPROVED VERSION) ---

# --- MOBILE PORTRAIT OPTIMIZED JSON EDITOR WITH PROPER ARABIC SUPPORT ---
# Designed for editing a football league's data (matches, teams, etc.)
# on Android devices using Pydroid 3.
# This version ensures that Arabic text is saved correctly in its logical order.

import json
import os
import tkinter as tk
from tkinter import ttk, messagebox, filedialog, font
from tkcalendar import DateEntry
from datetime import datetime
import copy
from PIL import Image, ImageTk

# ---------------------- Arabic Reshaper & Bidi ----------------------
# These libraries are essential for displaying Arabic text correctly in Tkinter.
try:
    import arabic_reshaper
    from bidi.algorithm import get_display
    HAS_BIDI = True
except ImportError:
    HAS_BIDI = False
    print("Warning: 'arabic-reshaper' or 'python-bidi' not installed. Arabic text may not display correctly.")
    print("In Pydroid 3, go to Menu -> Pip and install them.")

def reshape_arabic(text):
    """
    Properly reshape and reorder Arabic text for display-only widgets in Tkinter.
    """
    if not text or not isinstance(text, str):
        return text
    if any('\u0600' <= c <= '\u06FF' for c in text):
        if HAS_BIDI:
            reshaped = arabic_reshaper.reshape(text)
            bidi_text = get_display(reshaped)
            return bidi_text
        else:
            return text[::-1]
    return text

# ---------------------- Custom Widgets for a Better UI ----------------------
class ComboboxSearchable(ttk.Combobox):
    """A combobox that filters its values as the user types."""
    def __init__(self, master, **kwargs):
        if 'width' not in kwargs: kwargs['width'] = 18
        super().__init__(master, **kwargs)
        self._full_list = list(self['values'])
        self.bind('<KeyRelease>', self._filter_list)
        self.bind('<FocusOut>', self._restore_list)

    def _filter_list(self, event=None):
        search_term = self.get().lower()
        filtered_values = [val for val in self._full_list if search_term in val.lower()]
        self['values'] = filtered_values
        if filtered_values and event and event.keysym not in ('BackSpace', 'Delete'):
            if self.get(): self.event_generate('<Button-1>')

    def _restore_list(self, event=None):
        self['values'] = self._full_list

    def update_values(self, new_values):
        self._full_list = list(new_values)
        self['values'] = self._full_list

class ArrayFieldEditor(ttk.Frame):
    """A widget for editing a list of strings."""
    def __init__(self, parent, field_name, initial_list=None, dirty_callback=None, **kwargs):
        super().__init__(parent, **kwargs)
        self.field_name = field_name
        self.dirty_callback = dirty_callback
        self.entries = []
        self.list_frame = ttk.Frame(self)
        self.list_frame.pack(fill=tk.X, pady=1)
        add_btn = ttk.Button(self, text="+", command=self.add_entry, width=3)
        add_btn.pack(anchor=tk.W, pady=(0, 2))
        if initial_list:
            for item in initial_list: self.add_entry(initial_value=item)

    def add_entry(self, initial_value=""):
        if self.dirty_callback: self.dirty_callback()
        row_frame = ttk.Frame(self.list_frame)
        row_frame.pack(fill=tk.X, pady=1)
        entry = ttk.Entry(row_frame, width=25)
        entry.pack(side=tk.LEFT, padx=(0, 3), expand=True, fill=tk.X)
        entry.insert(0, initial_value)
        entry.bind("<KeyRelease>", lambda e: self.dirty_callback())
        remove_btn = ttk.Button(row_frame, text="✖", width=2, command=lambda rf=row_frame: self.remove_entry(rf))
        remove_btn.pack(side=tk.LEFT)
        self.entries.append((entry, row_frame))

    def remove_entry(self, row_frame):
        if self.dirty_callback: self.dirty_callback()
        for i, (entry, frame) in enumerate(self.entries):
            if frame == row_frame:
                frame.destroy()
                del self.entries[i]
                break

    def get_value(self):
        result = [entry.get().strip() for entry, _ in self.entries if entry.get().strip()]
        return result if result else None

    def set_value(self, value_list):
        for _, frame in self.entries: frame.destroy()
        self.entries.clear()
        if value_list and isinstance(value_list, list):
            for item in value_list: self.add_entry(initial_value=str(item))

class DictFieldEditor(ttk.Frame):
    """A specialized widget for editing a dictionary of lists (e.g., team squads)."""
    def __init__(self, parent, field_name, initial_dict=None, dirty_callback=None, **kwargs):
        super().__init__(parent, **kwargs)
        self.editors = {}
        self.sub_keys = ["coach", "goalkeepers", "defenders", "midfielders", "attackers"]
        initial_dict = initial_dict or {}
        for key in self.sub_keys:
            frame = ttk.Frame(self)
            frame.pack(fill=tk.X, pady=1)
            short_label = {"coach": "Coach", "goalkeepers": "GK", "defenders": "DEF",
                           "midfielders": "MID", "attackers": "ATT"}.get(key, key.title())
            lbl = ttk.Label(frame, text=short_label + ":", width=6, anchor="w")
            lbl.pack(side=tk.TOP, anchor="w")
            editor = ArrayFieldEditor(frame, key, initial_list=initial_dict.get(key, []), dirty_callback=dirty_callback)
            editor.pack(fill=tk.X)
            self.editors[key] = editor

    def get_value(self):
        result = {key: editor.get_value() for key, editor in self.editors.items()}
        return result if any(result.values()) else None

    def set_value(self, value_dict):
        value_dict = value_dict or {}
        for key, editor in self.editors.items():
            editor.set_value(value_dict.get(key))

def create_reverse_map(data_list):
    """Creates a dictionary to map reshaped Arabic text back to its original form."""
    return {reshape_arabic(item): item for item in data_list}

# ---------------------- Match Editor Tab ----------------------
class MatchEditorTab(ttk.Frame):
    def __init__(self, parent, data, team_map, venue_list, save_callback, icons={}):
        super().__init__(parent)
        self.data = data; self.team_map = team_map; self.venue_list = venue_list
        self.save_callback = save_callback; self.icons = icons
        self.current_match = None; self.is_dirty = False
        self.pack(fill=tk.BOTH, expand=True)
        self.create_widgets()
        self.refresh_match_tree()

    def _mark_dirty(self, event=None): self.is_dirty = True

    def _check_unsaved_changes(self):
        if not self.is_dirty: return True
        response = messagebox.askyesnocancel("Unsaved Changes", "Save changes?")
        if response is True:
            self.save_current_match()
            return not self.is_dirty
        return response is not None

    def create_widgets(self):
        self.columnconfigure(0, weight=1); self.rowconfigure(1, weight=1)
        self.rowconfigure(0, weight=0, minsize=200)
        top_frame = ttk.Frame(self); top_frame.grid(row=0, column=0, sticky="nsew", padx=5, pady=3)
        top_frame.columnconfigure(0, weight=1); top_frame.rowconfigure(1, weight=1)
        btn_frame = ttk.Frame(top_frame); btn_frame.grid(row=0, column=0, sticky="ew")
        row1 = ttk.Frame(btn_frame); row1.pack(fill=tk.X, pady=1)
        btn_configs1 = [("New", self.new_match, 'new'), ("Dup", self.duplicate_match, 'duplicate'), ("Del", self.delete_match, 'delete')]
        for text, cmd, icon in btn_configs1:
            btn = ttk.Button(row1, text=text, image=self.icons.get(icon), compound=tk.LEFT, command=cmd)
            btn.pack(side=tk.LEFT, padx=2, expand=True, fill=tk.X)
        row2 = ttk.Frame(btn_frame); row2.pack(fill=tk.X, pady=1)
        btn_configs2 = [("Collapse", self.collapse_all_dates, 'collapse'), ("Expand", self.expand_all_dates, 'expand')]
        for text, cmd, icon in btn_configs2:
            btn = ttk.Button(row2, text=text, image=self.icons.get(icon), compound=tk.LEFT, command=cmd)
            btn.pack(side=tk.LEFT, padx=2, expand=True, fill=tk.X)
        tree_container = ttk.Frame(top_frame); tree_container.grid(row=1, column=0, sticky="nsew", pady=2)
        tree_container.columnconfigure(0, weight=1); tree_container.rowconfigure(0, weight=1)
        self.match_tree = ttk.Treeview(tree_container, selectmode="browse", show="tree")
        vsb = ttk.Scrollbar(tree_container, orient="vertical", command=self.match_tree.yview)
        self.match_tree.configure(yscrollcommand=vsb.set); self.match_tree.grid(row=0, column=0, sticky="nsew")
        vsb.grid(row=0, column=1, sticky="ns"); self.match_tree.bind("<<TreeviewSelect>>", self.on_match_select)
        bottom_frame = ttk.Frame(self); bottom_frame.grid(row=1, column=0, sticky="nsew", padx=5, pady=3)
        bottom_frame.columnconfigure(0, weight=1); bottom_frame.rowconfigure(0, weight=1)
        canvas = tk.Canvas(bottom_frame, highlightthickness=0)
        v_scrollbar = ttk.Scrollbar(bottom_frame, orient="vertical", command=canvas.yview)
        canvas.configure(yscrollcommand=v_scrollbar.set); canvas.grid(row=0, column=0, sticky="nsew")
        v_scrollbar.grid(row=0, column=1, sticky="ns"); scrollable_frame = ttk.Frame(canvas)
        canvas.create_window((0, 0), window=scrollable_frame, anchor="nw")
        scrollable_frame.bind("<Configure>", lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
        canvas.bind_all("<MouseWheel>", lambda e: canvas.yview_scroll(int(-1 * (e.delta / 120)), "units"))
        self.widgets = {}; row = 0
        simple_fields = [("match_id", "ID:"), ("group", "Grp:"), ("week", "Wk:"), ("date", "Date:"), 
                         ("time", "Time:"), ("venue", "Venue:"), ("status", "Status:"), ("stage", "Stage:"), ("note", "Note:")]
        for field, label in simple_fields:
            frm = ttk.Frame(scrollable_frame); frm.grid(row=row, column=0, sticky="ew", pady=1)
            frm.columnconfigure(1, weight=1); ttk.Label(frm, text=label, width=7, anchor="e").grid(row=0, column=0, padx=2)
            if field == "date": w = DateEntry(frm, width=18, date_pattern='yyyy-mm-dd'); w.bind("<<DateEntrySelected>>", self._mark_dirty)
            elif field == "venue": w = ComboboxSearchable(frm, values=[reshape_arabic(v) for v in self.venue_list], width=20); w.bind("<<ComboboxSelected>>", self._mark_dirty); w.bind("<KeyRelease>", self._mark_dirty)
            elif field == "status": w = ttk.Combobox(frm, values=["upcoming", "completed", "delayed"], width=20); w.bind("<<ComboboxSelected>>", self._mark_dirty)
            elif field == "stage": w = ttk.Combobox(frm, values=["", "1", "2", "knockout"], width=20); w.bind("<<ComboboxSelected>>", self._mark_dirty)
            else: w = ttk.Entry(frm, width=22); w.bind("<KeyRelease>", self._mark_dirty)
            w.grid(row=0, column=1, sticky="ew", padx=2); self.widgets[field] = w; row += 1
        ttk.Label(scrollable_frame, text="HOME TEAM", font=("Segoe UI", 9, "bold"), foreground="blue").grid(row=row, column=0, sticky="w", pady=(8,2)); row += 1
        self.create_team_section(scrollable_frame, "home", row); row += 7
        ttk.Label(scrollable_frame, text="AWAY TEAM", font=("Segoe UI", 9, "bold"), foreground="red").grid(row=row, column=0, sticky="w", pady=(10,2)); row += 1
        self.create_team_section(scrollable_frame, "away", row); row += 7
        ttk.Button(scrollable_frame, text="Save Match", image=self.icons.get('save'), compound=tk.LEFT, command=self.save_current_match).grid(row=row, column=0, pady=10)

    def create_team_section(self, parent, prefix, start_row):
        row = start_row
        frm = ttk.Frame(parent); frm.grid(row=row, column=0, sticky="ew", pady=1); frm.columnconfigure(1, weight=1); row+=1
        ttk.Label(frm, text="Team:", width=7, anchor="e").grid(row=0, column=0, padx=2)
        team_combo = ComboboxSearchable(frm, values=[reshape_arabic(n) for n in self.team_map.keys()], width=20)
        team_combo.bind("<<ComboboxSelected>>", self._mark_dirty); team_combo.grid(row=0, column=1, sticky="ew", padx=2)
        self.widgets[f"{prefix}_team_id"] = team_combo
        frm = ttk.Frame(parent); frm.grid(row=row, column=0, sticky="ew", pady=1); frm.columnconfigure(1, weight=1); row+=1
        ttk.Label(frm, text="Score:", width=7, anchor="e").grid(row=0, column=0, padx=2)
        score_entry = ttk.Entry(frm, width=22); score_entry.bind("<KeyRelease>", self._mark_dirty)
        score_entry.grid(row=0, column=1, sticky="ew", padx=2); self.widgets[f"{prefix}_score"] = score_entry
        array_fields = [("squade", "Squad"), ("scorers", "Scorers"), ("yc", "Yellows"), ("rc", "Reds"), ("sub", "Subs")]
        for field, label in array_fields:
            full_field = f"{prefix}_{field}"; frm = ttk.Frame(parent); frm.grid(row=row, column=0, sticky="ew", pady=2, padx=10); row+=1
            ttk.Label(frm, text=label+":", anchor="w").pack(anchor="w")
            editor = ArrayFieldEditor(frm, full_field, dirty_callback=self._mark_dirty); editor.pack(fill=tk.X)
            self.widgets[full_field] = editor

    def refresh_match_tree(self):
        self.match_tree.delete(*self.match_tree.get_children())
        matches_by_date = {}
        for match in self.data.get("matches", []):
            date = match.get("date", "Unknown Date")
            matches_by_date.setdefault(date, []).append(match)
        for date in sorted(matches_by_date.keys()):
            try: dt = datetime.strptime(date, "%Y-%m-%d"); display_date = f"{date} ({dt.strftime('%a')})"
            except (ValueError, TypeError): display_date = date
            date_node = self.match_tree.insert("", "end", text=display_date, open=True)
            sorted_matches = sorted(matches_by_date[date], key=lambda m: m.get("time", ""))
            for match in sorted_matches:
                home_name = self.get_team_name(match.get("home_team_id")) or "?"; away_name = self.get_team_name(match.get("away_team_id")) or "?"
                match_text = f"ID:{match.get('match_id')} - {reshape_arabic(home_name)} vs {reshape_arabic(away_name)}"
                self.match_tree.insert(date_node, "end", text=match_text, values=(match.get('match_id'),))

    def collapse_all_dates(self): [self.match_tree.item(c, open=False) for c in self.match_tree.get_children()]
    def expand_all_dates(self): [self.match_tree.item(c, open=True) for c in self.match_tree.get_children()]
    def get_match_by_id(self, match_id):
        for match in self.data.get("matches", []):
            if str(match.get("match_id")) == str(match_id): return match
        return None
    def get_team_name(self, team_id):
        for name, tid in self.team_map.items():
            if tid == team_id: return name
        return None

    def on_match_select(self, event):
        selection = self.match_tree.selection()
        if not selection: return
        item = self.match_tree.item(selection[0])
        if not item['values']: return
        if not self._check_unsaved_changes(): return
        match = self.get_match_by_id(item['values'][0])
        if match: self.current_match = match; self.populate_form(match)

    def populate_form(self, match):
        for field, widget in self.widgets.items():
            value = match.get(field)
            if isinstance(widget, DateEntry):
                try: widget.set_date(value)
                except: widget.set_date(datetime.now())
            elif field in ("home_team_id", "away_team_id"):
                name = self.get_team_name(value) or ""
                widget.set(reshape_arabic(name))  # Display reshaped
            elif field == "venue":
                widget.set(reshape_arabic(str(value)) if value else "") # Display reshaped
            elif isinstance(widget, ArrayFieldEditor):
                widget.set_value(value if value else []) # Use raw logical text
            elif isinstance(widget, ttk.Combobox):
                widget.set(str(value) if value is not None else "")
            else: # Entry widget
                widget.delete(0, tk.END)
                val = str(value) if value is not None else ""
                widget.insert(0, val) # Use raw logical text
        self.is_dirty = False

    def new_match(self):
        if not self._check_unsaved_changes(): return
        existing_ids = [int(str(m.get("match_id","m0"))[1:]) for m in self.data.get("matches",[]) if str(m.get("match_id","m0")).startswith("m") and str(m.get("match_id","m0"))[1:].isdigit()]
        new_id_num = max(existing_ids) + 1 if existing_ids else 1
        new_id = f"m{new_id_num:03d}"
        new_match = {"match_id": new_id, "date": datetime.now().strftime("%Y-%m-%d"), "status": "upcoming"}
        for key in self.widgets.keys():
            if key not in new_match: new_match[key] = None
        self.current_match = new_match; self.populate_form(new_match); self.is_dirty = True

    def duplicate_match(self):
        if not self.current_match: return messagebox.showwarning("Warning", "Select a match to duplicate.")
        if not self._check_unsaved_changes(): return
        dup = copy.deepcopy(self.current_match)
        existing_ids = [int(str(m.get("match_id","m0"))[1:]) for m in self.data.get("matches",[]) if str(m.get("match_id","m0")).startswith("m") and str(m.get("match_id","m0"))[1:].isdigit()]
        new_id_num = max(existing_ids) + 1 if existing_ids else 1
        dup['match_id'] = f"m{new_id_num:03d}"
        self.current_match = dup; self.populate_form(dup); self.is_dirty = True
        messagebox.showinfo("Duplicated", f"Match duplicated with new ID: {dup['match_id']}")

    def save_current_match(self):
        if not self.current_match: return messagebox.showwarning("Warning", "No match selected.")
        updated = {}; team_name_reverse_map = create_reverse_map(self.team_map.keys())
        venue_name_reverse_map = create_reverse_map(self.venue_list)
        for field, widget in self.widgets.items():
            value = None
            if isinstance(widget, DateEntry): value = widget.get_date().strftime("%Y-%m-%d")
            elif field in ("home_team_id", "away_team_id"):
                display_name = widget.get()
                original_name = team_name_reverse_map.get(display_name, display_name)
                value = self.team_map.get(original_name, "")
            elif isinstance(widget, ArrayFieldEditor): value = widget.get_value()
            elif isinstance(widget, ttk.Combobox):
                value = widget.get().strip()
                if field == "venue":
                    original_venue = venue_name_reverse_map.get(value, value)
                    if original_venue and original_venue not in self.venue_list:
                        self.venue_list.append(original_venue); self.data["venues"] = sorted(self.venue_list)
                        self.widgets["venue"].update_values([reshape_arabic(v) for v in self.venue_list])
                    value = original_venue
            else: value = widget.get().strip()
            if field in ("home_score", "away_score"): value = int(value) if value.isdigit() else None
            elif not value: value = None
            updated[field] = value
        updated["match_id"] = str(self.widgets["match_id"].get().strip() or self.current_match.get("match_id"))
        matches = self.data.get("matches", []); original_id = self.current_match.get("match_id")
        found = any(str(m.get("match_id")) == str(original_id) for m in matches)
        if found:
            for i, m in enumerate(matches):
                if str(m.get("match_id")) == str(original_id): matches[i] = updated; break
        else: matches.append(updated)
        self.data["matches"] = matches; self.current_match = updated
        self.refresh_match_tree(); self.save_callback(); self.is_dirty = False
        messagebox.showinfo("Success", f"Match '{updated['match_id']}' saved.")

    def delete_match(self):
        if not self.current_match: return
        mid = self.current_match.get("match_id")
        if messagebox.askyesno("Confirm Deletion", f"Delete match '{mid}'?"):
            self.data["matches"] = [m for m in self.data["matches"] if str(m.get("match_id")) != str(mid)]
            self.current_match = None; self.populate_form({})
            self.refresh_match_tree(); self.save_callback(); self.is_dirty = False
            messagebox.showinfo("Deleted", f"Match '{mid}' removed.")

# ---------------------- Team Editor Tab ----------------------
class TeamEditorTab(ttk.Frame):
    def __init__(self, parent, data, save_callback, refresh_all_ui_callback, icons={}):
        super().__init__(parent)
        self.data = data; self.save_callback = save_callback
        self.refresh_all_ui_callback = refresh_all_ui_callback; self.icons = icons
        self.current_team = None; self.is_dirty = False
        self.pack(fill=tk.BOTH, expand=True)
        self.create_widgets(); self.refresh_team_list()

    def _mark_dirty(self, event=None): self.is_dirty = True

    def _check_unsaved_changes(self):
        if not self.is_dirty: return True
        response = messagebox.askyesnocancel("Unsaved Changes", "Save team?")
        if response is True:
            self.save_current_team()
            return not self.is_dirty
        return response is not None

    def create_widgets(self):
        self.columnconfigure(0, weight=1); self.rowconfigure(1, weight=1)
        self.rowconfigure(0, weight=0, minsize=180)
        top_frame = ttk.Frame(self); top_frame.grid(row=0, column=0, sticky="nsew", padx=5, pady=3)
        top_frame.columnconfigure(0, weight=1); top_frame.rowconfigure(1, weight=1)
        btn_frame = ttk.Frame(top_frame); btn_frame.grid(row=0, column=0, sticky="ew")
        btn_configs = [("New", self.new_team, 'new'), ("Dup", self.duplicate_team, 'duplicate'), ("Del", self.delete_team, 'delete')]
        for i, (text, cmd, icon) in enumerate(btn_configs):
            btn_frame.columnconfigure(i, weight=1)
            ttk.Button(btn_frame, text=text, image=self.icons.get(icon), compound=tk.LEFT, command=cmd).grid(row=0, column=i, sticky="ew", padx=2)
        list_container = ttk.Frame(top_frame); list_container.grid(row=1, column=0, sticky="nsew", pady=2)
        list_container.columnconfigure(0, weight=1); list_container.rowconfigure(0, weight=1)
        self.team_listbox = tk.Listbox(list_container, height=8, relief="flat", exportselection=False)
        vsb = ttk.Scrollbar(list_container, orient="vertical", command=self.team_listbox.yview)
        self.team_listbox.configure(yscrollcommand=vsb.set); self.team_listbox.grid(row=0, column=0, sticky="nsew")
        vsb.grid(row=0, column=1, sticky="ns"); self.team_listbox.bind("<<ListboxSelect>>", self.on_team_select)
        bottom_frame = ttk.Frame(self); bottom_frame.grid(row=1, column=0, sticky="nsew", padx=5, pady=3)
        bottom_frame.columnconfigure(0, weight=1); bottom_frame.rowconfigure(0, weight=1)
        canvas = tk.Canvas(bottom_frame, highlightthickness=0)
        v_scrollbar = ttk.Scrollbar(bottom_frame, orient="vertical", command=canvas.yview)
        canvas.configure(yscrollcommand=v_scrollbar.set); canvas.grid(row=0, column=0, sticky="nsew")
        v_scrollbar.grid(row=0, column=1, sticky="ns"); scrollable_frame = ttk.Frame(canvas)
        canvas.create_window((0, 0), window=scrollable_frame, anchor="nw")
        scrollable_frame.bind("<Configure>", lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
        canvas.bind_all("<MouseWheel>", lambda e: canvas.yview_scroll(int(-1 * (e.delta / 120)), "units"))
        self.widgets = {}; row = 0
        fields = [("team_id", "ID:"), ("name", "Name:"), ("group", "Grp:"), ("logo", "Logo:"),
                  ("field", "Field:"), ("fieldurl", "Field URL:"), ("city", "City:"),
                  ("information", "Info:"), ("point_deduction", "Pts Ded:")]
        for field, label in fields:
            frm = ttk.Frame(scrollable_frame); frm.grid(row=row, column=0, sticky="ew", pady=2)
            frm.columnconfigure(1, weight=1)
            ttk.Label(frm, text=label, width=9, anchor="e").grid(row=0, column=0, padx=2)
            if field == "information": w = tk.Text(frm, width=30, height=3, relief="flat"); w.bind("<KeyRelease>", self._mark_dirty)
            else: w = ttk.Entry(frm, width=25); w.bind("<KeyRelease>", self._mark_dirty)
            w.grid(row=0, column=1, sticky="ew", padx=2); self.widgets[field] = w; row += 1
        ttk.Label(scrollable_frame, text="Squad:", font=("Segoe UI", 8, "bold")).grid(row=row, column=0, sticky="w", pady=(5,0)); row += 1
        self.players_editor = DictFieldEditor(scrollable_frame, "players", dirty_callback=self._mark_dirty)
        self.players_editor.grid(row=row, column=0, sticky="ew", pady=2); row += 1
        ttk.Button(scrollable_frame, text="Save Team", image=self.icons.get('save'), compound=tk.LEFT, command=self.save_current_team).grid(row=row, column=0, pady=8)

    def refresh_team_list(self):
        current_id = self.current_team.get("team_id") if self.current_team else None
        self.team_listbox.delete(0, tk.END)
        self.sorted_teams = sorted(self.data.get("teams", []), key=lambda x: x.get("name", ""))
        for i, team in enumerate(self.sorted_teams):
            display = f"{team.get('team_id','')} - {reshape_arabic(team.get('name',''))}"
            self.team_listbox.insert(tk.END, display)
            if team.get("team_id") == current_id:
                self.team_listbox.selection_set(i); self.team_listbox.activate(i); self.team_listbox.see(i)

    def on_team_select(self, event):
        sel = self.team_listbox.curselection();
        if not sel: return
        if not self._check_unsaved_changes(): return
        self.current_team = self.sorted_teams[sel[0]]
        self.populate_form(self.current_team)

    def populate_form(self, team):
        for field, widget in self.widgets.items():
            value = team.get(field, "")
            if isinstance(widget, tk.Text):
                widget.delete(1.0, tk.END)
                if value: widget.insert(1.0, str(value)) # Use raw logical text
            else:
                widget.delete(0, tk.END)
                if value is not None: widget.insert(0, str(value)) # Use raw logical text
        self.players_editor.set_value(team.get("players", {})) # Use raw logical text
        self.is_dirty = False

    def new_team(self):
        if not self._check_unsaved_changes(): return
        self.current_team = {"players": {}}; self.populate_form(self.current_team)
        self.is_dirty = True; self.team_listbox.selection_clear(0, tk.END)

    def duplicate_team(self):
        if not self.current_team: return messagebox.showwarning("Warning", "Select a team to duplicate.")
        if not self._check_unsaved_changes(): return
        dup = copy.deepcopy(self.current_team); dup['team_id'] = ""
        dup['name'] = f"{dup.get('name', '')} (Copy)"; self.current_team = dup
        self.populate_form(dup); self.is_dirty = True
        messagebox.showinfo("Duplicated", "Team duplicated. Set a new ID.")

    def save_current_team(self):
        if not self.current_team: return messagebox.showwarning("Warning", "No team loaded.")
        updated = {}
        for field, widget in self.widgets.items():
            if isinstance(widget, tk.Text): value = widget.get(1.0, tk.END).strip()
            else: value = widget.get().strip()
            if field == "point_deduction": value = int(value) if value.isdigit() else None
            elif not value: value = None
            updated[field] = value
        updated["players"] = self.players_editor.get_value()
        if not updated.get("team_id"): return messagebox.showerror("Error", "Team ID is required.")
        teams = self.data.get("teams", []); orig_id = self.current_team.get("team_id")
        found = any(t.get("team_id") == orig_id for t in teams if orig_id)
        if found:
            for i, t in enumerate(teams):
                if t.get("team_id") == orig_id: teams[i] = updated; break
        else:
            if any(t.get("team_id") == updated["team_id"] for t in teams):
                return messagebox.showerror("Error", f"Team ID '{updated['team_id']}' already exists.")
            teams.append(updated)
        self.data["teams"] = teams; self.current_team = updated
        self.save_callback(); self.refresh_all_ui_callback(); self.is_dirty = False
        messagebox.showinfo("Success", f"Team '{updated['name']}' saved.")

    def delete_team(self):
        if not self.current_team: return
        tid, tname = self.current_team.get("team_id"), self.current_team.get("name")
        if messagebox.askyesno("Confirm Deletion", f"Delete '{tname}' ({tid})?"):
            self.data["teams"] = [t for t in self.data["teams"] if t.get("team_id") != tid]
            self.current_team = None; self.populate_form({})
            self.save_callback(); self.refresh_all_ui_callback(); self.is_dirty = False

# ---------------------- Main Application ----------------------
class MainApp:
    def __init__(self, root):
        self.root = root; self.root.title("Football Manager")
        self.root.geometry(f"{root.winfo_screenwidth()}x{root.winfo_screenheight()}+0+0")
        self.setup_styles(); self.load_icons()
        self.root.bind_class("TEntry", "<Control-a>", lambda e: e.widget.select_range(0, "end"))
        self.root.bind_class("Text", "<Control-a>", lambda e: e.widget.tag_add("sel", "1.0", "end"))
        self.data = None; self.file_path = None
        self.toolbar = ttk.Frame(root, padding=2); self.toolbar.pack(side=tk.TOP, fill=tk.X)
        ttk.Button(self.toolbar, text="Open JSON File", command=self.open_new_file).pack(side=tk.LEFT, padx=2)
        self.notebook = ttk.Notebook(root); self.notebook.pack(fill=tk.BOTH, expand=True, padx=2, pady=2)
        if not self.load_initial_file(): self.root.destroy()

    def setup_styles(self):
        self.style = ttk.Style(); self.style.theme_use('clam')
        default_font = ("DejaVu Sans", 8); text_font = ("DejaVu Sans", 7)
        self.root.option_add("*Font", default_font); self.root.configure(background="#f0f0f0")
        self.style.configure('.', background="#f0f0f0", font=default_font)
        self.style.configure('TLabel', padding=2); self.style.configure('TButton', padding=4)
        self.style.configure('TEntry', padding=2, font=text_font); self.style.configure('TCombobox', padding=2, font=text_font)
        self.style.configure("Treeview", rowheight=25, font=("DejaVu Sans", 8))
        self.style.configure("Treeview.Heading", font=("Segoe UI", 9, "bold"))
        self.style.map('TCombobox', fieldbackground=[('readonly','white')])

    def load_icons(self):
        self.icons = {}; icon_names = ['new', 'duplicate', 'delete', 'collapse', 'save', 'expand']
        script_dir = os.path.dirname(os.path.abspath(__file__)); icon_dir = os.path.join(script_dir, 'icons')
        for name in icon_names:
            try:
                path = os.path.join(icon_dir, f'{name}.png')
                img = Image.open(path).resize((16, 16), Image.Resampling.LANCZOS)
                self.icons[name] = ImageTk.PhotoImage(img)
            except Exception as e: print(f"Could not load icon: {name}.png ({e})")

    def load_initial_file(self):
        path = filedialog.askopenfilename(title="Select JSON database", filetypes=[("JSON files", "*.json")])
        if path and self._load_file_data(path): self.rebuild_ui(); return True
        return False

    def open_new_file(self):
        if self.notebook.tabs():
            active_tab = self.root.nametowidget(self.notebook.select())
            if hasattr(active_tab, '_check_unsaved_changes') and not active_tab._check_unsaved_changes(): return
        path = filedialog.askopenfilename(title="Select JSON database", filetypes=[("JSON files", "*.json")])
        if path and self._load_file_data(path): self.rebuild_ui()

    def _load_file_data(self, path):
        try:
            with open(path, 'r', encoding='utf-8') as f: self.data = json.load(f)
            self.file_path = path; self.root.title(f"Editor - {os.path.basename(path)}")
            for key in ["matches", "teams", "venues"]:
                if key not in self.data: self.data[key] = []
            return True
        except Exception as e:
            messagebox.showerror("File Load Error", f"Failed to load or parse JSON file:\n{e}")
            return False

    def rebuild_ui(self):
        for tab in self.notebook.tabs(): self.notebook.forget(tab)
        self.update_data_helpers()
        self.match_tab = MatchEditorTab(self.notebook, self.data, self.team_map, self.venue_list, self.save_to_file, self.icons)
        self.team_tab = TeamEditorTab(self.notebook, self.data, self.save_to_file, self.refresh_all_ui, self.icons)
        self.notebook.add(self.match_tab, text="Matches"); self.notebook.add(self.team_tab, text="Teams")

    def update_data_helpers(self):
        self.team_map = {t.get("name", ""): t.get("team_id") for t in self.data.get("teams", [])}
        venues = set(self.data.get("venues", []))
        for m in self.data.get("matches", []):
            if m.get("venue"): venues.add(m["venue"])
        self.venue_list = sorted(list(venues)); self.data["venues"] = self.venue_list

    def refresh_all_ui(self):
        self.update_data_helpers()
        if hasattr(self, 'team_tab'): self.team_tab.refresh_team_list()
        if hasattr(self, 'match_tab'):
            self.match_tab.team_map = self.team_map
            reshaped_teams = [reshape_arabic(n) for n in self.team_map.keys()]
            self.match_tab.widgets["home_team_id"].update_values(reshaped_teams)
            self.match_tab.widgets["away_team_id"].update_values(reshaped_teams)
            self.match_tab.venue_list = self.venue_list
            reshaped_venues = [reshape_arabic(v) for v in self.venue_list]
            self.match_tab.widgets["venue"].update_values(reshaped_venues)
            self.match_tab.refresh_match_tree()

    def save_to_file(self):
        if not self.file_path: return
        try:
            with open(self.file_path, 'w', encoding='utf-8') as f:
                json.dump(self.data, f, indent=2, ensure_ascii=False)
        except Exception as e: messagebox.showerror("Save Error", f"Failed to save file:\n{e}")

if __name__ == "__main__":
    root = tk.Tk()
    app = MainApp(root)
    root.mainloop()