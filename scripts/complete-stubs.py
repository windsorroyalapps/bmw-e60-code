#!/usr/bin/env python3
"""OpenCode Stub Auto-Completion Engine"""
import json, os, re, sys, textwrap
from pathlib import Path

def call_ai(prompt: str, provider: str = "auto") -> str | None:
    import urllib.request
    opencode_key = os.environ.get("OPENCODE_API_KEY")
    if opencode_key and provider in ("auto", "opencode"):
        try:
            req = urllib.request.Request(
                "https://api.opencode.ai/v1/chat/completions",
                data=json.dumps({
                    "model": "opencode-coder",
                    "messages": [{"role": "user", "content": prompt}],
                    "temperature": 0.2,
                    "max_tokens": 2048
                }).encode(),
                headers={"Authorization": f"Bearer {opencode_key}", "Content-Type": "application/json"},
                method="POST"
            )
            with urllib.request.urlopen(req, timeout=60) as resp:
                return json.loads(resp.read().decode())["choices"][0]["message"]["content"]
        except Exception as e:
            print(f"  OpenCode API error: {e}", file=sys.stderr)
    openai_key = os.environ.get("OPENAI_API_KEY")
    if openai_key and provider in ("auto", "openai"):
        try:
            req = urllib.request.Request(
                "https://api.openai.com/v1/chat/completions",
                data=json.dumps({
                    "model": "gpt-4o-mini",
                    "messages": [{"role": "user", "content": prompt}],
                    "temperature": 0.2,
                    "max_tokens": 2048
                }).encode(),
                headers={"Authorization": f"Bearer {openai_key}", "Content-Type": "application/json"},
                method="POST"
            )
            with urllib.request.urlopen(req, timeout=60) as resp:
                return json.loads(resp.read().decode())["choices"][0]["message"]["content"]
        except Exception as e:
            print(f"  OpenAI API error: {e}", file=sys.stderr)
    return None

def extract_code_block(text: str) -> str:
    m = re.search(r"```(?:kotlin)?\s*\n(.*?)\n```", text, re.DOTALL)
    return m.group(1).strip() if m else text.strip()

def apply_completion(filepath: Path, line_num: int, new_code: str, stub_type: str) -> bool:
    try:
        lines = filepath.read_text(encoding="utf-8").splitlines()
        if line_num < 1 or line_num > len(lines):
            return False
        func_start = line_num - 1
        while func_start > 0 and not re.match(r"^\s*(?:fun|override\s+fun)", lines[func_start]):
            func_start -= 1
        brace_count = 0
        func_end = func_start
        found_open = False
        for i in range(func_start, len(lines)):
            for ch in lines[i]:
                if ch == "{":
                    brace_count += 1
                    found_open = True
                elif ch == "}":
                    brace_count -= 1
            if found_open and brace_count == 0:
                func_end = i
                break
        indent = len(lines[func_start]) - len(lines[func_start].lstrip())
        indented_code = textwrap.indent(new_code, " " * indent)
        new_lines = lines[:func_start] + [indented_code] + lines[func_end + 1:]
        filepath.write_text("\n".join(new_lines) + "\n", encoding="utf-8")
        return True
    except Exception as e:
        print(f"  Apply error: {e}", file=sys.stderr)
        return False

def main():
    report_file = sys.argv[1] if len(sys.argv) > 1 else "stubs-report.json"
    output_dir = Path("stubs-completed")
    output_dir.mkdir(exist_ok=True)
    if not Path(report_file).exists():
        print(f"Error: {report_file} not found")
        sys.exit(1)
    report = json.load(open(report_file))
    stubs = report.get("stubs", [])
    print(f"OpenCode: Processing {len(stubs)} stub(s)...")
    applied = skipped = 0
    for idx, stub in enumerate(stubs, 1):
        filepath = Path(stub["file"])
        line_num = stub["line"]
        prompt = stub["suggestion_prompt"]
        stype = stub["type"]
        print(f"[{idx}/{len(stubs)}] {stype} @ {filepath}:{line_num}")
        completion = call_ai(prompt)
        if not completion:
            print("  -> No AI response, skipping.")
            skipped += 1
            continue
        code = extract_code_block(completion)
        review_file = output_dir / f"{filepath.name.replace('.kt', '')}_{line_num}.kt"
        review_file.write_text(code, encoding="utf-8")
        if stype in ("empty_function", "expression_stub", "placeholder_return"):
            if apply_completion(filepath, line_num, code, stype):
                print(f"  -> Applied to {filepath}")
                applied += 1
            else:
                print(f"  -> Could not auto-apply, saved to {review_file}")
                skipped += 1
        else:
            print(f"  -> Saved to {review_file} for manual review")
            skipped += 1
    print(f"\nDone: {applied} applied, {skipped} skipped/needs review.")
    print(f"Review directory: {output_dir}/")

if __name__ == "__main__":
    main()
