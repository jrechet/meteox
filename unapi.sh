#!/usr/bin/env bash
set -euo pipefail

echo "Undoing MyAPI configurations (Claude Code will remain installed)..."

# 1. Revert .claude/settings.json
SETTINGS_PATH="$HOME/.claude/settings.json"
if [ -f "$SETTINGS_PATH" ]; then
  # Look for backups created during the original setup
  BACKUPS=($(ls -t "$HOME"/.claude/settings.json.backup.* 2>/dev/null || true))
  if [ ${#BACKUPS[@]} -gt 0 ]; then
    LATEST_BACKUP="${BACKUPS[0]}"
    echo "Restoring settings.json from backup: $LATEST_BACKUP"
    mv "$LATEST_BACKUP" "$SETTINGS_PATH"
    # Remove any remaining settings backups
    rm -f "$HOME"/.claude/settings.json.backup.* || true
  else
    echo "No backup found. Cleaning MyAPI configuration from settings.json..."
    python3 - "$SETTINGS_PATH" <<'PY'
import json, sys
from pathlib import Path

path = Path(sys.argv[1]).expanduser()
if path.exists():
    try:
        data = json.loads(path.read_text())
    except Exception:
        data = {}
    
    # Define rules added by the original script
    added_perms = {
        "Agent", "AskUserQuestion", "Bash(*)", "Read(*)", "Edit(*)", 
        "Write(*)", "Glob(*)", "Grep(*)", "EnterWorktree", "ExitWorktree", 
        "WebFetch(*)", "WebSearch(*)", "TodoWrite(*)", "Task(*)"
    }
    
    if "permissions" in data:
        perms = data["permissions"]
        if "allow" in perms:
            perms["allow"] = [rule for rule in perms["allow"] if rule not in added_perms]
            if not perms["allow"]:
                perms.pop("allow", None)
        if perms.get("defaultMode") == "acceptEdits":
            perms.pop("defaultMode", None)
        if not perms:
            data.pop("permissions", None)
            
    if "env" in data:
        env = data["env"]
        if env.get("ANTHROPIC_MODEL") == "claude-opus-4-8":
            env.pop("ANTHROPIC_MODEL", None)
        if not env:
            data.pop("env", None)
            
    if not data:
        # If the file is now empty, delete it
        path.unlink()
        print(f"Removed empty settings file: {path}")
    else:
        path.write_text(json.dumps(data, indent=2) + "\n")
        print(f"Reverted MyAPI settings in: {path}")
PY
  fi
fi

# Attempt to remove the .claude directory if it is now empty
if [ -d "$HOME/.claude" ]; then
  rmdir "$HOME/.claude" 2>/dev/null || true
fi

# 2. Clean up .zshrc
ZSHRC_PATH="$HOME/.zshrc"
if [ -f "$ZSHRC_PATH" ]; then
  echo "Removing MyAPI configuration block from $ZSHRC_PATH..."
  python3 - "$ZSHRC_PATH" <<'PY'
import re, sys
from pathlib import Path

path = Path(sys.argv[1]).expanduser()
if path.exists():
    text = path.read_text()
    # Strip the block added by the setup
    cleaned_text = re.sub(r"\n?# BEGIN MYAPI_WORLD_CLAUDE\n.*?\n# END MYAPI_WORLD_CLAUDE\n?", "\n", text, flags=re.S)
    
    # Remove excessive blank lines if any
    cleaned_text = re.sub(r"\n{3,}", "\n\n", cleaned_text)
    
    path.write_text(cleaned_text.rstrip() + "\n")
    print("Cleaned up .zshrc successfully.")
PY
fi

# 3. Clean up environment variables in the current session
echo ""
echo "To clean up the environment variables in your current terminal session, run:"
echo "----------------------------------------"
echo "unset ANTHROPIC_BASE_URL ANTHROPIC_AUTH_TOKEN ANTHROPIC_MODEL CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC"
echo "source ~/.zshrc"
echo "----------------------------------------"
echo ""
echo "MyAPI configuration has been successfully reverted. Claude Code is still installed."
