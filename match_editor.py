import json
import tkinter as tk
from tkinter import ttk, messagebox, filedialog
from tkcalendar import DateEntry
from datetime import datetime

# ---------------------- Custom Widgets ----------------------

class ComboboxSearchable(ttk.Combobox):
    """
    A Combobox that filters its values based on user typing.
    """
    def __init__(self, master, **kwargs):
        super().__init__(master, **kwargs)
        
        # Store the full list of values
        self._full_list = list(self['values'])
        
        # Bind the KeyRelease event to filter the list
        self.bind('<KeyRelease>', self._filter_list)
        # Bind FocusOut to restore the list if the user clicks away
        self.bind('<FocusOut>', self._restore_list)

    def _filter_list(self, event=None):
        """Filter the dropdown values based on current text."""
        search_term = self.get().lower()
        
        # Filter the full list
        filtered_values = [val for val in self._full_list if search_term in val.lower()]
        
        # Update the combobox values
        self['values'] = filtered_values
        
        # Auto-open the list if there are matches and it's not just deleting
        if filtered_values and event and event.keysym not in ('BackSpace', 'Delete'):
            # Only open if we have text
            if self.get():
                self.event_generate('<Button-1>') # Simulate click to open dropdown

    def _restore_list(self, event=None):
        """Restore the full list when focus is lost."""
        self['values'] = self._full_list
    
    def update_values(self, new_values):
        """Public method to update the master list of values."""
        self._full_list = list(new_values)
        self['values'] = self._full_list


class ArrayFieldEditor(ttk.Frame):
    """Dynamic list editor for simple arrays (strings)."""
    def __init__(self, parent, field_name, initial_list=None, **kwargs):
        super().__init__(parent, **kwargs)
        self.field_name = field_name
        self.entries = [] 
        self.list_frame = ttk.Frame(self)
        self.list_frame.pack(fill=tk.X, pady=2)
        
        add_btn = ttk.Button(self, text="+ Add", command=self.add_entry, width=8)
        add_btn.pack(anchor=tk.W, pady=(0,5))
        
        if initial_list:
            for item in initial_list:
                self.add_entry(initial_value=item)
    
    def add_entry(self, initial_value=""):
        row_frame = ttk.Frame(self.list_frame)
        row_frame.pack(fill=tk.X, pady=2)
        
        entry = ttk.Entry(row_frame, width=35)
        entry.pack(side=tk.LEFT, padx=(0,5))
        entry.insert(0, initial_value)
        
        remove_btn = ttk.Button(row_frame, text="✖", width=3, command=lambda: self.remove_entry(row_frame))
        remove_btn.pack(side=tk.LEFT)
        
        self.entries.append((entry, row_frame))
    
    def remove_entry(self, row_frame):
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
    def __init__(self, parent, field_name, initial_dict=None, **kwargs):
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
                
            editor = ArrayFieldEditor(frame, key, initial_list=val_list)
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
    def __init__(self, parent, data, team_map, venue_list, save_callback):
        super().__init__(parent)
        self.data = data
        self.team_map = team_map
        self.venue_list = venue_list
        self.save_callback = save_callback
        self.current_match = None
        
        self.pack(fill=tk.BOTH, expand=True)
        self.create_widgets()
        self.refresh_match_tree()

    def create_widgets(self):
        # Left panel
        left_frame = ttk.Frame(self, width=400)
        left_frame.pack(side=tk.LEFT, fill=tk.BOTH, padx=5, pady=5, expand=False)
        
        ttk.Label(left_frame, text="Matches by Date", font=("Arial", 12, "bold")).pack(pady=5)
        
        tree_frame = ttk.Frame(left_frame)
        tree_frame.pack(fill=tk.BOTH, expand=True)
        
        self.match_tree = ttk.Treeview(tree_frame, selectmode="browse", show="tree")
        vsb = ttk.Scrollbar(tree_frame, orient="vertical", command=self.match_tree.yview)
        self.match_tree.configure(yscrollcommand=vsb.set)
        self.match_tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        vsb.pack(side=tk.RIGHT, fill=tk.Y)
        
        self.match_tree.bind("<<TreeviewSelect>>", self.on_match_select)
        
        btn_frame = ttk.Frame(left_frame)
        btn_frame.pack(pady=5)
        ttk.Button(btn_frame, text="New Match", command=self.new_match).pack(side=tk.LEFT, padx=5)
        ttk.Button(btn_frame, text="Delete Match", command=self.delete_match).pack(side=tk.LEFT, padx=5)
        ttk.Button(btn_frame, text="Collapse All", command=self.collapse_all_dates).pack(side=tk.LEFT, padx=5)

        # Right panel (Scrollable)
        right_frame = ttk.Frame(self)
        right_frame.pack(side=tk.RIGHT, fill=tk.BOTH, expand=True, padx=5, pady=5)
        
        canvas = tk.Canvas(right_frame)
        scrollbar = ttk.Scrollbar(right_frame, orient="vertical", command=canvas.yview)
        scrollable_frame = ttk.Frame(canvas)
        
        # --- Mouse Wheel & Focus Fixes ---
        scrollable_frame.bind("<Configure>", lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
        canvas.create_window((0, 0), window=scrollable_frame, anchor="nw")
        canvas.configure(yscrollcommand=scrollbar.set)
        
        canvas.pack(side="left", fill="both", expand=True)
        scrollbar.pack(side="right", fill="y")

        # Mouse wheel events for Windows/MacOS and Linux
        # We bind to the canvas, not all, to prevent conflicts
        def _on_mousewheel(event):
            # Windows/MacOS
            canvas.yview_scroll(int(-1*(event.delta/120)), "units")
        
        def _on_mousewheel_linux(event):
            # Linux (Button 4 is up, 5 is down)
            if event.num == 4:
                canvas.yview_scroll(-1, "units")
            elif event.num == 5:
                canvas.yview_scroll(1, "units")

        canvas.bind("<MouseWheel>", _on_mousewheel) # Windows & MacOS
        canvas.bind("<Button-4>", _on_mousewheel_linux) # Linux scroll up
        canvas.bind("<Button-5>", _on_mousewheel_linux) # Linux scroll down
        
        # Focus fix: clicking anywhere on canvas ensures focus is there for scrolling
        canvas.bind("<Button-1>", lambda e: canvas.focus_set())
        # Also ensure scrollable_frame passes focus to canvas
        scrollable_frame.bind("<Button-1>", lambda e: canvas.focus_set())

        self.widgets = {}
        
        status_options = ["upcoming", "completed", "delayed"]
        stage_options = ["", "1", "2", "knockout"]

        simple_fields = [
            ("match_id", "Match ID:", "text"),
            ("group", "Group:", "text"),
            ("week", "Week:", "text"),
            ("date", "Date:", "date"),
            ("time", "Time (HH:MM):", "text"),
            ("home_team_id", "Home Team:", "team_dropdown"),
            ("away_team_id", "Away Team:", "team_dropdown"),
            ("venue", "Venue:", "venue_dropdown"),
            ("status", "Status:", "status_dropdown"),
            ("stage", "Stage:", "stage_dropdown"),
            ("note", "Note:", "text"),
            ("home_score", "Home Score:", "int"),
            ("away_score", "Away Score:", "int"),
        ]
        
        array_fields = [
            "home_squade", "away_squade", "home_scorers", "away_scorers",
            "home_yc", "away_yc", "home_rc", "away_rc", "home_sub", "away_sub"
        ]
        
        row = 0
        for field, label, ftype in simple_fields:
            ttk.Label(scrollable_frame, text=label, width=28, anchor="e").grid(row=row, column=0, padx=5, pady=3, sticky="e")
            
            if ftype == "date":
                widget = DateEntry(scrollable_frame, width=20, background='darkblue',
                                   foreground='white', borderwidth=2, date_pattern='yyyy-mm-dd')
                widget.grid(row=row, column=1, padx=5, pady=3, sticky="w")
                self.widgets[field] = widget
            elif ftype == "team_dropdown":
                # Use Searchable Combobox
                cb = ComboboxSearchable(scrollable_frame, values=list(self.team_map.keys()), width=40)
                cb.grid(row=row, column=1, padx=5, pady=3, sticky="w")
                self.widgets[field] = cb
            elif ftype == "venue_dropdown":
                # Use Searchable Combobox
                cb = ComboboxSearchable(scrollable_frame, values=self.venue_list, width=40)
                cb.grid(row=row, column=1, padx=5, pady=3, sticky="w")
                self.widgets[field] = cb
            elif ftype == "status_dropdown":
                cb = ttk.Combobox(scrollable_frame, values=status_options, width=38)
                cb.grid(row=row, column=1, padx=5, pady=3, sticky="w")
                self.widgets[field] = cb
            elif ftype == "stage_dropdown":
                cb = ttk.Combobox(scrollable_frame, values=stage_options, width=38)
                cb.grid(row=row, column=1, padx=5, pady=3, sticky="w")
                self.widgets[field] = cb
            elif ftype == "int":
                entry = ttk.Entry(scrollable_frame, width=42)
                entry.grid(row=row, column=1, padx=5, pady=3, sticky="w")
                self.widgets[field] = entry
            else:  # text
                entry = ttk.Entry(scrollable_frame, width=42)
                entry.grid(row=row, column=1, padx=5, pady=3, sticky="w")
                self.widgets[field] = entry
            row += 1
        
        for field in array_fields:
            label_text = field.replace("_", " ").title() + ":"
            ttk.Label(scrollable_frame, text=label_text, width=28, anchor="ne").grid(row=row, column=0, padx=5, pady=5, sticky="ne")
            array_frame = ttk.Frame(scrollable_frame)
            array_frame.grid(row=row, column=1, padx=5, pady=5, sticky="w")
            editor = ArrayFieldEditor(array_frame, field)
            editor.pack(fill=tk.X)
            self.widgets[field] = editor
            row += 1
        
        ttk.Button(scrollable_frame, text="Save Current Match", command=self.save_current_match).grid(row=row, column=0, columnspan=2, pady=20)

    # --- Tree Management ---
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
            for match in matches_by_date[date]:
                home_name = self.get_team_name(match.get("home_team_id")) or match.get("home_team_id", "?")
                away_name = self.get_team_name(match.get("away_team_id")) or match.get("away_team_id", "?")
                match_text = f"{match.get('match_id')} - {home_name} vs {away_name}"
                self.match_tree.insert(date_node, "end", text=match_text, values=(match.get('match_id'),))

    def collapse_all_dates(self):
        for child in self.match_tree.get_children():
            if self.match_tree.get_children(child):
                self.match_tree.item(child, open=False)

    def get_match_by_tree_selection(self):
        selection = self.match_tree.selection()
        if not selection: return None
        item = selection[0]
        values = self.match_tree.item(item, "values")
        if values and len(values) > 0:
            match_id = values[0]
            for match in self.data.get("matches", []):
                if match.get("match_id") == match_id:
                    return match
        return None

    def on_match_select(self, event):
        match = self.get_match_by_tree_selection()
        if match:
            self.current_match = match
            self.populate_form(match)

    def get_team_name(self, team_id):
        for team in self.data.get("teams", []):
            if team.get("team_id") == team_id:
                return team.get("name", team_id)
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
            elif field in ("venue", "status", "stage"):
                widget.set(str(value) if value is not None else "")
            elif field in ("home_score", "away_score"):
                widget.delete(0, tk.END)
                widget.insert(0, str(value) if value not in (None, "") else "")
            elif isinstance(widget, ArrayFieldEditor):
                widget.set_value(value if value else [])
            else: # Text fields
                widget.delete(0, tk.END)
                widget.insert(0, str(value) if value not in (None, "") else "")

    def new_match(self):
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
            "away_scorers": None, "home_yc": None, "away_yc": None,
            "home_rc": None, "away_rc": None, "home_sub": None, "away_sub": None, "stage": ""
        }
        self.current_match = new_match
        self.populate_form(new_match)

    def save_current_match(self):
        if not self.current_match:
            messagebox.showwarning("Warning", "No match selected.")
            return
        
        updated = {}
        for field, widget in self.widgets.items():
            if field == "date":
                value = widget.get_date().strftime("%Y-%m-%d")
            elif field in ("home_team_id", "away_team_id"):
                selected_name = widget.get()
                value = self.team_map.get(selected_name, "")
            elif field == "venue":
                value = widget.get().strip()
                if value and value not in self.venue_list:
                    self.venue_list.append(value)
                    self.data["venues"] = sorted(self.venue_list)
                    # Update the venue dropdown widget specifically
                    if "venue" in self.widgets:
                         self.widgets["venue"].update_values(self.venue_list)
            elif field in ("home_score", "away_score"):
                txt = widget.get().strip()
                value = int(txt) if txt.isdigit() else None
            elif isinstance(widget, ArrayFieldEditor):
                value = widget.get_value()
            else:
                value = widget.get().strip()
                if field == "note": value = value if value else None
            
            updated[field] = value
        
        updated["match_id"] = self.current_match.get("match_id", "")
        
        matches = self.data.get("matches", [])
        found = False
        for i, m in enumerate(matches):
            if m.get("match_id") == updated["match_id"]:
                matches[i] = updated
                found = True
                break
        if not found:
            matches.append(updated)
        
        self.data["matches"] = matches
        self.current_match = updated
        self.refresh_match_tree()
        self.save_callback()
        messagebox.showinfo("Success", "Match saved successfully.")

    def delete_match(self):
        if not self.current_match: return
        match_id = self.current_match.get("match_id")
        if messagebox.askyesno("Confirm", f"Delete match {match_id}?"):
            self.data["matches"] = [m for m in self.data.get("matches", []) if m.get("match_id") != match_id]
            self.current_match = None
            self.refresh_match_tree()
            self.save_callback()
            messagebox.showinfo("Deleted", f"Match {match_id} removed.")

# ---------------------- Team Editor Tab ----------------------

class TeamEditorTab(ttk.Frame):
    def __init__(self, parent, data, save_callback, refresh_team_map_callback):
        super().__init__(parent)
        self.data = data
        self.save_callback = save_callback
        self.refresh_team_map_callback = refresh_team_map_callback
        self.current_team = None
        
        self.pack(fill=tk.BOTH, expand=True)
        self.create_widgets()
        self.refresh_team_list()

    def create_widgets(self):
        # Left Panel: Team List
        left_frame = ttk.Frame(self, width=300)
        left_frame.pack(side=tk.LEFT, fill=tk.Y, padx=5, pady=5)
        
        ttk.Label(left_frame, text="Teams", font=("Arial", 12, "bold")).pack(pady=5)
        
        list_frame = ttk.Frame(left_frame)
        list_frame.pack(fill=tk.BOTH, expand=True)
        
        self.team_listbox = tk.Listbox(list_frame, width=40, font=("Arial", 10))
        vsb = ttk.Scrollbar(list_frame, orient="vertical", command=self.team_listbox.yview)
        self.team_listbox.configure(yscrollcommand=vsb.set)
        self.team_listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        vsb.pack(side=tk.RIGHT, fill=tk.Y)
        
        self.team_listbox.bind("<<ListboxSelect>>", self.on_team_select)
        
        btn_frame = ttk.Frame(left_frame)
        btn_frame.pack(pady=5)
        ttk.Button(btn_frame, text="New Team", command=self.new_team).pack(side=tk.LEFT, padx=5)
        ttk.Button(btn_frame, text="Delete Team", command=self.delete_team).pack(side=tk.LEFT, padx=5)

        # Right Panel: Form (Scrollable)
        right_frame = ttk.Frame(self)
        right_frame.pack(side=tk.RIGHT, fill=tk.BOTH, expand=True, padx=5, pady=5)
        
        canvas = tk.Canvas(right_frame)
        scrollbar = ttk.Scrollbar(right_frame, orient="vertical", command=canvas.yview)
        scrollable_frame = ttk.Frame(canvas)
        
        # --- Mouse Wheel & Focus Fixes ---
        scrollable_frame.bind("<Configure>", lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
        canvas.create_window((0, 0), window=scrollable_frame, anchor="nw")
        canvas.configure(yscrollcommand=scrollbar.set)
        
        canvas.pack(side="left", fill="both", expand=True)
        scrollbar.pack(side="right", fill="y")

        # Mouse wheel events
        def _on_mousewheel(event):
            canvas.yview_scroll(int(-1*(event.delta/120)), "units")
        def _on_mousewheel_linux(event):
            if event.num == 4: canvas.yview_scroll(-1, "units")
            elif event.num == 5: canvas.yview_scroll(1, "units")

        canvas.bind("<MouseWheel>", _on_mousewheel)
        canvas.bind("<Button-4>", _on_mousewheel_linux)
        canvas.bind("<Button-5>", _on_mousewheel_linux)
        canvas.bind("<Button-1>", lambda e: canvas.focus_set())
        scrollable_frame.bind("<Button-1>", lambda e: canvas.focus_set())

        self.widgets = {}
        
        fields = [
            ("team_id", "Team ID:", "text"),
            ("name", "Name:", "text"),
            ("group", "Group:", "text"),
            ("logo", "Logo URL:", "text"),
            ("field", "Field:", "text"),
            ("fieldurl", "Field URL:", "text"),
            ("city", "City:", "text"),
            ("information", "Information:", "text_multiline"),
            ("point_deduction", "Point Deduction:", "int")
        ]
        
        row = 0
        for field, label, ftype in fields:
            ttk.Label(scrollable_frame, text=label, width=15, anchor="e").grid(row=row, column=0, padx=5, pady=3, sticky="ne")
            
            if ftype == "text_multiline":
                txt = tk.Text(scrollable_frame, width=50, height=4)
                txt.grid(row=row, column=1, padx=5, pady=3, sticky="w")
                self.widgets[field] = txt
            elif ftype == "int":
                entry = ttk.Entry(scrollable_frame, width=52)
                entry.grid(row=row, column=1, padx=5, pady=3, sticky="w")
                self.widgets[field] = entry
            else:
                entry = ttk.Entry(scrollable_frame, width=52)
                entry.grid(row=row, column=1, padx=5, pady=3, sticky="w")
                self.widgets[field] = entry
            row += 1
            
        ttk.Label(scrollable_frame, text="Players Squad:", font=("Arial", 10, "bold")).grid(row=row, column=0, columnspan=2, sticky="w", pady=(10, 5))
        row += 1
        
        self.players_editor = DictFieldEditor(scrollable_frame, "players")
        self.players_editor.grid(row=row, column=0, columnspan=2, sticky="ew")
        row += 1
        
        ttk.Button(scrollable_frame, text="Save Team", command=self.save_current_team).grid(row=row, column=0, columnspan=2, pady=20)

    def refresh_team_list(self):
        self.team_listbox.delete(0, tk.END)
        teams = self.data.get("teams", [])
        teams = sorted(teams, key=lambda x: x.get("name", ""))
        for team in teams:
            self.team_listbox.insert(tk.END, f"{team.get('team_id', '')} - {team.get('name', '')}")

    def on_team_select(self, event):
        selection = self.team_listbox.curselection()
        if not selection: return
        index = selection[0]
        teams = sorted(self.data.get("teams", []), key=lambda x: x.get("name", ""))
        self.current_team = teams[index]
        self.populate_form(self.current_team)

    def populate_form(self, team):
        for field, widget in self.widgets.items():
            value = team.get(field)
            if isinstance(widget, tk.Text):
                widget.delete(1.0, tk.END)
                widget.insert(1.0, str(value) if value else "")
            elif field == "point_deduction":
                widget.delete(0, tk.END)
                widget.insert(0, str(value) if value is not None else "")
            else:
                widget.delete(0, tk.END)
                widget.insert(0, str(value) if value else "")
        
        self.players_editor.set_value(team.get("players"))

    def new_team(self):
        new_team = {
            "team_id": "", "name": "", "group": "", "logo": "",
            "field": None, "fieldurl": None, "city": None, 
            "information": "", "players": {}, "point_deduction": None
        }
        self.current_team = new_team
        self.populate_form(new_team)

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
            
            if field in ["field", "fieldurl", "city", "information", "logo", "group"]:
                if value == "": value = None
            
            updated[field] = value
        
        updated["players"] = self.players_editor.get_value()
        
        if not updated.get("team_id"):
             updated["team_id"] = self.current_team.get("team_id", "")
        
        teams = self.data.get("teams", [])
        team_id = updated["team_id"]
        found = False
        for i, t in enumerate(teams):
            if t.get("team_id") == team_id:
                teams[i] = updated
                found = True
                break
        if not found:
            teams.append(updated)
            
        self.data["teams"] = teams
        self.current_team = updated
        
        self.refresh_team_list()
        self.refresh_team_map_callback()
        self.save_callback()
        messagebox.showinfo("Success", "Team saved successfully.")

    def delete_team(self):
        if not self.current_team: return
        tid = self.current_team.get("team_id")
        if messagebox.askyesno("Confirm", f"Delete team {tid}?"):
            self.data["teams"] = [t for t in self.data.get("teams", []) if t.get("team_id") != tid]
            self.current_team = None
            self.refresh_team_list()
            self.refresh_team_map_callback()
            self.save_callback()
            for w in self.widgets.values(): 
                if isinstance(w, tk.Text): w.delete(1.0, tk.END)
                else: w.delete(0, tk.END)
            self.players_editor.set_value({})

# ---------------------- Main Application ----------------------

class MainApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Football Data Manager")
        self.root.geometry("1300x800")
        
        # Enable Copy/Paste shortcuts globally for standard text widgets
        # This helps with tk.Text and ttk.Entry (mostly Entry needs less help, but Text does)
        self.root.bind_class("Entry", "<Control-c>", lambda e: e.widget.event_generate("<<Copy>>"))
        self.root.bind_class("Entry", "<Control-v>", lambda e: e.widget.event_generate("<<Paste>>"))
        self.root.bind_class("Entry", "<Control-x>", lambda e: e.widget.event_generate("<<Cut>>"))
        self.root.bind_class("Entry", "<Control-a>", lambda e: e.widget.select_range(0, "end"))
        
        self.root.bind_class("Text", "<Control-c>", lambda e: e.widget.event_generate("<<Copy>>"))
        self.root.bind_class("Text", "<Control-v>", lambda e: e.widget.event_generate("<<Paste>>"))
        self.root.bind_class("Text", "<Control-x>", lambda e: e.widget.event_generate("<<Cut>>"))
        self.root.bind_class("Text", "<Control-a>", lambda e: e.widget.tag_add("sel", "1.0", "end"))
        
        self.data = None
        self.file_path = None
        
        self.load_json_file()
        if not self.data: return

        self.notebook = ttk.Notebook(root)
        self.notebook.pack(fill=tk.BOTH, expand=True)
        
        self.team_map = {}
        self.venue_list = []
        self.update_data_helpers()
        
        self.match_tab = MatchEditorTab(self.notebook, self.data, self.team_map, self.venue_list, self.save_to_file)
        self.team_tab = TeamEditorTab(self.notebook, self.data, self.save_to_file, self.refresh_helpers_and_ui)
        
        self.notebook.add(self.match_tab, text="Matches")
        self.notebook.add(self.team_tab, text="Teams")

    def load_json_file(self):
        file_path = filedialog.askopenfilename(
            title="Select JSON file",
            filetypes=[("JSON files", "*.json"), ("All files", "*.*")]
        )
        if not file_path:
            messagebox.showerror("Error", "No file selected. Exiting.")
            self.root.destroy()
            return

        try:
            with open(file_path, 'r', encoding='utf-8') as self.f:
                self.data = json.load(self.f)
            self.file_path = file_path
        except Exception as e:
            messagebox.showerror("Error", f"Failed to load file:\n{e}")
            self.root.destroy()
            return

        if "matches" not in self.data: self.data["matches"] = []
        if "teams" not in self.data: self.data["teams"] = []
        if "venues" not in self.data: self.data["venues"] = []

    def update_data_helpers(self):
        self.team_map = {}
        for team in self.data.get("teams", []):
            name = team.get("name", "Unknown")
            self.team_map[name] = team.get("team_id")

        venues = set(self.data.get("venues", []))
        for match in self.data.get("matches", []):
            if match.get("venue"): venues.add(match["venue"])
        self.venue_list = sorted(venues)
        self.data["venues"] = list(self.venue_list)

    def refresh_helpers_and_ui(self):
        self.update_data_helpers()
        if hasattr(self, 'match_tab'):
            for field in ["home_team_id", "away_team_id"]:
                if field in self.match_tab.widgets:
                    # Use update_values for searchable comboboxes
                    widget = self.match_tab.widgets[field]
                    if isinstance(widget, ComboboxSearchable):
                        widget.update_values(list(self.team_map.keys()))
                    else:
                        widget['values'] = list(self.team_map.keys())
            self.match_tab.refresh_match_tree()

    def save_to_file(self):
        try:
            with open(self.file_path, 'w', encoding='utf-8') as f:
                json.dump(self.data, f, indent=2, ensure_ascii=False)
        except Exception as e:
            messagebox.showerror("Error", f"Failed to save file:\n{e}")

if __name__ == "__main__":
    root = tk.Tk()
    app = MainApp(root)
    root.mainloop()