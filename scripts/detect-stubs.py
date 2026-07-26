#!/usr/bin/env python3
"""OpenCode Stub Detector for BMW E60 Coder Pro"""
import argparse, json, re, sys
from pathlib import Path
from dataclasses import dataclass, asdict
from typing import List

@dataclass
class StubEntry:
    file: str
    line: int
    column: int
    type: str
    severity: str
    context: str
    suggestion_prompt: str

STUB_PATTERNS = [
    ("empty_function", r'(?:fun|override\s+fun)\s+\w+\s*\([^)]*\)\s*(?::\s*\w+)?\s*\{\s*\}', "stub"),
    ("todo_comment", r'(?://|/\*|\*)\s*(TODO|FIXME|STUB|XXX|HACK)[\s:]*(.*)', "todo"),
    ("not_implemented", r'throw\s+NotImplementedError|TODO\s*\(\s*\)', "stub"),
    ("placeholder_return", r'return\s+(0|0x[0-9A-Fa-f]+|false|null|""|\[\]|emptyList\(\)|emptySet\(\))\s*(?:;)?\s*$', "partial"),
    ("wip_marker", r'(?://|/\*|\*)\s*(PLACEHOLDER|WIP|INCOMPLETE|DRAFT|UNFINISHED)', "stub"),
    ("expression_stub", r'(?:fun|override\s+fun)\s+\w+\s*\([^)]*\)\s*(?::\s*\w+)?\s*=\s*(0|false|null|"")\s*$', "stub"),
]

def get_context_lines(filepath: Path, target_line: int, radius: int = 6) -> str:
    try:
        lines = filepath.read_text(encoding='utf-8').splitlines()
        start = max(0, target_line - radius - 1)
        end = min(len(lines), target_line + radius)
        return "\n".join(f"{i+1:4d}: {lines[i]}" for i in range(start, end))
    except Exception:
        return ""

def scan_file(filepath: Path, project_root: Path) -> List[StubEntry]:
    stubs: List[StubEntry] = []
    try:
        text = filepath.read_text(encoding='utf-8')
    except Exception as e:
        print(f"Warning: could not read {filepath}: {e}", file=sys.stderr)
        return stubs
    rel_path = filepath.relative_to(project_root)
    for pat_name, pat_regex, severity in STUB_PATTERNS:
        for match in re.finditer(pat_regex, text, re.MULTILINE | re.IGNORECASE):
            line_num = text[:match.start()].count("\n") + 1
            col_num = match.start() - text.rfind("\n", 0, match.start())
            context = get_context_lines(filepath, line_num)
            matched_text = match.group(0).strip()
            prompt = f"""Complete the following Kotlin stub in `{rel_path}`.
Detected pattern: {pat_name}
Severity: {severity}
Matched code:
```kotlin
{matched_text}
```
Context:
```kotlin
{context}
```
Please provide a complete, production-ready implementation that:
1. Fulfills the function's declared contract and return type
2. Handles edge cases and errors appropriately
3. Follows Kotlin best practices
4. Integrates with the existing BMW E60 Coder Pro architecture (OBD2, CAN bus, Compose UI)
5. Includes brief inline comments explaining non-obvious logic
Output ONLY the replacement code block (no explanations outside the code)."""
            stubs.append(StubEntry(
                file=str(rel_path), line=line_num, column=col_num,
                type=pat_name, severity=severity, context=context,
                suggestion_prompt=prompt
            ))
    return stubs

def main():
    parser = argparse.ArgumentParser(description="OpenCode Stub Detector")
    parser.add_argument("--src", default="app/src/main/java", help="Source root to scan")
    parser.add_argument("--format", choices=["json", "markdown", "sarif"], default="json")
    parser.add_argument("--output", default="stubs-report.json", help="Output file path")
    parser.add_argument("--severity", choices=["stub", "partial", "todo", "all"], default="all")
    args = parser.parse_args()
    project_root = Path.cwd()
    src_root = project_root / args.src
    if not src_root.exists():
        print(f"Error: source root not found: {src_root}", file=sys.stderr)
        sys.exit(1)
    all_stubs = []
    kotlin_files = list(src_root.rglob("*.kt"))
    print(f"Scanning {len(kotlin_files)} Kotlin file(s) in {src_root}...")
    for kt_file in kotlin_files:
        all_stubs.extend(scan_file(kt_file, project_root))
    severity_order = {"todo": 0, "partial": 1, "stub": 2}
    min_sev = severity_order.get(args.severity, 0) if args.severity != "all" else -1
    filtered = [s for s in all_stubs if severity_order.get(s.severity, 0) >= min_sev]
    seen = set()
    unique = []
    for s in filtered:
        key = (s.file, s.line)
        if key not in seen:
            seen.add(key)
            unique.append(s)
    print(f"Found {len(unique)} unique stub(s) / placeholder(s).")
    report = {
        "meta": {
            "scanner": "opencode-stub-detector",
            "version": "1.0.0",
            "files_scanned": len(kotlin_files),
            "stubs_found": len(unique),
            "severity_filter": args.severity,
        },
        "stubs": [asdict(s) for s in unique]
    }
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)
    print(f"Report written to: {args.output}")
    sys.exit(0)

if __name__ == "__main__":
    main()
