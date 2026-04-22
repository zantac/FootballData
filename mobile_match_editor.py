# --- MOBILE PORTRAIT OPTIMIZED VERSION ---
# Place this entire script in Pydroid3 and run.

import json
import os
import tkinter as tk
from tkinter import ttk, messagebox, filedialog, font
from tkcalendar import DateEntry
from datetime import datetime
import copy
from PIL import Image, ImageTk

# ---------------------- Arabic Reshaper ----------------------
try:
    import arabic_reshaper
    HAS_RESHAPER = True
except ImportError:
    HAS_RESHAPER = False
    print("Warning: arabic-reshaper not installed.")

def reshape_arabic(text):
    if not HAS_RESHAPER or not text or not isinstance(text, str):
        return text
    if any('\u0600' <= c <= '\u06FF' for c in text):
        return arabic_reshaper.reshape(text)
    return text

# ---------------------- Custom Widgets (unchanged) ----------------------
class ComboboxSearchable(ttk.Combobox):
    def __init__(self, master, **kwargs):
        if 'width' not in kwargs:
            kwargs['width'] = 18
        super().__init__(master, **kwargs)
        self._full_list = list(self['values'])
        self.bind('<KeyRelease>', self._filter_list)
        self.bind('<FocusOut>', self._restore_list)
    def _filter_list(self, event=None):
        search_term = self.get().lower()
        filtered_values = [val for val in self._full_list if search_term in val.lower()]
        self['values'] = filtered_values
        if filtered_values and event and event.keysym not in ('BackSpace', 'Delete'):
            if self.get():
                self.event_generate('<Button-1>')
    def _restore_list(self, event=None):
        self['values'] = self._full_list
    def update_values(self, new_values):
        self._full_list = list(new_values)
        self['values'] = self._full_list

class ArrayFieldEditor(ttk.Frame):
    def __init__(self, parent, field_name, initial_list=None, dirty_callback=None, **kwargs):
        super().__init__(parent, **kwargs)
        self.field_name = field_name
        self.dirty_callback = dirty_callback
        self.entries = []
        self.list_frame = ttk.Frame(self)
        self.list_frame.pack(fill=tk.X, pady=1)
        add_btn = ttk.Button(self, text="+", command=self.add_entry, width=3)
        add_btn.pack(anchor=tk.W, pady=(0,2))
        if initial_list:
            for item in initial_list:
                self.add_entry(initial_value=item)
    def add_entry(self, initial_value=""):
        if self.dirty_callback:
            self.dirty_callback()
        row_frame = ttk.Frame(self.list_frame)
        row_frame.pack(fill=tk.X, pady=1)
        entry = ttk.Entry(row_frame, width=25)
        entry.pack(side=tk.LEFT, padx=(0,3))
        entry.insert(0, initial_value)
        entry.bind("<KeyRelease>", lambda e: self.dirty_callback())
        remove_btn = ttk.Button(row_frame, text="✖", width=2, command=lambda: self.remove_entry(row_frame))
        remove_btn.pack(side=tk.LEFT)
        self.entries.append((entry, row_frame))
    def remove_entry(self, row_frame):
        if self.dirty_callback:
            self.dirty_callback()
        for i, (entry, frame) in enumerate(self.entries):
            if frame == row_frame:
                frame.destroy()
                del self.entries[i]
                break
    def get_value(self):
        result = []
        for entry, _ in self.entries:
            val = entry.get().strip()
            if val:
                result.append(val)
        return result if result else None
    def set_value(self, value_list):
        for entry, frame in self.entries:
            frame.destroy()
        self.entries.clear()
        if value_list and isinstance(value_list, list):
            for item in value_list:
                self.add_entry(initial_value=str(item))

class DictFieldEditor(ttk.Frame):
    def __init__(self, parent, field_name, initial_dict=None, dirty_callback=None, **kwargs):
        super().__init__(parent, **kwargs)
        self.field_name = field_name
        self.editors = {}
        self.sub_keys = ["coach", "goalkeepers", "defenders", "midfielders", "attackers"]
        if initial_dict is None:
            initial_dict = {}
        for key in self.sub_keys:
            frame = ttk.Frame(self)
            frame.pack(fill=tk.X, pady=1)
            short_label = {"coach":"Coach", "goalkeepers":"GK", "defenders":"DEF",
                           "midfielders":"MID", "attackers":"ATT"}.get(key, key.title())
            lbl = ttk.Label(frame, text=short_label+":", width=6, anchor="w")
            lbl.pack(side=tk.TOP, anchor="w")
            val_list = initial_dict.get(key, [])
            editor = ArrayFieldEditor(frame, key, initial_list=val_list, dirty_callback=dirty_callback)
            editor.pack(fill=tk.X)
            self.editors[key] = editor
    def get_value(self):
        result = {}
        has_data = False
        for key, editor in self.editors.items():
            val = editor.get_value()
            result[key] = val
            if val:
                has_data = True
        return result if has_data else None
    def set_value(self, value_dict):
        if not isinstance(value_dict, dict):
            value_dict = {}
        for key, editor in self.editors.items():
            editor.set_value(value_dict.get(key))

# ---------------------- Match Editor Tab (Portrait) ----------------------
class MatchEditorTab(ttk.Frame):
    def __init__(self, parent, data, team_map, venue_list, save_callback, icons={}):
        super().__init__(parent)
        self.data = data
        self.team_map = team_map
        self.venue_list = venue_list
        self.save_callback = save_callback
        self.icons = icons
        self.current_match = None
        self.is_dirty = False
        self.pack(fill=tk.BOTH, expand=True)
        self.create_widgets()
        self.refresh_match_tree()

    def _mark_dirty(self, event=None):
        self.is_dirty = True

    def _check_unsaved_changes(self):
        if not self.is_dirty:
            return True
        response = messagebox.askyesnocancel("Unsaved Changes", "Save changes?")
        if response is True:
            self.save_current_match()
            return True
        elif response is False:
            self.is_dirty = False
            return True
        else:
            return False

    def create_widgets(self):
        # Main vertical layout
        self.columnconfigure(0, weight=1)
        self.rowconfigure(0, weight=0)   # tree frame (fixed height)
        self.rowconfigure(1, weight=1)   # form frame (expands)

        # --- TOP: match tree (LARGER & TALLER) ---
        top_frame = ttk.Frame(self)
        top_frame.grid(row=0, column=0, sticky="ew", padx=5, pady=3)
        top_frame.columnconfigure(0, weight=1)

        # Buttons row
        btn_frame = ttk.Frame(top_frame)
        btn_frame.grid(row=0, column=0, sticky="ew", pady=2)
        for text, cmd, icon in [("New", self.new_match, 'new'),
                                ("Dup", self.duplicate_match, 'duplicate'),
                                ("Del", self.delete_match, 'delete'),
                                ("Collapse", self.collapse_all_dates, 'collapse'),
                                ("Expand", self.expand_all_dates, 'expand')]:
            btn = ttk.Button(btn_frame, text=text, image=self.icons.get(icon),
                             compound=tk.LEFT, command=cmd)
            btn.pack(side=tk.LEFT, padx=2)

        # Treeview with bigger font and more rows
        tree_container = ttk.Frame(top_frame)
        tree_container.grid(row=1, column=0, sticky="ew", pady=2)
        tree_container.columnconfigure(0, weight=1)
        tree_container.rowconfigure(0, weight=1)

        self.match_tree = ttk.Treeview(tree_container, selectmode="browse", show="tree",
                                       height=12)   # show 12 matches at once
        vsb = ttk.Scrollbar(tree_container, orient="vertical", command=self.match_tree.yview)
        self.match_tree.configure(yscrollcommand=vsb.set)
        self.match_tree.grid(row=0, column=0, sticky="nsew")
        vsb.grid(row=0, column=1, sticky="ns")
        self.match_tree.bind("<<TreeviewSelect>>", self.on_match_select)

        # Style the tree font (larger)
        style = ttk.Style()
        style.configure("Treeview", font=("DejaVu Sans", 9), rowheight=24)
        style.configure("Treeview.Heading", font=("Segoe UI", 9, "bold"))

        # --- BOTTOM: scrollable form (with left margin) ---
        bottom_frame = ttk.Frame(self)
        bottom_frame.grid(row=1, column=0, sticky="nsew", padx=8, pady=3)   # added left margin
        bottom_frame.columnconfigure(0, weight=1)
        bottom_frame.rowconfigure(0, weight=1)

        canvas = tk.Canvas(bottom_frame, highlightthickness=0)
        v_scrollbar = ttk.Scrollbar(bottom_frame, orient="vertical", command=canvas.yview)
        canvas.configure(yscrollcommand=v_scrollbar.set)
        canvas.grid(row=0, column=0, sticky="nsew")
        v_scrollbar.grid(row=0, column=1, sticky="ns")

        scrollable = ttk.Frame(canvas)
        canvas.create_window((0,0), window=scrollable, anchor="nw")

        def on_configure(event):
            canvas.configure(scrollregion=canvas.bbox("all"))
        scrollable.bind("<Configure>", on_configure)

        def on_mousewheel(event):
            canvas.yview_scroll(int(-1*(event.delta/120)), "units")
        canvas.bind_all("<MouseWheel>", on_mousewheel)

        # --- Build the form (smaller fonts) ---
        self.widgets = {}
        row = 0

        # Common fields (single column)
        simple_fields = [
            ("match_id", "ID:"), ("group", "Grp:"), ("week", "Wk:"),
            ("date", "Date:"), ("time", "Time:"), ("venue", "Venue:"),
            ("status", "Status:"), ("stage", "Stage:"), ("note", "Note:")
        ]
        for field, label in simple_fields:
            frm = ttk.Frame(scrollable)
            frm.grid(row=row, column=0, sticky="ew", pady=1)
            frm.columnconfigure(1, weight=1)
            lbl = ttk.Label(frm, text=label, width=6, anchor="e", font=("DejaVu Sans", 8))
            lbl.grid(row=0, column=0, padx=2)
            if field == "date":
                w = DateEntry(frm, width=12, date_pattern='yyyy-mm-dd',
                              font=("DejaVu Sans", 7))
                w.bind("<<DateEntrySelected>>", self._mark_dirty)
            elif field == "venue":
                w = ComboboxSearchable(frm, values=self.venue_list, width=20)
                w.bind("<<ComboboxSelected>>", self._mark_dirty)
                w.bind("<KeyRelease>", self._mark_dirty)
            elif field in ("status", "stage"):
                opts = ["upcoming","completed","delayed"] if field=="status" else ["","1","2","knockout"]
                w = ttk.Combobox(frm, values=opts, width=18)
                w.bind("<<ComboboxSelected>>", self._mark_dirty)
            else:
                w = ttk.Entry(frm, width=22, font=("DejaVu Sans", 7))
                w.bind("<KeyRelease>", self._mark_dirty)
            w.grid(row=0, column=1, sticky="ew", padx=2)
            self.widgets[field] = w
            row += 1

        # ---- HOME section ----
        ttk.Label(scrollable, text="🏠 HOME", font=("Segoe UI", 9, "bold"),
                  foreground="blue").grid(row=row, column=0, sticky="w", pady=(5,2))
        row += 1

        # Home team
        frm = ttk.Frame(scrollable)
        frm.grid(row=row, column=0, sticky="ew", pady=1)
        frm.columnconfigure(1, weight=1)
        ttk.Label(frm, text="Team:", width=6, anchor="e", font=("DejaVu Sans", 8)).grid(row=0, column=0, padx=2)
        home_team = ComboboxSearchable(frm, values=[reshape_arabic(n) for n in self.team_map.keys()], width=20)
        home_team.bind("<<ComboboxSelected>>", self._mark_dirty)
        home_team.grid(row=0, column=1, sticky="ew", padx=2)
        self.widgets["home_team_id"] = home_team
        row += 1

        # Home score
        frm = ttk.Frame(scrollable)
        frm.grid(row=row, column=0, sticky="ew", pady=1)
        frm.columnconfigure(1, weight=1)
        ttk.Label(frm, text="Score:", width=6, anchor="e", font=("DejaVu Sans", 8)).grid(row=0, column=0, padx=2)
        home_score = ttk.Entry(frm, width=22, font=("DejaVu Sans", 7))
        home_score.bind("<KeyRelease>", self._mark_dirty)
        home_score.grid(row=0, column=1, sticky="ew", padx=2)
        self.widgets["home_score"] = home_score
        row += 1

        # Home array fields
        home_arrays = [("home_squade", "Squad"), ("home_scorers", "Scorers"),
                       ("home_yc", "YC"), ("home_rc", "RC"), ("home_sub", "Sub")]
        for field, label in home_arrays:
            frm = ttk.Frame(scrollable)
            frm.grid(row=row, column=0, sticky="ew", pady=2)
            ttk.Label(frm, text=label+":", font=("DejaVu Sans", 8), anchor="w").pack(anchor="w")
            editor = ArrayFieldEditor(frm, field, dirty_callback=self._mark_dirty)
            editor.pack(fill=tk.X)
            self.widgets[field] = editor
            row += 1

        # ---- AWAY section ----
        ttk.Label(scrollable, text="✈️ AWAY", font=("Segoe UI", 9, "bold"),
                  foreground="red").grid(row=row, column=0, sticky="w", pady=(10,2))
        row += 1

        # Away team
        frm = ttk.Frame(scrollable)
        frm.grid(row=row, column=0, sticky="ew", pady=1)
        frm.columnconfigure(1, weight=1)
        ttk.Label(frm, text="Team:", width=6, anchor="e", font=("DejaVu Sans", 8)).grid(row=0, column=0, padx=2)
        away_team = ComboboxSearchable(frm, values=[reshape_arabic(n) for n in self.team_map.keys()], width=20)
        away_team.bind("<<ComboboxSelected>>", self._mark_dirty)
        away_team.grid(row=0, column=1, sticky="ew", padx=2)
        self.widgets["away_team_id"] = away_team
        row += 1

        # Away score
        frm = ttk.Frame(scrollable)
        frm.grid(row=row, column=0, sticky="ew", pady=1)
        frm.columnconfigure(1, weight=1)
        ttk.Label(frm, text="Score:", width=6, anchor="e", font=("DejaVu Sans", 8)).grid(row=0, column=0, padx=2)
        away_score = ttk.Entry(frm, width=22, font=("DejaVu Sans", 7))
        away_score.bind("<KeyRelease>", self._mark_dirty)
        away_score.grid(row=0, column=1, sticky="ew", padx=2)
        self.widgets["away_score"] = away_score
        row += 1

        # Away array fields
        away_arrays = [("away_squade", "Squad"), ("away_scorers", "Scorers"),
                       ("away_yc", "YC"), ("away_rc", "RC"), ("away_sub", "Sub")]
        for field, label in away_arrays:
            frm = ttk.Frame(scrollable)
            frm.grid(row=row, column=0, sticky="ew", pady=2)
            ttk.Label(frm, text=label+":", font=("DejaVu Sans", 8), anchor="w").pack(anchor="w")
            editor = ArrayFieldEditor(frm, field, dirty_callback=self._mark_dirty)
            editor.pack(fill=tk.X)
            self.widgets[field] = editor
            row += 1

        # Save button
        save_btn = ttk.Button(scrollable, text="💾 Save Match", image=self.icons.get('save'),
                              compound=tk.LEFT, command=self.save_current_match)
        save_btn.grid(row=row, column=0, pady=10)

    # ---------- The rest of the methods (refresh_match_tree, populate_form, etc.) ----------
    # They remain exactly as in your original script, but we'll include them for completeness.
    # (Copy from your existing MatchEditorTab – no changes needed)
    def refresh_match_tree(self):
        self.match_tree.delete(*self.match_tree.get_children())
        matches_by_date = {}
        for match in self.data.get("matches", []):
            date = match.get("date", "Unknown")
            matches_by_date.setdefault(date, []).append(match)
        for date in sorted(matches_by_date.keys()):
            try:
                dt = datetime.strptime(date, "%Y-%m-%d")
                display_date = f"{date} ({dt.strftime('%a')})"
            except:
                display_date = date
            date_node = self.match_tree.insert("", "end", text=display_date, open=True)
            for match in sorted(matches_by_date[date], key=lambda m: m.get("time", "")):
                home_name = self.get_team_name(match.get("home_team_id")) or "?"
                away_name = self.get_team_name(match.get("away_team_id")) or "?"
                home_disp = reshape_arabic(home_name)
                away_disp = reshape_arabic(away_name)
                match_text = f"{match.get('match_id')} - {home_disp} vs {away_disp}"
                self.match_tree.insert(date_node, "end", text=match_text, values=(match.get('match_id'),))

    def collapse_all_dates(self):
        for child in self.match_tree.get_children():
            self.match_tree.item(child, open=False)

    def expand_all_dates(self):
        for child in self.match_tree.get_children():
            self.match_tree.item(child, open=True)

    def get_match_by_tree_selection(self):
        selection = self.match_tree.selection()
        if not selection: return None
        item = selection[0]
        values = self.match_tree.item(item, "values")
        if values:
            match_id = values[0]
            for match in self.data.get("matches", []):
                if match.get("match_id") == match_id:
                    return match
        return None

    def on_match_select(self, event):
        if not self._check_unsaved_changes(): return
        match = self.get_match_by_tree_selection()
        if match:
            self.current_match = match
            self.populate_form(match)

    def get_team_name(self, team_id):
        for name, tid in self.team_map.items():
            if tid == team_id:
                return name
        return None

    def populate_form(self, match):
        for field, widget in self.widgets.items():
            value = match.get(field)
            if field == "date":
                if value:
                    try: widget.set_date(datetime.strptime(value, "%Y-%m-%d"))
                    except: widget.set_date(datetime.now())
                else: widget.set_date(datetime.now())
            elif field in ("home_team_id", "away_team_id"):
                name = self.get_team_name(value) if value else ""
                widget.set(reshape_arabic(name))
            elif isinstance(widget, ArrayFieldEditor):
                # reshape each string in the list
                if value and isinstance(value, list):
                    reshaped = [reshape_arabic(str(v)) for v in value]
                    widget.set_value(reshaped)
                else:
                    widget.set_value([])
            elif isinstance(widget, ttk.Combobox):
                widget.set(str(value) if value is not None else "")
            else:
                widget.delete(0, tk.END)
                val = str(value) if value not in (None, "") else ""
                # reshape if it's a text field that may contain Arabic
                if field in ("group", "note", "venue"):
                    val = reshape_arabic(val)
                widget.insert(0, val)
        self.is_dirty = False

    def new_match(self):
        if not self._check_unsaved_changes(): return
        existing_ids = [m.get("match_id","") for m in self.data.get("matches",[]) if m.get("match_id")]
        max_num = 0
        for mid in existing_ids:
            if mid.startswith("m") and mid[1:].isdigit():
                max_num = max(max_num, int(mid[1:]))
        new_id = f"m{max_num+1:03d}"
        new_match = {
            "match_id": new_id, "group": "", "week": "", "date": "", "time": "",
            "home_team_id": "", "away_team_id": "", "venue": "", "status": "upcoming",
            "note": None, "home_squade": None, "away_squade": None,
            "home_score": None, "away_score": None, "home_scorers": None,
            "away_scorers": None, "home_yc": None, "away_yc": None,
            "home_rc": None, "away_rc": None, "home_sub": None, "away_sub": None, "stage": ""
        }
        self.current_match = new_match
        self.populate_form(new_match)
        self.is_dirty = True

    def duplicate_match(self):
        if not self.current_match:
            messagebox.showwarning("Warning", "Select a match to duplicate.")
            return
        if not self._check_unsaved_changes(): return
        dup = copy.deepcopy(self.current_match)
        existing_ids = [m.get("match_id","") for m in self.data.get("matches",[])]
        max_num = 0
        for mid in existing_ids:
            if mid.startswith("m") and mid[1:].isdigit():
                max_num = max(max_num, int(mid[1:]))
        dup['match_id'] = f"m{max_num+1:03d}"
        self.current_match = dup
        self.populate_form(dup)
        self.is_dirty = True
        messagebox.showinfo("Duplicated", f"New ID: {dup['match_id']}")

    def save_current_match(self):
        if not self.current_match:
            messagebox.showwarning("Warning", "No match selected.")
            return
        updated = {}
        for field, widget in self.widgets.items():
            if field == "date":
                value = widget.get_date().strftime("%Y-%m-%d")
            elif field in ("home_team_id", "away_team_id"):
                display_name = widget.get()
                # find original name (unreshaped)
                original_name = None
                for name in self.team_map.keys():
                    if reshape_arabic(name) == display_name:
                        original_name = name
                        break
                if original_name is None:
                    original_name = display_name
                value = self.team_map.get(original_name, "")
            elif field == "venue":
                value = widget.get().strip()
                if value and value not in self.venue_list:
                    self.venue_list.append(value)
                    self.data["venues"] = sorted(self.venue_list)
                    self.widgets["venue"].update_values(self.venue_list)
            elif field in ("home_score", "away_score"):
                txt = widget.get().strip()
                value = int(txt) if txt.isdigit() else None
            elif isinstance(widget, ArrayFieldEditor):
                value = widget.get_value()  # already list of strings (original, unreshaped)
            else:
                value = widget.get().strip()
                if field == "note" and not value:
                    value = None
            updated[field] = value
        original_id = self.current_match.get("match_id")
        updated["match_id"] = self.widgets["match_id"].get().strip() or original_id
        matches = self.data.get("matches", [])
        found = False
        for i, m in enumerate(matches):
            if m.get("match_id") == original_id:
                matches[i] = updated
                found = True
                break
        if not found:
            matches.append(updated)
        self.data["matches"] = matches
        self.current_match = updated
        self.refresh_match_tree()
        self.save_callback()
        self.is_dirty = False
        messagebox.showinfo("Success", "Match saved.")

    def delete_match(self):
        if not self.current_match: return
        mid = self.current_match.get("match_id")
        if messagebox.askyesno("Confirm", f"Delete {mid}?"):
            self.data["matches"] = [m for m in self.data["matches"] if m.get("match_id") != mid]
            self.current_match = None
            self.populate_form({})
            self.refresh_match_tree()
            self.save_callback()
            self.is_dirty = False
            messagebox.showinfo("Deleted", f"{mid} removed.")

class TeamEditorTab(ttk.Frame):
    def __init__(self, parent, data, save_callback, refresh_team_map_callback, icons={}):
        super().__init__(parent)
        self.data = data
        self.save_callback = save_callback
        self.refresh_team_map_callback = refresh_team_map_callback
        self.icons = icons
        self.current_team = None
        self.is_dirty = False
        self.pack(fill=tk.BOTH, expand=True)
        self.create_widgets()
        self.refresh_team_list()

    def _mark_dirty(self, event=None):
        self.is_dirty = True

    def _check_unsaved_changes(self):
        if not self.is_dirty:
            return True
        response = messagebox.askyesnocancel("Unsaved Changes", "Save team?")
        if response is True:
            self.save_current_team()
            return True
        elif response is False:
            self.is_dirty = False
            return True
        else:
            return False

    def create_widgets(self):
        # Use PanedWindow for left/right but on portrait we still need left list narrow
        paned = ttk.PanedWindow(self, orient=tk.HORIZONTAL)
        paned.pack(fill=tk.BOTH, expand=True)

        left_frame = ttk.Frame(paned, width=150)
        paned.add(left_frame, weight=0)
        right_frame = ttk.Frame(paned)
        paned.add(right_frame, weight=1)

        ttk.Label(left_frame, text="Teams", font=("Segoe UI", 9, "bold")).pack(pady=2)
        self.team_listbox = tk.Listbox(left_frame, width=20, font=("DejaVu Sans", 8), relief="flat")
        vsb = ttk.Scrollbar(left_frame, orient="vertical", command=self.team_listbox.yview)
        self.team_listbox.configure(yscrollcommand=vsb.set)
        self.team_listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        vsb.pack(side=tk.RIGHT, fill=tk.Y)
        self.team_listbox.bind("<<ListboxSelect>>", self.on_team_select)

        btn_frame = ttk.Frame(left_frame)
        btn_frame.pack(pady=3)
        ttk.Button(btn_frame, text="New", image=self.icons.get('new'), compound=tk.LEFT, command=self.new_team).pack(side=tk.LEFT, padx=2)
        ttk.Button(btn_frame, text="Dup", image=self.icons.get('duplicate'), compound=tk.LEFT, command=self.duplicate_team).pack(side=tk.LEFT, padx=2)
        ttk.Button(btn_frame, text="Del", image=self.icons.get('delete'), compound=tk.LEFT, command=self.delete_team).pack(side=tk.LEFT, padx=2)

        # Right side scrollable form
        canvas = tk.Canvas(right_frame, highlightthickness=0)
        vscroll = ttk.Scrollbar(right_frame, orient="vertical", command=canvas.yview)
        canvas.configure(yscrollcommand=vscroll.set)
        canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        vscroll.pack(side=tk.RIGHT, fill=tk.Y)

        scrollable = ttk.Frame(canvas)
        canvas.create_window((0,0), window=scrollable, anchor="nw")
        def on_configure(e):
            canvas.configure(scrollregion=canvas.bbox("all"))
        scrollable.bind("<Configure>", on_configure)

        self.widgets = {}
        fields = [
            ("team_id", "ID:"), ("name", "Name:"), ("group", "Grp:"),
            ("logo", "Logo:"), ("field", "Field:"), ("fieldurl", "Field URL:"),
            ("city", "City:"), ("information", "Info:"), ("point_deduction", "Pts Ded:")
        ]
        row = 0
        for field, label in fields:
            frm = ttk.Frame(scrollable)
            frm.grid(row=row, column=0, sticky="ew", pady=1)
            frm.columnconfigure(1, weight=1)
            ttk.Label(frm, text=label, width=8, anchor="e").grid(row=0, column=0, padx=2)
            if field == "information":
                w = tk.Text(frm, width=30, height=3, font=("DejaVu Sans", 8))
                w.bind("<KeyRelease>", self._mark_dirty)
            else:
                w = ttk.Entry(frm, width=25)
                w.bind("<KeyRelease>", self._mark_dirty)
            w.grid(row=0, column=1, sticky="ew", padx=2)
            self.widgets[field] = w
            row += 1

        ttk.Label(scrollable, text="Squad:", font=("Segoe UI", 8, "bold")).grid(row=row, column=0, sticky="w", pady=(5,0))
        row += 1
        self.players_editor = DictFieldEditor(scrollable, "players", dirty_callback=self._mark_dirty)
        self.players_editor.grid(row=row, column=0, sticky="ew", pady=2)
        row += 1
        ttk.Button(scrollable, text="Save Team", image=self.icons.get('save'), compound=tk.LEFT, command=self.save_current_team).grid(row=row, column=0, pady=5)

    def refresh_team_list(self):
        current_id = self.current_team.get("team_id") if self.current_team else None
        self.team_listbox.delete(0, tk.END)
        self.sorted_teams = sorted(self.data.get("teams", []), key=lambda x: x.get("name", ""))
        for i, team in enumerate(self.sorted_teams):
            name_disp = reshape_arabic(team.get('name',''))
            display = f"{team.get('team_id','')} - {name_disp}"
            self.team_listbox.insert(tk.END, display)
            if team.get("team_id") == current_id:
                self.team_listbox.selection_set(i)
    def on_team_select(self, event):
        if not self._check_unsaved_changes(): return
        sel = self.team_listbox.curselection()
        if not sel: return
        self.current_team = self.sorted_teams[sel[0]]
        self.populate_form(self.current_team)
    def populate_form(self, team):
        for field, widget in self.widgets.items():
            value = team.get(field)
            if isinstance(widget, tk.Text):
                widget.delete(1.0, tk.END)
                if value: widget.insert(1.0, str(value))
            else:
                widget.delete(0, tk.END)
                if value is not None:
                    if field == "name":
                        value = reshape_arabic(str(value))
                    widget.insert(0, str(value))
        self.players_editor.set_value(team.get("players"))
        self.is_dirty = False
    def new_team(self):
        if not self._check_unsaved_changes(): return
        self.current_team = {"team_id":"","name":"","group":"","logo":None,"field":None,"fieldurl":None,"city":None,"information":None,"players":{},"point_deduction":None}
        self.populate_form(self.current_team)
        self.is_dirty = True
    def duplicate_team(self):
        if not self.current_team:
            messagebox.showwarning("Warning", "Select a team to duplicate.")
            return
        if not self._check_unsaved_changes(): return
        dup = copy.deepcopy(self.current_team)
        dup['team_id'] = ""
        dup['name'] = f"{dup.get('name','')} (Copy)"
        self.current_team = dup
        self.populate_form(dup)
        self.is_dirty = True
        messagebox.showinfo("Duplicated", "Team duplicated. Set new ID.")
    def save_current_team(self):
        if not self.current_team:
            messagebox.showwarning("Warning", "No team loaded.")
            return
        updated = {}
        for field, widget in self.widgets.items():
            if isinstance(widget, tk.Text):
                value = widget.get(1.0, tk.END).strip()
            elif field == "point_deduction":
                txt = widget.get().strip()
                value = int(txt) if txt.isdigit() else None
            else:
                value = widget.get().strip()
            if not value and field not in ["team_id","name","point_deduction"]:
                value = None
            updated[field] = value
        updated["players"] = self.players_editor.get_value()
        if not updated.get("team_id"):
            messagebox.showerror("Error", "Team ID required.")
            return
        teams = self.data.get("teams", [])
        orig_id = self.current_team.get("team_id")
        found = False
        if orig_id:
            for i, t in enumerate(teams):
                if t.get("team_id") == orig_id:
                    teams[i] = updated
                    found = True
                    break
        if not found:
            teams.append(updated)
        self.data["teams"] = teams
        self.current_team = updated
        self.save_callback()
        self.refresh_team_map_callback()
        self.refresh_team_list()
        self.is_dirty = False
        messagebox.showinfo("Success", "Team saved.")
    def delete_team(self):
        if not self.current_team: return
        tid = self.current_team.get("team_id")
        if messagebox.askyesno("Confirm", f"Delete {tid}?"):
            self.data["teams"] = [t for t in self.data["teams"] if t.get("team_id") != tid]
            self.current_team = None
            self.populate_form({})
            self.save_callback()
            self.refresh_team_map_callback()
            self.refresh_team_list()
            self.is_dirty = False

# ---------------------- Main Application (adjusted for portrait) ----------------------
class MainApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Football Manager")
        # Use full screen but with safe margins
        w = root.winfo_screenwidth()
        h = root.winfo_screenheight()
        # Use almost full screen, but keep status bar visible
        root.geometry(f"{w}x{h}+0+0")
        self.setup_styles()
        self.load_icons()

        self.root.bind_class("Entry", "<Control-a>", lambda e: e.widget.select_range(0, "end"))
        self.root.bind_class("Text", "<Control-a>", lambda e: e.widget.tag_add("sel", "1.0", "end"))

        self.data = None
        self.file_path = None

        self.toolbar = ttk.Frame(root, padding=2)
        self.toolbar.pack(side=tk.TOP, fill=tk.X)
        ttk.Button(self.toolbar, text="Open File", command=self.open_new_file).pack(side=tk.LEFT, padx=2)

        self.content_frame = ttk.Frame(root)
        self.content_frame.pack(side=tk.TOP, fill=tk.BOTH, expand=True)
        self.notebook = ttk.Notebook(self.content_frame)
        self.notebook.pack(fill=tk.BOTH, expand=True)

        if not self.load_initial_file():
            self.root.destroy()

    def setup_styles(self):
        self.style = ttk.Style()
        self.style.theme_use('clam')
        default_font = font.nametofont("TkDefaultFont")
        default_font.configure(family="DejaVu Sans", size=8)
        self.root.configure(background="#f0f0f0")
        self.style.configure('.', background="#f0f0f0", font=default_font)
        self.style.configure('TLabel', padding=1)
        self.style.configure('TButton', padding=2)
        self.style.configure("Treeview", rowheight=20, font=("DejaVu Sans",7))

    def load_icons(self):
        self.icons = {}
        icon_names = ['new', 'duplicate', 'delete', 'collapse', 'save', 'expand']
        script_dir = os.path.dirname(os.path.abspath(__file__))
        for name in icon_names:
            try:
                path = os.path.join(script_dir, 'icons', f'{name}.png')
                img = Image.open(path).resize((14,14), Image.Resampling.LANCZOS)
                self.icons[name] = ImageTk.PhotoImage(img)
            except:
                pass

    def load_initial_file(self):
        path = filedialog.askopenfilename(title="Select JSON", filetypes=[("JSON","*.json")])
        if not path: return False
        if self._load_file_data(path):
            self.rebuild_ui()
            return True
        return False

    def open_new_file(self):
        tab = self.notebook.select()
        if tab:
            widget = self.root.nametowidget(tab)
            if hasattr(widget, '_check_unsaved_changes') and not widget._check_unsaved_changes():
                return
        path = filedialog.askopenfilename(title="Select JSON", filetypes=[("JSON","*.json")])
        if path and self._load_file_data(path):
            self.rebuild_ui()

    def _load_file_data(self, path):
        try:
            with open(path, 'r', encoding='utf-8') as f:
                self.data = json.load(f)
            self.file_path = path
            self.root.title(f"Football Manager - {os.path.basename(path)}")
        except Exception as e:
            messagebox.showerror("Error", str(e))
            return False
        for k in ["matches","teams","venues"]:
            if k not in self.data:
                self.data[k] = []
        return True

    def rebuild_ui(self):
        for tab in self.notebook.tabs():
            self.notebook.forget(tab)
        self.update_data_helpers()
        self.match_tab = MatchEditorTab(self.notebook, self.data, self.team_map, self.venue_list, self.save_to_file, self.icons)
        self.team_tab = TeamEditorTab(self.notebook, self.data, self.save_to_file, self.refresh_helpers_and_ui, self.icons)
        self.notebook.add(self.match_tab, text="Matches")
        self.notebook.add(self.team_tab, text="Teams")

    def update_data_helpers(self):
        self.team_map = {t.get("name",""): t.get("team_id") for t in self.data.get("teams",[])}
        venues = set(self.data.get("venues",[]))
        for m in self.data.get("matches",[]):
            if m.get("venue"):
                venues.add(m["venue"])
        self.venue_list = sorted(venues)
        self.data["venues"] = self.venue_list

    def refresh_helpers_and_ui(self):
        self.update_data_helpers()
        if hasattr(self, 'match_tab'):
            self.match_tab.team_map = self.team_map
            reshaped = [reshape_arabic(n) for n in self.team_map.keys()]
            self.match_tab.widgets["home_team_id"].update_values(reshaped)
            self.match_tab.widgets["away_team_id"].update_values(reshaped)
            self.match_tab.refresh_match_tree()

    def save_to_file(self):
        try:
            with open(self.file_path, 'w', encoding='utf-8') as f:
                json.dump(self.data, f, indent=2, ensure_ascii=False)
        except Exception as e:
            messagebox.showerror("Error", f"Save failed: {e}")

if __name__ == "__main__":
    root = tk.Tk()
    app = MainApp(root)
    root.mainloop()