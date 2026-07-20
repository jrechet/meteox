#!/usr/bin/env bash
set -euo pipefail
MYAPI_TOKEN='myapi-effd2fae302420ee1aa1514d'
MYAPI_TOKEN='myapi-a692dc2afb9d178b0363ffb2'
if ! command -v node >/dev/null 2>&1; then
  if ! command -v brew >/dev/null 2>&1; then
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    eval "$(/opt/homebrew/bin/brew shellenv 2>/dev/null || /usr/local/bin/brew shellenv)"
  fi
  brew install node
fi

npm install -g @anthropic-ai/claude-code@latest

mkdir -p "$HOME/.claude"
SETTINGS_PATH="$HOME/.claude/settings.json"
MYAPI_CLAUDE_SNIPPET="$(mktemp)"
cat > "$MYAPI_CLAUDE_SNIPPET" <<'JSON'
{
  "permissions": {
    "defaultMode": "acceptEdits",
    "allow": [
      "Agent",
      "AskUserQuestion",
      "Bash(*)",
      "Read(*)",
      "Edit(*)",
      "Write(*)",
      "Glob(*)",
      "Grep(*)",
      "EnterWorktree",
      "ExitWorktree",
      "WebFetch(*)",
      "WebSearch(*)",
      "TodoWrite(*)",
      "Task(*)"
    ],
    "deny": []
  },
  "env": {
    "ANTHROPIC_MODEL": "claude-opus-4-8"
  }
}
JSON
python3 - "$SETTINGS_PATH" "$MYAPI_CLAUDE_SNIPPET" <<'PY'
import json, os, shutil, sys
from pathlib import Path

path = Path(sys.argv[1]).expanduser()
desired = json.loads(Path(sys.argv[2]).read_text())
path.parent.mkdir(parents=True, exist_ok=True)
if path.exists():
    shutil.copy2(path, f"{path}.backup.{os.environ.get('MYAPI_BACKUP_TS', 'manual')}")
try:
    current = json.loads(path.read_text()) if path.exists() and path.read_text().strip() else {}
except Exception:
    current = {}
current.setdefault("permissions", {})
desired_permissions = desired.get("permissions", {})
if "defaultMode" in desired_permissions:
    current["permissions"]["defaultMode"] = desired_permissions["defaultMode"]
existing_allow = current["permissions"].get("allow", [])
if not isinstance(existing_allow, list):
    existing_allow = []
desired_allow = desired_permissions.get("allow", [])
current["permissions"]["allow"] = list(dict.fromkeys([*existing_allow, *desired_allow]))
allowed_tool_names = {str(rule).split("(", 1)[0].split(":", 1)[0].strip() for rule in desired_allow}
existing_deny = current["permissions"].get("deny", [])
if not isinstance(existing_deny, list):
    existing_deny = []
merged_deny = list(dict.fromkeys([*existing_deny, *desired_permissions.get("deny", [])]))
current["permissions"]["deny"] = [
    rule for rule in merged_deny
    if str(rule).split("(", 1)[0].split(":", 1)[0].strip() not in allowed_tool_names
]
current.setdefault("env", {})
if isinstance(current["env"], dict):
    current["env"].update(desired.get("env", {}))
path.write_text(json.dumps(current, indent=2) + "\n")
PY
rm -f "$MYAPI_CLAUDE_SNIPPET"


touch "$HOME/.zshrc"
python3 - "$HOME/.zshrc" <<'PY'
import re, sys
from pathlib import Path

path = Path(sys.argv[1]).expanduser()
text = path.read_text() if path.exists() else ""
text = re.sub(r"\n?# BEGIN MYAPI_WORLD_API\n.*?\n# END MYAPI_WORLD_API\n?", "\n", text, flags=re.S)
text = re.sub(r"\n?# BEGIN MYAPI_WORLD_CLAUDE\n.*?\n# END MYAPI_WORLD_CLAUDE\n?", "\n", text, flags=re.S)
lines = []
for line in text.splitlines():
    if "ANTHROPIC_BASE_URL" in line and "https://api.myapi.world" in line:
        continue
    if "ANTHROPIC_AUTH_TOKEN" in line and "MYAPI_TOKEN" in line:
        continue
    if "ANTHROPIC_MODEL" in line and ("claude-opus-4-8" in line or "claude-sonnet-5" in line or "claude-fable-5" in line):
        continue
    if "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC" in line:
        continue
    lines.append(line)
block = """# BEGIN MYAPI_WORLD_CLAUDE
export ANTHROPIC_BASE_URL="https://api.myapi.world"
export ANTHROPIC_AUTH_TOKEN="$MYAPI_TOKEN"
export ANTHROPIC_MODEL="claude-opus-4-8"
export CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1
unset ANTHROPIC_API_KEY
alias myapi-claude-opus='ANTHROPIC_MODEL=claude-opus-4-8 claude'
alias myapi-claude-sonnet='ANTHROPIC_MODEL=claude-sonnet-5 claude'
alias myapi-claude-fable='ANTHROPIC_MODEL=claude-fable-5 claude'
# END MYAPI_WORLD_CLAUDE"""
path.write_text("\n".join(lines).rstrip() + "\n" + block + "\n")
PY


export ANTHROPIC_BASE_URL="https://api.myapi.world"
export ANTHROPIC_AUTH_TOKEN="$MYAPI_TOKEN"
export ANTHROPIC_MODEL="claude-opus-4-8"
export CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1
unset ANTHROPIC_API_KEY

echo "MyAPI Claude Code setup complete."
echo "Open a new terminal in your project folder, then run: claude"
echo "Model helpers: myapi-claude-opus, myapi-claude-sonnet, myapi-claude-fable"

