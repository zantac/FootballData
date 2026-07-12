# --- START OF FILE Paste April 15, 2026 - 6:13PM (FINAL) ---

import json
import os
import tkinter as tk
from tkinter import ttk, messagebox, filedialog, font
from tkcalendar import DateEntry
from datetime import datetime
import copy
from PIL import Image, ImageTk

# ---------------------- Custom Widgets ----------------------

class ComboboxSearchable(ttk.Combobox):
    """A Combobox that filters its values based on user typing."""
    def __init__(self, master, **kwargs):
        super().__init__(master, **kwargs)
        if 'width' not in kwargs:
            self['width'] = 20
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
    """Dynamic list editor for simple arrays (strings)."""
    def __init__(self, parent, field_name, initial_list=None, dirty_callback=None, **kwargs):
        super().__init__(parent, **kwargs)
        self.field_name = field_name
        self.dirty_callback = dirty_callback
        self.entries = [] 
        self.list_frame = ttk.Frame(self)
        self.list_frame.pack(fill=tk.X, pady=2)
        
        add_btn = ttk.Button(self, text="+ Add", command=self.add_entry, width=8)
        add_btn.pack(anchor=tk.W, pady=(0,5))
        
        if initial_list:
            for item in initial_list:
                self.add_entry(initial_value=item)
    
    def add_entry(self, initial_value=""):
        if self.dirty_callback: self.dirty_callback()
        row_frame = ttk.Frame(self.list_frame)
        row_frame.pack(fill=tk.X, pady=2)
        
        entry = ttk.Entry(row_frame, width=30)
        entry.pack(side=tk.LEFT, padx=(0,5))
        entry.insert(0, initial_value)
        entry.bind("<KeyRelease>", lambda e: self.dirty_callback())
        
        remove_btn = ttk.Button(row_frame, text="✖", width=3, command=lambda: self.remove_entry(row_frame))
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
    """Editor for the 'players' dict object (Coach, GK, Def, etc.)."""
    def __init__(self, parent, field_name, initial_dict=None, dirty_callback=None, **kwargs):
        super().__init__(parent, **kwargs)
        self.field_name = field_name
        self.editors = {} 
        self.sub_keys = ["coach", "goalkeepers", "defenders", "midfielders", "attackers"]
        
        if initial_dict is None:
            initial_dict = {}

        for key in self.sub_keys:
            frame = ttk.Frame(self)
            frame.pack(fill=tk.X, pady=2)
            
            lbl = ttk.Label(frame, text=key.title() + ":", width=12, anchor="w")
            lbl.pack(side=tk.TOP, anchor="w")
            
            val_list = initial_dict.get(key)
            if val_list is None:
                val_list = []
                
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

# ---------------------- Match Editor Tab ----------------------

class MatchEditorTab(ttk.Frame):
    def __init__(self, parent, data, team_map, venue_list, save_callback, icons={}, dirty_callback=None):
        super().__init__(parent)
        self.data = data
        self.team_map = team_map
        self.venue_list = venue_list
        self.save_callback = save_callback
        self.icons = icons
        self.dirty_callback = dirty_callback
        self.current_match = None
        self.is_dirty = False
        
        self.pack(fill=tk.BOTH, expand=True)
        self.create_widgets()
        self.refresh_match_tree()

    def _mark_dirty(self, event=None):
        self.is_dirty = True
        if self.dirty_callback:
            self.dirty_callback(True)

    def _check_unsaved_changes(self):
        if not self.is_dirty:
            return True
        response = messagebox.askyesnocancel("Unsaved Changes", "You have unsaved changes. Do you want to save them before continuing?")
        if response is True:
            self.save_current_match()
            return True
        elif response is False:
            self.is_dirty = False
            return True
        else:
            return False

    def create_widgets(self):
        # Use grid for main layout to give left frame a fixed width
        self.columnconfigure(0, weight=0)   # left frame – fixed width
        self.columnconfigure(1, weight=1)   # right frame – expands
        self.rowconfigure(0, weight=1)

        # --- LEFT SIDE: LIST & BUTTONS (fixed width) ---
        paned = ttk.Panedwindow(self, orient=tk.HORIZONTAL)
        paned.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        left_frame = ttk.Frame(paned, width=320, relief="sunken")
        right_frame = ttk.Frame(paned)
        paned.add(left_frame, weight=1)
        paned.add(right_frame, weight=3)
        left_frame.columnconfigure(0, weight=1)
        left_frame.rowconfigure(0, weight=0)   # label
        left_frame.rowconfigure(1, weight=1)   # tree
        left_frame.rowconfigure(2, weight=0)   # buttons

        ttk.Label(left_frame, text="Matches by Date", font=("Segoe UI", 12, "bold")).grid(row=0, column=0, pady=5, sticky="ew")
        
        tree_frame = ttk.Frame(left_frame)
        tree_frame.grid(row=1, column=0, sticky="nsew", pady=(0, 10))
        tree_frame.columnconfigure(0, weight=1)
        tree_frame.rowconfigure(0, weight=1)
        
        self.match_tree = ttk.Treeview(tree_frame, selectmode="browse", show="tree")
        vsb = ttk.Scrollbar(tree_frame, orient="vertical", command=self.match_tree.yview)
        self.match_tree.configure(yscrollcommand=vsb.set)
        self.match_tree.grid(row=0, column=0, sticky="nsew")
        vsb.grid(row=0, column=1, sticky="ns")
        
        self.match_tree.bind("<<TreeviewSelect>>", self.on_match_select)
        
        # --- BUTTONS (New, Duplicate, Delete, Collapse, Expand) ---
        btn_frame = ttk.Frame(left_frame)
        btn_frame.grid(row=2, column=0, pady=5)
        
        # Row 0: New, Duplicate
        ttk.Button(btn_frame, text="New", image=self.icons.get('new'), compound=tk.LEFT, command=self.new_match).grid(row=0, column=0, padx=5, pady=2)
        ttk.Button(btn_frame, text="Duplicate", image=self.icons.get('duplicate'), compound=tk.LEFT, command=self.duplicate_match).grid(row=0, column=1, padx=5, pady=2)
        
        # Row 1: Delete, Collapse, Expand
        ttk.Button(btn_frame, text="Delete", image=self.icons.get('delete'), compound=tk.LEFT, command=self.delete_match).grid(row=1, column=0, padx=5, pady=2)
        ttk.Button(btn_frame, text="Collapse", image=self.icons.get('collapse'), compound=tk.LEFT, command=self.collapse_all_dates).grid(row=1, column=1, padx=5, pady=2)
        ttk.Button(btn_frame, text="Expand", image=self.icons.get('expand'), compound=tk.LEFT, command=self.expand_all_dates).grid(row=1, column=2, padx=5, pady=2)

        # --- RIGHT SIDE: FORM (with horizontal scrollbar) ---
        right_frame.columnconfigure(0, weight=1)
        right_frame.rowconfigure(0, weight=1)
        
        # Create canvas with both scrollbars
        canvas = tk.Canvas(right_frame, highlightthickness=0)
        h_scrollbar = ttk.Scrollbar(right_frame, orient="horizontal", command=canvas.xview)
        v_scrollbar = ttk.Scrollbar(right_frame, orient="vertical", command=canvas.yview)
        canvas.configure(xscrollcommand=h_scrollbar.set, yscrollcommand=v_scrollbar.set)
        
        canvas.grid(row=0, column=0, sticky="nsew")
        h_scrollbar.grid(row=1, column=0, sticky="ew")
        v_scrollbar.grid(row=0, column=1, sticky="ns")
        
        right_frame.columnconfigure(0, weight=1)
        right_frame.rowconfigure(0, weight=1)
        
        scrollable_frame = ttk.Frame(canvas)
        scrollable_frame.columnconfigure(0, weight=0)
        scrollable_frame.columnconfigure(1, weight=1)
        scrollable_frame.columnconfigure(2, weight=1)
        scrollable_frame.columnconfigure(3, weight=1)
        window_id = canvas.create_window((0, 0), window=scrollable_frame, anchor="nw")
        
        def _configure_scroll_region(event):
            canvas.configure(scrollregion=canvas.bbox("all"))
            canvas.itemconfigure(window_id, width=canvas.winfo_width())
        scrollable_frame.bind("<Configure>", _configure_scroll_region)
        canvas.bind("<Configure>", lambda e: canvas.itemconfigure(window_id, width=e.width))
        
        # Mouse wheel binding for vertical scroll
        def _on_mousewheel(event):
            canvas.yview_scroll(int(-1*(event.delta/120)), "units")
        canvas.bind_all("<MouseWheel>", _on_mousewheel)
        canvas.bind("<Button-1>", lambda e: canvas.focus_set())
        scrollable_frame.bind("<Button-1>", lambda e: canvas.focus_set())

        self.widgets = {}
        
        status_options = ["upcoming", "completed", "delayed"]
        stage_options = ["", "1", "2", "knockout"]

        # --- Simple fields (excluding home/away team and scores) ---
        simple_fields = [
            ("match_id", "Match ID:", "text"), ("group", "Group:", "text"), ("week", "Week:", "text"),
            ("date", "Date:", "date"), ("time", "Time (HH:MM):", "text"),
            ("venue", "Venue:", "venue_dropdown"),
            ("status", "Status:", "status_dropdown"), ("stage", "Stage:", "stage_dropdown"), ("note", "Note:", "text"),
        ]
        
        scrollable_frame.columnconfigure(0, weight=0)
        scrollable_frame.columnconfigure(1, weight=1)
        scrollable_frame.columnconfigure(2, weight=1)
        scrollable_frame.columnconfigure(3, weight=1)
        
        row = 0
        for field, label, ftype in simple_fields:
            lbl = ttk.Label(scrollable_frame, text=label, width=25, anchor="e")
            lbl.grid(row=row, column=0, padx=5, pady=2, sticky="e")
            widget = None
            if ftype == "date":
                widget = DateEntry(scrollable_frame, width=20, date_pattern='yyyy-mm-dd')
                widget.bind("<<DateEntrySelected>>", self._mark_dirty)
            elif ftype == "venue_dropdown":
                widget = ComboboxSearchable(scrollable_frame, values=self.venue_list, width=35)
                widget.bind("<<ComboboxSelected>>", self._mark_dirty)
                widget.bind("<KeyRelease>", self._mark_dirty)
            elif ftype in ("status_dropdown", "stage_dropdown"):
                values = status_options if ftype == "status_dropdown" else stage_options
                widget = ttk.Combobox(scrollable_frame, values=values, width=33)
                widget.bind("<<ComboboxSelected>>", self._mark_dirty)
            else:
                widget = ttk.Entry(scrollable_frame)
                widget.bind("<KeyRelease>", self._mark_dirty)
            
            widget.grid(row=row, column=1, padx=5, pady=2, sticky="ew", columnspan=3)
            self.widgets[field] = widget
            row += 1
        
        # --- HOME vs AWAY header ---
        ttk.Label(scrollable_frame, text="HOME", font=("Segoe UI", 10, "bold", "underline"), foreground="blue").grid(row=row, column=1, sticky="w", padx=10, pady=(15,5))
        ttk.Label(scrollable_frame, text="AWAY", font=("Segoe UI", 10, "bold", "underline"), foreground="red").grid(row=row, column=3, sticky="w", padx=10, pady=(15,5))
        row += 1

        # --- Team row (Home Team vs Away Team) ---
        ttk.Label(scrollable_frame, text="Team:", anchor="e").grid(row=row, column=0, padx=5, pady=2, sticky="e")
        home_team_cb = ComboboxSearchable(scrollable_frame, values=list(self.team_map.keys()), width=35)
        home_team_cb.bind("<<ComboboxSelected>>", self._mark_dirty)
        home_team_cb.bind("<KeyRelease>", self._mark_dirty)
        home_team_cb.grid(row=row, column=1, padx=5, pady=2, sticky="ew")
        self.widgets["home_team_id"] = home_team_cb

        ttk.Label(scrollable_frame, text="Team:", anchor="e").grid(row=row, column=2, padx=5, pady=2, sticky="e")
        away_team_cb = ComboboxSearchable(scrollable_frame, values=list(self.team_map.keys()), width=35)
        away_team_cb.bind("<<ComboboxSelected>>", self._mark_dirty)
        away_team_cb.bind("<KeyRelease>", self._mark_dirty)
        away_team_cb.grid(row=row, column=3, padx=5, pady=2, sticky="ew")
        self.widgets["away_team_id"] = away_team_cb
        row += 1

        # --- Score row (Home Score vs Away Score) ---
        ttk.Label(scrollable_frame, text="Score:", anchor="e").grid(row=row, column=0, padx=5, pady=2, sticky="e")
        home_score_entry = ttk.Entry(scrollable_frame, width=37)
        home_score_entry.bind("<KeyRelease>", self._mark_dirty)
        home_score_entry.grid(row=row, column=1, padx=5, pady=2, sticky="ew")
        self.widgets["home_score"] = home_score_entry

        ttk.Label(scrollable_frame, text="Score:", anchor="e").grid(row=row, column=2, padx=5, pady=2, sticky="e")
        away_score_entry = ttk.Entry(scrollable_frame, width=37)
        away_score_entry.bind("<KeyRelease>", self._mark_dirty)
        away_score_entry.grid(row=row, column=3, padx=5, pady=2, sticky="ew")
        self.widgets["away_score"] = away_score_entry
        row += 1

        # --- Penalty row (Home Penalty vs Away Penalty) ---
        ttk.Label(scrollable_frame, text="Penalty:", anchor="e").grid(row=row, column=0, padx=5, pady=2, sticky="e")
        home_penalty_entry = ttk.Entry(scrollable_frame, width=37)
        home_penalty_entry.bind("<KeyRelease>", self._mark_dirty)
        home_penalty_entry.grid(row=row, column=1, padx=5, pady=2, sticky="ew")
        self.widgets["home_penalty"] = home_penalty_entry

        ttk.Label(scrollable_frame, text="Penalty:", anchor="e").grid(row=row, column=2, padx=5, pady=2, sticky="e")
        away_penalty_entry = ttk.Entry(scrollable_frame, width=37)
        away_penalty_entry.bind("<KeyRelease>", self._mark_dirty)
        away_penalty_entry.grid(row=row, column=3, padx=5, pady=2, sticky="ew")
        self.widgets["away_penalty"] = away_penalty_entry
        row += 1

        # --- Array fields (Squad, Scorers, Yellow/Red cards, Substitutes) ---
        array_pairs = [
            ("home_squade", "away_squade", "Squad"),
            ("home_scorers", "away_scorers", "Scorers"),
            ("home_yc", "away_yc", "Yellow Cards"),
            ("home_rc", "away_rc", "Red Cards"),
            ("home_sub", "away_sub", "Substitutes")
        ]

        scrollable_frame.columnconfigure(0, weight=0)
        scrollable_frame.columnconfigure(1, weight=1)
        scrollable_frame.columnconfigure(2, weight=0)
        scrollable_frame.columnconfigure(3, weight=1)

        for home_field, away_field, suffix in array_pairs:
            ttk.Frame(scrollable_frame, height=10).grid(row=row, column=0, columnspan=4)
            row += 1
            
            lbl_home = ttk.Label(scrollable_frame, text=f"Home {suffix}:", anchor="e")
            lbl_home.grid(row=row, column=0, padx=5, pady=2, sticky="ne")
            
            editor_home = ArrayFieldEditor(scrollable_frame, home_field, dirty_callback=self._mark_dirty)
            editor_home.grid(row=row, column=1, padx=5, pady=2, sticky="nsew")
            self.widgets[home_field] = editor_home
            
            lbl_away = ttk.Label(scrollable_frame, text=f"Away {suffix}:", anchor="e")
            lbl_away.grid(row=row, column=2, padx=5, pady=2, sticky="ne")
            
            editor_away = ArrayFieldEditor(scrollable_frame, away_field, dirty_callback=self._mark_dirty)
            editor_away.grid(row=row, column=3, padx=5, pady=2, sticky="nsew")
            self.widgets[away_field] = editor_away
            
            row += 1

        ttk.Button(scrollable_frame, text="Save Current Match", image=self.icons.get('save'), compound=tk.LEFT, command=self.save_current_match).grid(row=row, column=0, columnspan=4, pady=20)

    def refresh_match_tree(self):
        self.match_tree.delete(*self.match_tree.get_children())
        matches_by_date = {}
        for match in self.data.get("matches", []):
            date = match.get("date", "Unknown")
            if date not in matches_by_date:
                matches_by_date[date] = []
            matches_by_date[date].append(match)
        
        for date in sorted(matches_by_date.keys()):
            try:
                dt = datetime.strptime(date, "%Y-%m-%d")
                display_date = f"{date} ({dt.strftime('%A')})"
            except:
                display_date = date
            
            date_node = self.match_tree.insert("", "end", text=display_date, open=True)
            for match in sorted(matches_by_date[date], key=lambda m: m.get("time", "")):
                home_name = self.get_team_name(match.get("home_team_id")) or "?"
                away_name = self.get_team_name(match.get("away_team_id")) or "?"
                match_text = f"{match.get('match_id')} - {home_name} vs {away_name}"
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
                if match.get("match_id") == match_id: return match
        return None

    def on_match_select(self, event):
        if not self._check_unsaved_changes(): return
        match = self.get_match_by_tree_selection()
        if match:
            self.current_match = match
            self.populate_form(match)

    def get_team_name(self, team_id):
        for name, tid in self.team_map.items():
            if tid == team_id: return name
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
                widget.set(name)
            elif isinstance(widget, ArrayFieldEditor):
                widget.set_value(value if value else [])
            elif isinstance(widget, ttk.Combobox):
                widget.set(str(value) if value is not None else "")
            else:
                widget.delete(0, tk.END)
                widget.insert(0, str(value) if value not in (None, "") else "")
        self.is_dirty = False

    def new_match(self):
        if not self._check_unsaved_changes(): return
        
        existing_ids = [m.get("match_id", "") for m in self.data.get("matches", []) if m.get("match_id")]
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
            "away_scorers": None, "home_penalty": None, "away_penalty": None,
            "home_yc": None, "away_yc": None,
            "home_rc": None, "away_rc": None, "home_sub": None, "away_sub": None, "stage": ""
        }
        self.current_match = new_match
        self.populate_form(new_match)
        self.is_dirty = True

    def duplicate_match(self):
        if not self.current_match:
            messagebox.showwarning("Warning", "Please select a match to duplicate.")
            return
        if not self._check_unsaved_changes(): return

        duplicated_match = copy.deepcopy(self.current_match)
        existing_ids = [m.get("match_id", "") for m in self.data.get("matches", [])]
        max_num = 0
        for mid in existing_ids:
            if mid.startswith("m") and mid[1:].isdigit():
                max_num = max(max_num, int(mid[1:]))
        new_id = f"m{max_num+1:03d}"
        duplicated_match['match_id'] = new_id

        self.current_match = duplicated_match
        self.populate_form(self.current_match)
        self.is_dirty = True
        messagebox.showinfo("Duplicated", f"Match duplicated with new ID: {new_id}.\nReview and save.")

    def save_current_match(self):
        if not self.current_match:
            messagebox.showwarning("Warning", "No match selected.")
            return
        
        updated = {}
        for field, widget in self.widgets.items():
            if field == "date": 
                value = widget.get_date().strftime("%Y-%m-%d")
            elif field in ("home_team_id", "away_team_id"): 
                value = self.team_map.get(widget.get(), "")
            elif field == "venue":
                value = widget.get().strip()
                if value and value not in self.venue_list:
                    self.venue_list.append(value)
                    self.data["venues"] = sorted(self.venue_list)
                    self.widgets["venue"].update_values(self.venue_list)
            elif field in ("home_score", "away_score", "home_penalty", "away_penalty"):
                txt = widget.get().strip()
                value = int(txt) if txt.isdigit() else None
            elif isinstance(widget, ArrayFieldEditor): 
                value = widget.get_value()
            else:
                value = widget.get().strip()
                if field in ("note", "stage") and not value:
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
        messagebox.showinfo("Success", "Match saved successfully.")

    def delete_match(self):
        if not self.current_match: return
        match_id = self.current_match.get("match_id")
        if messagebox.askyesno("Confirm", f"Delete match {match_id}?"):
            self.data["matches"] = [m for m in self.data["matches"] if m.get("match_id") != match_id]
            self.current_match = None
            self.populate_form({})
            self.refresh_match_tree()
            self.save_callback()
            self.is_dirty = False
            messagebox.showinfo("Deleted", f"Match {match_id} removed.")

    

# ---------------------- Team Editor Tab ----------------------

class TeamEditorTab(ttk.Frame):
    def __init__(self, parent, data, save_callback, refresh_team_map_callback, icons={}, dirty_callback=None):
        super().__init__(parent)
        self.data = data
        self.save_callback = save_callback
        self.refresh_team_map_callback = refresh_team_map_callback
        self.icons = icons
        self.dirty_callback = dirty_callback
        self.current_team = None
        self.is_dirty = False
        
        self.pack(fill=tk.BOTH, expand=True)
        self.create_widgets()
        self.refresh_team_list()

    def _mark_dirty(self, event=None):
        self.is_dirty = True
        if self.dirty_callback:
            self.dirty_callback(True)
        
    def _check_unsaved_changes(self):
        if not self.is_dirty:
            return True
        response = messagebox.askyesnocancel("Unsaved Changes", "You have unsaved changes. Do you want to save them before continuing?")
        if response is True:
            self.save_current_team()
            return True
        elif response is False:
            self.is_dirty = False
            return True
        else:
            return False

    def create_widgets(self):
        self.columnconfigure(0, weight=0)
        self.columnconfigure(1, weight=1)
        self.rowconfigure(0, weight=1)

        left_frame = ttk.Frame(self, width=280, padding=(5, 5))
        right_frame = ttk.Frame(self, padding=(5, 5))
        left_frame.grid(row=0, column=0, sticky="nsew")
        right_frame.grid(row=0, column=1, sticky="nsew")
        left_frame.grid_propagate(False)

        ttk.Label(left_frame, text="Teams", font=("Segoe UI", 12, "bold")).pack(pady=5)

        list_frame = ttk.Frame(left_frame)
        list_frame.pack(fill=tk.BOTH, expand=True)

        self.team_listbox = tk.Listbox(list_frame, width=40, font=("Segoe UI", 10), relief="flat", borderwidth=1)
        vsb = ttk.Scrollbar(list_frame, orient="vertical", command=self.team_listbox.yview)
        self.team_listbox.configure(yscrollcommand=vsb.set)
        self.team_listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        vsb.pack(side=tk.RIGHT, fill=tk.Y)

        self.team_listbox.bind("<<ListboxSelect>>", self.on_team_select)

        btn_frame = ttk.Frame(left_frame)
        btn_frame.pack(pady=10)
        ttk.Button(btn_frame, text="New", image=self.icons.get('new'), compound=tk.LEFT, command=self.new_team).pack(side=tk.LEFT, padx=5)
        ttk.Button(btn_frame, text="Duplicate", image=self.icons.get('duplicate'), compound=tk.LEFT, command=self.duplicate_team).pack(side=tk.LEFT, padx=5)
        ttk.Button(btn_frame, text="Delete", image=self.icons.get('delete'), compound=tk.LEFT, command=self.delete_team).pack(side=tk.LEFT, padx=5)

        right_frame.columnconfigure(0, weight=1)
        right_frame.rowconfigure(0, weight=1)

        canvas = tk.Canvas(right_frame, highlightthickness=0)
        scrollbar = ttk.Scrollbar(right_frame, orient="vertical", command=canvas.yview)
        scrollable_frame = ttk.Frame(canvas)
        scrollable_frame.columnconfigure(0, weight=0)
        scrollable_frame.columnconfigure(1, weight=1)

        window_id = canvas.create_window((0, 0), window=scrollable_frame, anchor="nw")
        canvas.configure(yscrollcommand=scrollbar.set)

        def _resize_scrollable(event):
            canvas.configure(scrollregion=canvas.bbox("all"))
            canvas.itemconfigure(window_id, width=canvas.winfo_width())
        scrollable_frame.bind("<Configure>", _resize_scrollable)
        canvas.bind("<Configure>", lambda e: canvas.itemconfigure(window_id, width=e.width))

        canvas.grid(row=0, column=0, sticky="nsew")
        scrollbar.grid(row=0, column=1, sticky="ns")
        canvas.bind_all("<MouseWheel>", lambda e: canvas.yview_scroll(int(-1*(e.delta/120)), "units"))
        canvas.bind("<Button-1>", lambda e: canvas.focus_set())
        scrollable_frame.bind("<Button-1>", lambda e: canvas.focus_set())

        self.widgets = {}
        fields = [
            ("team_id", "Team ID:", "text"), ("name", "Name:", "text"), ("group", "Group:", "text"),
            ("logo", "Logo URL:", "text"), ("field", "Field:", "text"), ("fieldurl", "Field URL:", "text"),
            ("city", "City:", "text"), ("information", "Information:", "text_multiline"), ("point_deduction", "Point Deduction:", "int")
        ]
        
        row = 0
        for field, label, ftype in fields:
            ttk.Label(scrollable_frame, text=label, width=15, anchor="e").grid(row=row, column=0, padx=5, pady=5, sticky="ne")
            widget = None
            if ftype == "text_multiline":
                widget = tk.Text(scrollable_frame, width=50, height=4, relief="flat", font=("Segoe UI", 10))
                widget.bind("<KeyRelease>", self._mark_dirty)
            else:
                widget = ttk.Entry(scrollable_frame, width=52)
                widget.bind("<KeyRelease>", self._mark_dirty)
            
            widget.grid(row=row, column=1, padx=5, pady=5, sticky="w")
            self.widgets[field] = widget
            row += 1
            
        ttk.Label(scrollable_frame, text="Players Squad:", font=("Segoe UI", 10, "bold")).grid(row=row, column=0, columnspan=2, sticky="w", pady=(10, 5))
        row += 1
        
        self.players_editor = DictFieldEditor(scrollable_frame, "players", dirty_callback=self._mark_dirty)
        self.players_editor.grid(row=row, column=0, columnspan=2, sticky="ew")
        row += 1
        
        ttk.Button(scrollable_frame, text="Save Team", image=self.icons.get('save'), compound=tk.LEFT, command=self.save_current_team).grid(row=row, column=0, columnspan=2, pady=20)

    def refresh_team_list(self):
        current_selection_id = self.current_team.get("team_id") if self.current_team else None
        self.team_listbox.delete(0, tk.END)
        self.sorted_teams = sorted(self.data.get("teams", []), key=lambda x: x.get("name", ""))
        new_selection_index = -1
        for i, team in enumerate(self.sorted_teams):
            display_text = f"{team.get('team_id', '')} - {team.get('name', '')}"
            self.team_listbox.insert(tk.END, display_text)
            if team.get("team_id") == current_selection_id:
                new_selection_index = i
        
        if new_selection_index != -1:
            self.team_listbox.selection_set(new_selection_index)
            self.team_listbox.activate(new_selection_index)
            self.team_listbox.see(new_selection_index)

    def on_team_select(self, event):
        if not self._check_unsaved_changes(): return
        selection = self.team_listbox.curselection()
        if not selection: return
        self.current_team = self.sorted_teams[selection[0]]
        self.populate_form(self.current_team)

    def populate_form(self, team):
        for field, widget in self.widgets.items():
            value = team.get(field)
            if isinstance(widget, tk.Text):
                widget.delete(1.0, tk.END)
                if value: widget.insert(1.0, str(value))
            else:
                widget.delete(0, tk.END)
                if value is not None: widget.insert(0, str(value))
        
        self.players_editor.set_value(team.get("players"))
        self.is_dirty = False

    def new_team(self):
        if not self._check_unsaved_changes(): return
        new_team = {
            "team_id": "", "name": "", "group": "", "logo": None, "field": None, "fieldurl": None, 
            "city": None, "information": None, "players": {}, "point_deduction": None
        }
        self.current_team = new_team
        self.populate_form(new_team)
        self.is_dirty = True

    def duplicate_team(self):
        if not self.current_team:
            messagebox.showwarning("Warning", "Please select a team to duplicate.")
            return
        if not self._check_unsaved_changes(): return

        duplicated_team = copy.deepcopy(self.current_team)
        duplicated_team['team_id'] = ""
        duplicated_team['name'] = f"{duplicated_team.get('name', '')} (Copy)"

        self.current_team = duplicated_team
        self.populate_form(self.current_team)
        self.is_dirty = True
        messagebox.showinfo("Duplicated", "Team duplicated.\nPlease provide a new unique Team ID and save.")

    def save_current_team(self):
        if not self.current_team:
            messagebox.showwarning("Warning", "No team loaded.")
            return
        
        updated = {}
        for field, widget in self.widgets.items():
            if isinstance(widget, tk.Text): value = widget.get(1.0, tk.END).strip()
            elif field == "point_deduction":
                txt = widget.get().strip()
                value = int(txt) if txt.isdigit() else None
            else: value = widget.get().strip()
            
            if not value and field not in ["team_id", "name", "point_deduction"]:
                value = None
            
            updated[field] = value
        
        updated["players"] = self.players_editor.get_value()
        
        if not updated.get("team_id"):
            messagebox.showerror("Error", "Team ID cannot be empty.")
            return

        original_id = self.current_team.get("team_id")
        teams = self.data.get("teams", [])
        found = False
        if original_id:
            for i, t in enumerate(teams):
                if t.get("team_id") == original_id:
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
        messagebox.showinfo("Success", "Team saved successfully.")

    def delete_team(self):
        if not self.current_team: return
        tid = self.current_team.get("team_id")
        if messagebox.askyesno("Confirm", f"Delete team {tid}?"):
            self.data["teams"] = [t for t in self.data["teams"] if t.get("team_id") != tid]
            self.current_team = None
            self.populate_form({})
            self.save_callback()
            self.refresh_team_map_callback()
            self.refresh_team_list()
            self.is_dirty = False

# ---------------------- Main Application ----------------------

class MainApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Football Data Manager")
        
        screen_width = self.root.winfo_screenwidth()
        screen_height = self.root.winfo_screenheight()
        
        width = screen_width // 2
        height = screen_height - 80
        x = (screen_width - width) // 2
        y = 40
        self.root.geometry(f"{width}x{height}+{x}+{y}")
        
        self.setup_styles()
        self.load_icons()

        self._bind_text_shortcuts()
        
        self.data = None
        self.file_path = None
        self.json_folder_path = None
        self.available_json_files = []
        
        self.toolbar = ttk.Frame(root, padding=5)
        self.toolbar.pack(side=tk.TOP, fill=tk.X)
        
        ttk.Button(self.toolbar, text="Open File", command=self.open_file).pack(side=tk.LEFT, padx=5)
        ttk.Button(self.toolbar, text="Open Folder", command=self.open_folder).pack(side=tk.LEFT, padx=5)
        ttk.Button(self.toolbar, text="Save As", command=self.save_as_file).pack(side=tk.LEFT, padx=5)
        ttk.Button(self.toolbar, text="Refresh Files", command=self.refresh_file_list).pack(side=tk.LEFT, padx=5)
        
        self.content_frame = ttk.Panedwindow(root, orient=tk.HORIZONTAL)
        self.content_frame.pack(side=tk.TOP, fill=tk.BOTH, expand=True)
        self.file_list_frame = ttk.Frame(self.content_frame, padding=(5,5))
        self.notebook_frame = ttk.Frame(self.content_frame)
        self.content_frame.add(self.file_list_frame, weight=1)
        self.content_frame.add(self.notebook_frame, weight=3)
        self.file_list_frame.configure(width=260)
        self.notebook_frame.configure(width=640)
        
        ttk.Label(self.file_list_frame, text="JSON Files", font=("Segoe UI", 11, "bold")).pack(pady=(0,5))
        self.folder_label = ttk.Label(self.file_list_frame, text="No folder loaded", wraplength=240, justify="left")
        self.folder_label.pack(padx=5, pady=(0,10))
        self.file_listbox = tk.Listbox(self.file_list_frame, activestyle="none")
        self.file_listbox.pack(fill=tk.BOTH, expand=True)
        self.file_listbox.bind("<<ListboxSelect>>", self.on_file_select)
        
        self.notebook = ttk.Notebook(self.notebook_frame)
        self.notebook.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
        
        self.status_frame = ttk.Frame(root, padding=(5, 4))
        self.status_frame.pack(side=tk.BOTTOM, fill=tk.X)
        self.status_label = ttk.Label(self.status_frame, text="No file loaded", anchor="w")
        self.status_label.pack(fill=tk.X)
        self.dirty_state = False
        
        if not self.load_initial_file():
            self.root.destroy()
            
    def setup_styles(self):
        self.style = ttk.Style()
        self.style.theme_use('clam')
        
        self.default_font = font.nametofont("TkDefaultFont")
        self.default_font.configure(family="Segoe UI", size=10)
        
        BG_COLOR = "#f0f0f0"
        self.root.configure(background=BG_COLOR)
        
        self.style.configure('.', background=BG_COLOR, font=self.default_font)
        self.style.configure('TFrame', background=BG_COLOR)
        self.style.configure('TLabel', background=BG_COLOR, padding=5)
        self.style.configure('TButton', padding=5, borderwidth=1)
        self.style.configure("Treeview", rowheight=25, fieldbackground=BG_COLOR)
        self.style.map("Treeview", background=[('selected', '#0078d7')])
        self.style.configure("Treeview.Heading", font=("Segoe UI", 10, 'bold'))

    def load_icons(self):
        self.icons = {}
        icon_names = ['new', 'duplicate', 'delete', 'collapse', 'save', 'expand']
        script_dir = os.path.dirname(os.path.abspath(__file__))
        for name in icon_names:
            try:
                icon_path = os.path.join(script_dir, 'icons', f'{name}.png')
                image = Image.open(icon_path).resize((16, 16), Image.Resampling.LANCZOS)
                self.icons[name] = ImageTk.PhotoImage(image)
            except Exception as e:
                print(f"Warning: Could not load icon '{icon_path}'. {e}")
        
    def _bind_text_shortcuts(self):
        shortcut_bindings = {
            '<Control-a>': lambda e: e.widget.select_range(0, 'end') if hasattr(e.widget, 'select_range') else e.widget.tag_add('sel', '1.0', 'end'),
            '<Control-c>': lambda e: e.widget.event_generate('<<Copy>>'),
            '<Control-x>': lambda e: e.widget.event_generate('<<Cut>>'),
            '<Control-v>': lambda e: e.widget.event_generate('<<Paste>>'),
        }

        widget_classes = ['Entry', 'Text', 'TEntry', 'TCombobox', 'Combobox']
        for widget_class in widget_classes:
            for sequence, command in shortcut_bindings.items():
                self.root.bind_class(widget_class, sequence, command)

    def _normalize_data(self, data):
        if not isinstance(data, dict):
            data = {}

        normalized = dict(data)
        for key in ["matches", "teams", "venues"]:
            value = normalized.get(key)
            if value is None:
                normalized[key] = []
            elif not isinstance(value, list):
                normalized[key] = []
        return normalized

    def load_initial_file(self):
        file_path = filedialog.askopenfilename(title="Select JSON file", filetypes=[("JSON files", "*.json")])
        if not file_path:
            return False
        if self._load_file_data(file_path):
            folder = os.path.dirname(file_path)
            self.json_folder_path = folder
            self.available_json_files = self._find_json_files(folder)
            self.update_folder_label()
            self.refresh_file_list()
            self.rebuild_ui()
            self.update_status(file_path=self.file_path, dirty=False)
            return True
        return False

    def open_file(self):
        if not self._check_unsaved_current_tab():
            return
        file_path = filedialog.askopenfilename(title="Select JSON file", filetypes=[("JSON files", "*.json")])
        if file_path and self._load_file_data(file_path):
            folder = os.path.dirname(file_path)
            self.json_folder_path = folder
            self.available_json_files = self._find_json_files(folder)
            self.update_folder_label()
            self.refresh_file_list()
            self.rebuild_ui()
            self.update_status(file_path=self.file_path, dirty=False)

    def open_folder(self):
        if not self._check_unsaved_current_tab():
            return
        folder = filedialog.askdirectory(title="Select folder containing JSON files")
        if not folder:
            return
        self.json_folder_path = folder
        self.available_json_files = self._find_json_files(folder)
        self.update_folder_label()
        if not self.available_json_files:
            messagebox.showinfo("No JSON files", "No JSON files were found in the selected folder.")
            self.file_listbox.delete(0, tk.END)
            self.file_path = None
            self.data = None
            for tab in self.notebook.tabs():
                self.notebook.forget(tab)
            self.update_status(dirty=False)
            return
        self.refresh_file_list()
        self.file_listbox.selection_set(0)
        if self._load_file_data(self.available_json_files[0]):
            self.rebuild_ui()
            self.update_status(file_path=self.file_path, dirty=False)

    def refresh_file_list(self):
        self.file_listbox.delete(0, tk.END)
        for path in self.available_json_files:
            display = os.path.relpath(path, self.json_folder_path) if self.json_folder_path else os.path.basename(path)
            self.file_listbox.insert(tk.END, display)
        if self.file_path and self.file_path in self.available_json_files:
            index = self.available_json_files.index(self.file_path)
            self.file_listbox.selection_set(index)
            self.file_listbox.see(index)
        elif self.available_json_files:
            self.file_listbox.selection_set(0)
            self.file_listbox.see(0)
        self.update_status(file_path=self.file_path, dirty=self.dirty_state)

    def _find_json_files(self, folder_path):
        json_files = []
        for root_dir, dirnames, filenames in os.walk(folder_path):
            dirnames[:] = [d for d in dirnames if not d.startswith('.')]
            for filename in sorted(filenames):
                if filename.lower().endswith('.json'):
                    json_files.append(os.path.join(root_dir, filename))
        return json_files

    def update_folder_label(self):
        if self.json_folder_path:
            self.folder_label.config(text=self.json_folder_path)
        else:
            self.folder_label.config(text="No folder loaded")

    def _check_unsaved_current_tab(self):
        if not self.notebook.tabs():
            return True
        current_tab = self.notebook.select()
        if not current_tab:
            return True
        tab_widget = self.root.nametowidget(current_tab)
        if hasattr(tab_widget, '_check_unsaved_changes'):
            return tab_widget._check_unsaved_changes()
        return True

    def set_dirty_state(self, dirty=True):
        self.update_status(file_path=self.file_path, dirty=dirty)

    def save_as_file(self):
        if not self.data:
            messagebox.showwarning("Warning", "No data available to save.")
            return
        file_path = filedialog.asksaveasfilename(title="Save As", defaultextension=".json", filetypes=[("JSON files", "*.json")])
        if not file_path:
            return
        try:
            with open(file_path, 'w', encoding='utf-8') as f:
                json.dump(self.data, f, indent=2, ensure_ascii=False)
            self.file_path = file_path
            self.root.title(f"Football Data Manager - {file_path}")
            if self.json_folder_path and os.path.commonpath([self.json_folder_path, file_path]) == self.json_folder_path:
                self.available_json_files = self._find_json_files(self.json_folder_path)
                self.refresh_file_list()
            else:
                self.json_folder_path = os.path.dirname(file_path)
                self.available_json_files = self._find_json_files(self.json_folder_path)
                self.update_folder_label()
                self.refresh_file_list()
            messagebox.showinfo("Saved", f"Data saved as {file_path}.")
        except Exception as e:
            messagebox.showerror("Error", f"Failed to save file:\n{e}")

    def on_file_select(self, event):
        if not self.file_listbox.curselection():
            return
        selected_index = self.file_listbox.curselection()[0]
        file_path = self.available_json_files[selected_index]
        if file_path == self.file_path:
            return
        if not self._check_unsaved_current_tab():
            return
        if self._load_file_data(file_path):
            self.rebuild_ui()
            self.update_status(file_path=self.file_path, dirty=False)

    def _load_file_data(self, file_path):
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                self.data = self._normalize_data(json.load(f))
            self.file_path = file_path
            self.root.title(f"Football Data Manager - {file_path}")
        except Exception as e:
            messagebox.showerror("Error", f"Failed to load file:\n{e}")
            return False
        return True

    def rebuild_ui(self):
        for tab in self.notebook.tabs():
            self.notebook.forget(tab)
        self.update_data_helpers()
        self.match_tab = MatchEditorTab(
            self.notebook, self.data, self.team_map, self.venue_list,
            self.save_to_file, self.icons, dirty_callback=self.set_dirty_state)
        self.team_tab = TeamEditorTab(
            self.notebook, self.data, self.save_to_file, self.refresh_helpers_and_ui,
            self.icons, dirty_callback=self.set_dirty_state)
        self.notebook.add(self.match_tab, text="Matches")
        self.notebook.add(self.team_tab, text="Teams")

    def update_data_helpers(self):
        teams = self.data.get("teams") or []
        matches = self.data.get("matches") or []
        venues = set(self.data.get("venues") or [])

        self.team_map = {}
        for team in teams:
            if isinstance(team, dict):
                self.team_map[team.get("name", "Unknown")] = team.get("team_id")

        for match in matches:
            if isinstance(match, dict) and match.get("venue"):
                venues.add(match["venue"])

        self.venue_list = sorted(list(venues))
        self.data["venues"] = self.venue_list

    def refresh_helpers_and_ui(self):
        self.update_data_helpers()
        if hasattr(self, 'match_tab'):
            self.match_tab.team_map = self.team_map
            self.match_tab.widgets["home_team_id"].update_values(list(self.team_map.keys()))
            self.match_tab.widgets["away_team_id"].update_values(list(self.team_map.keys()))
            self.match_tab.refresh_match_tree()

    def save_to_file(self):
        if not self.file_path:
            self.save_as_file()
            return
        try:
            with open(self.file_path, 'w', encoding='utf-8') as f:
                json.dump(self.data, f, indent=2, ensure_ascii=False)
            self.update_status(file_path=self.file_path, dirty=False)
        except Exception as e:
            messagebox.showerror("Error", f"Failed to save file:\n{e}")

    def update_status(self, file_path=None, dirty=False):
        file_path = file_path or self.file_path
        title = os.path.basename(file_path) if file_path else "No file loaded"
        status = f"{title}"
        if self.json_folder_path:
            status += f"  |  {self.json_folder_path}"
        if dirty:
            status += "  [Unsaved changes]"
        self.status_label.config(text=status)
        self.dirty_state = dirty

if __name__ == "__main__":
    root = tk.Tk()
    app = MainApp(root)
    root.mainloop()